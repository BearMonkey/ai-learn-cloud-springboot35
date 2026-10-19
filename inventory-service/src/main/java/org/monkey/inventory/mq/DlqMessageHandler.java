package org.monkey.inventory.mq;

import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 死信队列消费者 —— 消费 DLQ Topic
 *
 * <p>RocketMQ 默认重试 16 次后，消息进入 %DLQ% 前缀的 Topic。
 * 死信消费者记录日志并 ACK 掉消息，避免无限重试。
 *
 * @author monkey
 */
@Component
@RocketMQMessageListener(
        topic = "%DLQ%inventory-consumer_${rocketmq.env}",
        consumerGroup = "inventory-dlq-consumer_${rocketmq.env}"
)
public class DlqMessageHandler implements RocketMQListener<MessageExt> {

    private static final Logger log = LoggerFactory.getLogger(DlqMessageHandler.class);

    @Override
    public void onMessage(MessageExt msg) {
        String body = new String(msg.getBody(), StandardCharsets.UTF_8);
        log.error("死信消息: topic={}, msgId={}, retryTimes={}, body={}",
                msg.getTopic(), msg.getMsgId(), msg.getReconsumeTimes(), body);

        // TODO: 写入 dead_message 表（需要创建对应的实体和 Mapper）
        // TODO: 发送告警通知（钉钉/企微/邮件）

        // ACK 掉 —— 死信队列中的消息不再重试
    }
}
