package org.monkey.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.monkey.common.core.dto.Result;
import org.monkey.gateway.properties.GatewayAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局鉴权过滤器 —— JWT 验签 + Redis 黑名单 + Header 注入
 *
 * <p>请求流程：
 * <ol>
 *   <li>判断路径是否为公开路径 → 是则跳过</li>
 *   <li>从 Authorization Header 提取 Bearer Token</li>
 *   <li>使用 HMAC-SHA256 验证 JWT 签名和有效期</li>
 *   <li>查询 Redis 黑名单，检查 userId 是否已被吊销</li>
 *   <li>注入 X-User-Id / X-User-Roles 请求头，转发至下游服务</li>
 * </ol>
 *
 * @author monkey
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);

    /** JWT 签名密钥 */
    private final SecretKey signingKey;

    /** 公开路径列表 */
    private final List<String> publicPaths;

    /** Redis 反应式客户端 */
    private final ReactiveStringRedisTemplate redisTemplate;

    /** JSON 序列化 */
    private final ObjectMapper objectMapper;

    public AuthGlobalFilter(GatewayAuthProperties authProperties,
                             ReactiveStringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper) {
        this.signingKey = Keys.hmacShaKeyFor(authProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.publicPaths = authProperties.getPublicPaths();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("[gateway][FeignTraceIdInterceptor][apply] 开始处理请求");
        String path = exchange.getRequest().getURI().getPath();

        // 1. 公开路径跳过鉴权
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // 2. 提取 JWT
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeUnauthorized(exchange, "缺少认证信息");
        }
        String token = authHeader.substring(7);

        // 3. 验签并提取信息
        String userId;
        List<String> roles;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            userId = claims.getSubject();
            roles = claims.get("roles", List.class);
        } catch (Exception e) {
            log.debug("JWT 验签失败: {}", e.getMessage());
            return writeUnauthorized(exchange, "Token 无效或已过期");
        }

        // 4. Redis 黑名单检查（以 token 为 key，只废弃特定 token）
        String blacklistKey = "blacklist:token:" + token;
        return redisTemplate.hasKey(blacklistKey)
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        return writeUnauthorized(exchange, "Token 已失效，请重新登录");
                    }

                    // 5. 注入 Header 并转发
                    String rolesStr = roles != null ? String.join(",", roles) : "";
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header("X-User-Id", userId)
                            .header("X-User-Roles", rolesStr)
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    /**
     * 判断是否为公开路径（前缀匹配）
     * <p>请求进入 Gateway 时尚未 StripPrefix，因此需要额外去掉 /api 前缀后再匹配一次
     */
    private boolean isPublicPath(String path) {
        // 完整路径直接匹配
        if (publicPaths.stream().anyMatch(path::startsWith)) {
            return true;
        }
        // 去掉 /api 前缀后匹配（因为 StripPrefix 在 GlobalFilter 之后执行）
        if (path.startsWith("/api/")) {
            String stripped = path.substring(4);
            return publicPaths.stream().anyMatch(stripped::startsWith);
        }
        return false;
    }

    /**
     * 返回 401 JSON 响应，携带 traceId
     */
    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            // 从 Filter 链中获取 traceId
            String traceId = exchange.getAttribute("traceId");
            Result<Void> result = Result.error(401, message);
            result.setTraceId(traceId);
            byte[] bytes = objectMapper.writeValueAsBytes(result);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
