package org.monkey.iam;

import org.monkey.iam.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 认证服务
 *
 * @author monkey
 */
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class IamApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamApplication.class, args);
    }
}
