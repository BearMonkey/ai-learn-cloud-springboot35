package org.monkey.common.core.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.monkey.common.core.context.UserContextHolder;
import org.monkey.common.core.context.UserInfo;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Servlet 过滤器 —— 提取 traceId 写入 MDC，解析用户信息存入 ThreadLocal
 *
 * <p>调用链：
 * <ol>
 *   <li>从请求 Header "X-Trace-Id" 获取 traceId，为空则生成 UUID</li>
 *   <li>写入 MDC，日志 pattern 通过 %X{traceId} 输出</li>
 *   <li>从 Header "X-User-Id" / "X-User-Roles" 构建 {@link UserInfo} 存入 {@link UserContextHolder}</li>
 *   <li>请求结束后清理 MDC 和 ThreadLocal</li>
 * </ol>
 *
 * @author monkey
 */
@Slf4j
public class TraceIdFilter implements Filter {

    /** traceId 的 Header 名称 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 用户ID Header */
    private static final String USER_ID_HEADER = "X-User-Id";

    /** 用户角色 Header（逗号分隔） */
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    /** MDC 中的 key */
    public static final String MDC_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        log.info("[common-core][TraceIdFilter][doFilter] 开始处理请求");
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // 1. traceId → MDC
        String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, traceId);

        // 2. 用户信息 → ThreadLocal（仅已认证请求有 X-User-Id）
        String userIdHeader = httpRequest.getHeader(USER_ID_HEADER);
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            Long userId = Long.parseLong(userIdHeader);
            String rolesHeader = httpRequest.getHeader(USER_ROLES_HEADER);
            List<String> roles = (rolesHeader != null && !rolesHeader.isBlank())
                    ? Arrays.asList(rolesHeader.split(","))
                    : Collections.emptyList();
            UserContextHolder.set(new UserInfo(userId, roles));
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // 请求结束后清理，防止线程池复用导致串号
            MDC.remove(MDC_KEY);
            UserContextHolder.clear();
        }
    }
}
