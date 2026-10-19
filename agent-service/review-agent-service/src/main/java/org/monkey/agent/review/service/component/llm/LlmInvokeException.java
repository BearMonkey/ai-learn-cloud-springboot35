package org.monkey.agent.review.service.component.llm;

/**
 * LLM 调用失败异常：重试耗尽或永久错误时抛出——节点捕获后按 ESCALATED 降级转人工
 *
 * @author monkey
 */
public class LlmInvokeException extends RuntimeException {

    public LlmInvokeException(String message, Throwable cause) {
        super(message, cause);
    }
}