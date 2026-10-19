package org.monkey.agent.review.service.component.llm;

import dev.langchain4j.exception.HttpException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * LLM 调用错误分类器：区分瞬时错误（可重试）与永久错误（重试无意义）
 *
 * <p>遍历 cause 链识别：超时/连接失败/429/5xx 可重试；其余（4xx/参数错/非法输出）不重试
 *
 * @author monkey
 */
@Slf4j
public class LlmErrorClassifier {

    // HTTP 429：供应商限流，退避后可重试
    private static final int TOO_MANY_REQUESTS = 429;

    /**
     * 判断异常是否可重试
     *
     * @param throwable 调用抛出的异常
     * @return true=可重试（瞬时错误），false=永久错误
     */
    public boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            // 超时与连接失败：网络抖动，重试有效
            if (current instanceof SocketTimeoutException || current instanceof ConnectException) {
                return true;
            }
            // 其余 IO 异常：连接被重置等瞬时网络问题
            if (current instanceof IOException) {
                return true;
            }
            // langchain4j HttpException：按状态码分类
            if (current instanceof HttpException httpException) {
                int status = httpException.statusCode();
                if (status == TOO_MANY_REQUESTS || status >= 500) {
                    // 限流与服务器错误：退避后重试
                    return true;
                }
                // 4xx（除 429）：请求本身有问题，重试无意义还烧钱
                return false;
            }
            current = current.getCause();
        }
        // 未知异常：按永久错误处理，宁可转人工
        return false;
    }
}