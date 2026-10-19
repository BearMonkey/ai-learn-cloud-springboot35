package org.monkey.agent.review.mq;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.SelectorType;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.monkey.agent.review.service.ReviewService;
import org.monkey.common.mq.constant.EventType;
import org.monkey.common.mq.constant.TopicConstants;
import org.monkey.common.mq.dto.ProductEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 商品事件消费者 —— 消费 PRODUCT_EVENT Topic
 *
 * <p>处理创建商品时初始化库存（DB + Redis）
 *
 * @author monkey
 */
@Component
@RocketMQMessageListener(
        topic = TopicConstants.PRODUCT_EVENT,
        selectorType = SelectorType.TAG,
        consumerGroup = "product-consumer_${rocketmq.env}",
        selectorExpression = EventType.PRODUCT_AUDIT
)
@Slf4j
public class ProductEventConsumer implements RocketMQListener<ProductEventMessage> {

    @Autowired
    private ReviewService reviewService;

    @Override
    public void onMessage(ProductEventMessage msg) {
        String traceId = msg.getTraceId();
        MDC.put("traceId", traceId);
        log.info("收到消息: {}", JSON.toJSONString(msg));

        try {
            reviewService.handleProductAudit(msg);
            log.info("商品创建审核结束: eventId={}, sellerId={}, productId={}", msg.getEventId(), msg.getSellerId(), msg.getProductId());
        } catch (Exception e) {
            log.error("消费 PRODUCT_EVENT 失败: eventId={}, sellerId={}, productId={}", msg.getEventId(), msg.getSellerId(), msg.getProductId(), e);
            throw e;
        } finally {
            MDC.remove("traceId");
        }
    }
}