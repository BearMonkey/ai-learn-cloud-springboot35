package org.monkey.mcp.server.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
@Slf4j
public class ToolRequestHeaderInterceptor implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("[mcp-server][ToolRequestHeaderInterceptor][addInterceptors] 开始处理请求");
        registry.addInterceptor(new HeaderPropagationInterceptor())
                .addPathPatterns("/mcp/**");
        log.info("[mcp-server][ToolRequestHeaderInterceptor][addInterceptors] 处理请求结束");
    }
}
