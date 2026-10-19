package org.monkey.agent.review.rag.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LlmConfig {

    /**
     * 精判模型 Bean：类目精判是判断型任务，用更强模型 + 低温度（输出稳定少发散）
     * @param baseUrl
     * @param modelName
     * @param temperature
     * @param timeout
     * @param apiKey
     * @return
     */
    @Bean("judgeModel")
    public OpenAiChatModel judgeModel(@Value("${langchain4j.judge.base-url}") String baseUrl,     // Ollama 服务地址
                                      @Value("${langchain4j.judge.model-name}") String modelName, // 模型名：按本地 ollama list 改
                                      @Value("${langchain4j.judge.temperature:0.1}") double temperature,  // 温度 0.1：判断任务要确定性输出
                                      @Value("${langchain4j.judge.timeout:60s}") Duration timeout,
                                      @Value("${langchain4j.judge.api-key}") String apiKey) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)                          // 云厂商服务地址
                .apiKey(apiKey)                            // 云厂商密钥：计费+鉴权
                .modelName(modelName)                      // 云端模型名
                .temperature(temperature)
                .timeout(timeout)
                .build();
    }

    /**
     * 商品关键词提取模型 Bean：用于从商品描述中提取关键词 低温度 少发散
     * @param baseUrl
     * @param modelName
     * @param temperature
     * @param timeout
     * @param apiKey
     * @return
     */
    @Bean("parseInputModel")
    public OpenAiChatModel parseInputModel(
            @Value("${langchain4j.parseInput.base-url}") String baseUrl,     // Ollama 服务地址
            @Value("${langchain4j.parseInput.model-name}") String modelName, // 模型名：按本地 ollama list 改
            @Value("${langchain4j.parseInput.temperature:0.1}") double temperature,  // 温度 0.1：判断任务要确定性输出
            @Value("${langchain4j.parseInput.timeout:60s}") Duration timeout,
            @Value("${langchain4j.parseInput.api-key}") String apiKey) {      // 超时：推理慢时留足时间
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)         // Ollama 服务地址（如 http://127.0.0.1:11434）
                .modelName(modelName)     // 模型名（如 qwen3:32b）
                .temperature(temperature) // 采样温度 0~2，越低输出越确定
                .timeout(timeout)         // 单次请求超时时间
                .apiKey(apiKey)           // 云厂商密钥：计费+鉴权
                .build();
    }
}
