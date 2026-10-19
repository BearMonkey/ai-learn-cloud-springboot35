package org.monkey.catalog.mq;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.monkey.common.mq.constant.TopicConstants;
import org.monkey.common.mq.dto.ProductEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 商品事件消息生产者 —— 发送 PRODUCT_EVENT 消息
 *
 * @author monkey
 */
@Component
public class ProductEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventProducer.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rocketmq.env}")
    private String env;

    public ProductEventProducer(RocketMQTemplate rocketMQTemplate, ObjectMapper objectMapper) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送商品创建事件消息
     *
     * @param event 商品事件消息
     */
    public void sendProductEvent(ProductEventMessage event) throws Exception {
        if (event.getEnv() == null) {
            event.setEnv(env);
        }
        try {
            // 构建原生 RocketMQ Message，显式设置 user property 以支持 SQL92 过滤
            Message rocketMsg = new Message();
            rocketMsg.setTopic(TopicConstants.PRODUCT_EVENT);
            rocketMsg.setBody(objectMapper.writeValueAsBytes(event));
            rocketMsg.putUserProperty("env", event.getEnv());
            rocketMsg.setTags(event.getEventType()); // EventType
            rocketMQTemplate.getProducer().send(rocketMsg);
            log.info("发送商品事件: event={}", JSONObject.toJSONString(event));
        } catch (Exception e) {
            log.error("发送商品事件失败: productId={}", event.getProductId(), e);
            throw e;
        }
    }
}
