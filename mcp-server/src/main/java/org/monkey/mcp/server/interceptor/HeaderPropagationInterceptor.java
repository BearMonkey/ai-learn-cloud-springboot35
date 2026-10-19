package org.monkey.mcp.server.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class HeaderPropagationInterceptor implements HandlerInterceptor {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("[mcp-server][HeaderPropagationInterceptor][preHandle] 开始处理请求");
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId != null && !userId.isEmpty()) {
            // 设置到当前请求的 header 中（Feign 会读取）
            request.setAttribute("X-User-Id", userId);
            // 或者直接设置到 RequestContextHolder（如果支持）
        }
        log.info("[mcp-server][HeaderPropagationInterceptor][preHandle] 处理请求结束");
        return true;
    }
}
