package org.monkey.agent.review.service.component.llm;

import dev.langchain4j.exception.HttpException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LlmErrorClassifier 单测：可重试（瞬时）与不可重试（永久）两类错误的分类
 */
class LlmErrorClassifierTest {

    private final LlmErrorClassifier classifier = new LlmErrorClassifier();

    @Test
    void shouldRetryTimeout() {
        assertThat(classifier.isRetryable(new SocketTimeoutException("read timed out"))).isTrue();
    }

    @Test
    void shouldRetryConnectFailure() {
        assertThat(classifier.isRetryable(new ConnectException("connection refused"))).isTrue();
    }

    @Test
    void shouldRetryHttp429() {
        // 429 限流：退避后重试
        assertThat(classifier.isRetryable(new HttpException(429, "rate limited"))).isTrue();
    }

    @Test
    void shouldRetryHttp5xx() {
        // 服务器错误：瞬时故障
        assertThat(classifier.isRetryable(new HttpException(503, "service unavailable"))).isTrue();
    }

    @Test
    void shouldNotRetryHttp401() {
        // 鉴权错误：重试无意义
        assertThat(classifier.isRetryable(new HttpException(401, "unauthorized"))).isFalse();
    }

    @Test
    void shouldNotRetryHttp400() {
        // 参数错误：重试无意义还烧钱
        assertThat(classifier.isRetryable(new HttpException(400, "bad request"))).isFalse();
    }

    @Test
    void shouldNotRetryUnknownError() {
        // 未知异常（含 LLM 输出解析失败）：宁可转人工
        assertThat(classifier.isRetryable(new IllegalArgumentException("输出解析失败"))).isFalse();
    }

    @Test
    void shouldFindRetryableInCauseChain() {
        // cause 链深处埋超时：包装层再多也能识别
        assertThat(classifier.isRetryable(new RuntimeException(new IOException(new SocketTimeoutException())))).isTrue();
    }
}