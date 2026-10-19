package org.monkey.common.core.config;

import org.monkey.common.core.filter.TraceIdFilter;
import org.monkey.common.core.interceptor.FeignTraceIdInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * common-core 自动配置 —— 注册 TraceIdFilter 与 FeignTraceIdInterceptor
 *
 * <p>仅在 Servlet 容器（Tomcat/Jetty/Undertow）下生效，WebFlux（Gateway）不会加载
 * <p>业务服务引入 common-core 依赖后自动生效，无需手动配置
 *
 * @author monkey
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TraceIdAutoConfiguration {

    /**
     * 注册 TraceIdFilter，最高优先级确保 traceId 最早写入 MDC
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter());
        registration.setOrder(Integer.MIN_VALUE); // 最高优先级
        // 所有请求路径都经过
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * 注册 Feign 调用时的 traceId 传递拦截器
     */
    @Bean
    @ConditionalOnClass(feign.RequestInterceptor.class)
    public FeignTraceIdInterceptor feignTraceIdInterceptor() {
        return new FeignTraceIdInterceptor();
    }
}
