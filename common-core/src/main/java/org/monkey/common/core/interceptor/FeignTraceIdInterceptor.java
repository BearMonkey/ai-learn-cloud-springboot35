package org.monkey.common.core.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.monkey.common.core.filter.TraceIdFilter;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Feign 请求拦截器 —— 将 traceId 传递到下游服务
 *
 * <p>优先级：
 * <ol>
 *   <li>从当前请求 Header "X-Trace-Id" 获取（经 Gateway 传入）</li>
 *   <li>从 MDC 获取（本服务自行生成的情况）</li>
 *   <li>仍无则生成新的 UUID</li>
 * </ol>
 *
 * @author monkey
 */
@Slf4j
public class FeignTraceIdInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        log.info("[common-core][FeignTraceIdInterceptor][apply] 开始处理请求");
        String traceId = null;

        // 优先从当前 HTTP 请求的 Header 获取
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            if (request != null) {
                traceId = request.getHeader(TraceIdFilter.TRACE_ID_HEADER);
            }
        }

        // 回退：从 MDC 获取
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get(TraceIdFilter.MDC_KEY);
        }

        // 兜底：生成新的
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        template.header(TraceIdFilter.TRACE_ID_HEADER, traceId);

        // 透传用户身份——下游按用户身份执行业务（如"只能取消自己的订单"）
        // 只有经过网关鉴权的请求才有 X-User-Id，透传后下游 TraceIdFilter 解析进 UserContextHolder
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String userId = request.getHeader("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                template.header("X-User-Id", userId);
            }
            // 角色也一起带上，下游可能做角色校验
            String roles = request.getHeader("X-User-Roles");
            if (roles != null && !roles.isBlank()) {
                template.header("X-User-Roles", roles);
            }
        }
    }
}
