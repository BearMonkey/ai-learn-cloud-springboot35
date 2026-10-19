package org.monkey.mcp.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class ToolsConfig {
    /*@Bean
    public McpTransportContextExtractor transportContextExtractor() {
        return (serverRequest, builder) -> {
            String userId = serverRequest.headers().firstHeader("X-User-Id");
            builder.put("userId", userId);
            return builder.build();
        };
    }*/
}
