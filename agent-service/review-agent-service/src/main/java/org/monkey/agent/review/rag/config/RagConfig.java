package org.monkey.agent.review.rag.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public MilvusClientV2 milvusClientV2(MilvusConfig milvusConfig) {
        ConnectConfig config = ConnectConfig.builder()
                .uri(milvusConfig.getUri())          // Milvus 服务地址（192.168.1.200:19530）
                .username(milvusConfig.getUser())    // RBAC 认证用户名（root）
                .password(milvusConfig.getPasswd())  // RBAC 认证密码（Milvus）
                .build();
        return new MilvusClientV2(config);
    }
}
