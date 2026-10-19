package org.monkey.agent.review;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients(basePackages = "org.monkey")
@MapperScan
@EnableScheduling
public class ReviewAgentApp {
    public static void main(String[] args) {
        SpringApplication.run(ReviewAgentApp.class, args);
    }
}
