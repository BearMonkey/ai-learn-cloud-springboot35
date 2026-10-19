package org.monkey.gateway;

import org.monkey.gateway.properties.GatewayAuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 网关服务启动类
 *
 * <p>职责：
 * <ul>
 *   <li>统一入口：所有客户端请求经由 Gateway 路由转发到下游微服务</li>
 *   <li>JWT 验签：使用 HMAC-SHA256 无状态校验 JWT 合法性</li>
 *   <li>黑名单校验：查询 Redis，拦截已失效的 JWT（角色变更场景）</li>
 *   <li>路由治理：限流、熔断、负载均衡</li>
 * </ul>
 *
 * @author monkey
 */
@SpringBootApplication
@EnableConfigurationProperties(GatewayAuthProperties.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
