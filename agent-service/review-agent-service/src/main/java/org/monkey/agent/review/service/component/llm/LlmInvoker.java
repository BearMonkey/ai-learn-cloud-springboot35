package org.monkey.agent.review.service.component.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * LLM 调用统一封装：错误分类 → 瞬时错误指数退避重试 → 耗尽抛 {@link LlmInvokeException}
 *
 * <p>降级语义与审核领域绑定：耗尽后由调用方节点按 ESCALATED 转人工，而非自动通过/驳回
 *
 * @author monkey
 */
@Component
@Slf4j
public class LlmInvoker {

    // 最大尝试次数：1 次正常 + 2 次重试
    private static final int MAX_ATTEMPTS = 3;

    // 基础退避时间（毫秒）：第 n 次重试等待 基础 × 2^(n-1)
    private static final long BASE_BACKOFF_MS = 500;

    // 抖动上限（毫秒）：避免批量任务同步重试打爆供应商
    private static final long MAX_JITTER_MS = 200;

    // 错误分类器：单一职责，独立可测
    private final LlmErrorClassifier classifier = new LlmErrorClassifier();

    /**
     * 带容错执行一次 LLM 调用
     *
     * @param opName 调用名（如 text-judge），用于日志定位
     * @param call   实际调用
     * @return 调用结果
     * @throws LlmInvokeException 永久错误或重试耗尽
     */
    public <T> T invoke(String opName, Supplier<T> call) {
        int attempt = 0;
        while (true) {
            long startMs = System.currentTimeMillis();
            try {
                T result = call.get();
                log.info("[LlmInvoker] {} 调用成功 attempt={} costMs={}", opName, attempt + 1, System.currentTimeMillis() - startMs);
                return result;
            } catch (Exception e) {
                boolean retryable = classifier.isRetryable(e);
                attempt++;
                if (!retryable || attempt >= MAX_ATTEMPTS) {
                    // 永久错误或重试耗尽：抛业务异常，节点捕获后降级转人工
                    log.error("[LlmInvoker] {} 调用失败 attempt={} retryable={}", opName, attempt, retryable, e);
                    throw new LlmInvokeException(opName + " 调用失败（attempt=" + attempt + "）", e);
                }
                // 指数退避 + 抖动
                long backoffMs = BASE_BACKOFF_MS * (1L << (attempt - 1)) + ThreadLocalRandom.current().nextLong(MAX_JITTER_MS);
                log.warn("[LlmInvoker] {} 瞬时错误，{}ms 后重试（第 {} 次）", opName, backoffMs, attempt, e);
                sleep(backoffMs);
            }
        }
    }

    /**
     * 退避等待：被中断时恢复中断标记，让上层能感知取消
     */
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}