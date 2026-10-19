package org.monkey.gateway.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 网关鉴权配置属性
 *
 * @author monkey
 */
@ConfigurationProperties("gateway.auth")
public class GatewayAuthProperties {

    /** JWT 签名密钥（HMAC-SHA256，与 iam-service 共用） */
    private String secret;

    /** 公开路径（不需要鉴权），支持前缀匹配 */
    private List<String> publicPaths = List.of(
            "/iam/auth/register",
            "/iam/auth/login",
            "/swagger-ui",
            "/iam/v3/api-docs",
            "/webjars"
    );

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public List<String> getPublicPaths() { return publicPaths; }
    public void setPublicPaths(List<String> publicPaths) { this.publicPaths = publicPaths; }
}
