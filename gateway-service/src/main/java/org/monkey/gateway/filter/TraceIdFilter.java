package org.monkey.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 全局 TraceId 过滤器 —— 为每个请求生成唯一 traceId，注入响应与下游请求
 *
 * <p>执行顺序（order = -2），在 AuthGlobalFilter（order = -1）之前：
 * <ol>
 *   <li>生成 UUID 作为 traceId，存入 exchange.getAttributes()</li>
 *   <li>注入 X-Trace-Id Header 到下游服务请求</li>
 *   <li>响应返回时解析 JSON body，写入 Result.traceId 字段</li>
 * </ol>
 *
 * @author monkey
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    /** traceId 在 exchange attributes 中的 key */
    public static final String TRACE_ID_ATTR = "traceId";

    /** traceId 的 Header 名称，与 common-core TraceIdFilter 保持一致 */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final ObjectMapper objectMapper;

    public TraceIdFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("[gateway][TraceIdFilter][doFilter] 开始处理请求");
        // 1. 生成 UUID 作为 traceId
        String traceId = UUID.randomUUID().toString();
        exchange.getAttributes().put(TRACE_ID_ATTR, traceId);

        // 2. 注入 X-Trace-Id Header 传递给下游服务
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, traceId)
                .build();

        // 3. 装饰响应，在 JSON body 中注入 traceId
        ServerHttpResponseDecorator decoratedResponse = decorateResponse(exchange, traceId);

        return chain.filter(exchange.mutate()
                .request(mutatedRequest)
                .response(decoratedResponse)
                .build());
    }

    /**
     * 装饰响应 —— 拦截 writeWith 方法，将 traceId 注入 JSON 响应体
     */
    private ServerHttpResponseDecorator decorateResponse(ServerWebExchange exchange, String traceId) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        return new ServerHttpResponseDecorator(originalResponse) {

            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                // 仅处理 JSON 响应
                MediaType contentType = originalResponse.getHeaders().getContentType();
                if (contentType == null || !contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                    return super.writeWith(body);
                }

                // 收集所有 DataBuffer → 注入 traceId → 写回
                return DataBufferUtils.join(body).flatMap(joinedBuffer -> {
                    // 先读取原始字节
                    byte[] originalBytes = new byte[joinedBuffer.readableByteCount()];
                    joinedBuffer.read(originalBytes);
                    DataBufferUtils.release(joinedBuffer);

                    try {
                        String modifiedBody = injectTraceId(originalBytes, traceId);
                        byte[] modifiedBytes = modifiedBody.getBytes(StandardCharsets.UTF_8);
                        DataBuffer newBuffer = exchange.getResponse().bufferFactory()
                                .wrap(modifiedBytes);
                        // 更新 Content-Length
                        originalResponse.getHeaders().setContentLength(modifiedBytes.length);
                        return super.writeWith(Mono.just(newBuffer));
                    } catch (Exception e) {
                        log.debug("注入 traceId 失败，原样返回: {}", e.getMessage());
                        // 解析失败则原样返回原始响应体
                        DataBuffer recoveryBuffer = exchange.getResponse().bufferFactory()
                                .wrap(originalBytes);
                        originalResponse.getHeaders().setContentLength(originalBytes.length);
                        return super.writeWith(Mono.just(recoveryBuffer));
                    }
                }).switchIfEmpty(
                        // body 为空时直接放行
                        super.writeWith(body)
                );
            }

            @Override
            public Mono<Void> writeAndFlushWith(
                    org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
                // 流式响应直接放行，不注入 traceId
                return super.writeAndFlushWith(body);
            }
        };
    }

    /**
     * 将 traceId 注入 JSON 字符串的 "traceId" 字段
     *
     * @param bytes   原始响应字节
     * @param traceId 当前请求的 traceId
     * @return 注入后的 JSON 字符串
     */
    private String injectTraceId(byte[] bytes, String traceId) throws Exception {
        JsonNode node = objectMapper.readTree(bytes);
        if (node.isObject()) {
            ((ObjectNode) node).put("traceId", traceId);
            return objectMapper.writeValueAsString(node);
        }
        // 非 JSON 对象（如数组、字符串），原样返回
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        // 在 AuthGlobalFilter（-1）之前执行，确保鉴权失败时也能拿到 traceId
        return -2;
    }
}
