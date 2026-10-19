package org.monkey.order.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.monkey.common.mq.constant.TopicConstants;
import org.monkey.common.mq.dto.OrderEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 订单事件消息生产者 —— 通过 RocketMQTemplate 发送 ORDER_EVENT 消息
 *
 * @author monkey
 */
@Component
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rocketmq.env}")
    private String env;

    public OrderEventProducer(RocketMQTemplate rocketMQTemplate, ObjectMapper objectMapper) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送订单事件消息（下单/取消/发货）
     *
     * @param event 订单事件消息
     */
    public void sendOrderEvent(OrderEventMessage event) {
        // 注入环境标识（如果调用方未设置）
        if (event.getEnv() == null) {
            event.setEnv(env);
        }
        try {
            // 构建原生 RocketMQ Message，显式设置 user property 以支持 SQL92 过滤
            Message rocketMsg = new Message();
            rocketMsg.setTopic(TopicConstants.ORDER_EVENT);
            rocketMsg.setBody(objectMapper.writeValueAsBytes(event));
            rocketMsg.putUserProperty("env", event.getEnv());
            rocketMQTemplate.getProducer().send(rocketMsg);
            log.info("发送订单事件: eventType={}, orderId={}, eventId={}",
                    event.getEventType(), event.getOrderId(), event.getEventId());
        } catch (Exception e) {
            log.error("发送订单事件失败: orderId={}", event.getOrderId(), e);
        }
    }
}
