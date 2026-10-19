package org.monkey.agent.review.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "milvus.config")
@Data
public class MilvusConfig {

    /**
     * Milvus URI
     */
    private String uri;
    /**
     * Milvus user
     */
    private String user;
    /**
     * Milvus password
     */
    private String passwd;
}
