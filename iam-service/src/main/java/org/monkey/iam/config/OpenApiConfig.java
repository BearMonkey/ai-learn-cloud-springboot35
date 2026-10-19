package org.monkey.iam.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 配置
 *
 * @author monkey
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI iamOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("认证服务")
                        .description("用户注册、登录、JWT 签发、角色管理")
                        .version("1.0"));
    }
}
