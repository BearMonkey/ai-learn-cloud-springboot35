package org.monkey.inventory.mq;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.monkey.common.mq.constant.EventType;
import org.monkey.common.mq.constant.TopicConstants;
import org.monkey.common.mq.dto.OrderEventMessage;
import org.monkey.inventory.api.dto.StockOperationRequest;
import org.monkey.inventory.api.dto.StockOperationResult;
import org.monkey.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 订单事件消费者 —— 消费 ORDER_EVENT Topic
 *
 * <p>处理下单(同步DB锁定)、取消(释放库存)、发货(扣减库存)三种事件
 *
 * @author monkey
 */
@Component
@RocketMQMessageListener(
        topic = TopicConstants.ORDER_EVENT,
        consumerGroup = "inventory-order-consumer_${rocketmq.env}",
        selectorExpression = "env = '${rocketmq.env}'"
)
public class OrderEventConsumer implements RocketMQListener<OrderEventMessage> {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    // 幂等 Key 前缀
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:";
    // 幂等 Key 过期时间（小时）
    private static final int IDEMPOTENT_TTL_HOURS = 72;

    private final InventoryService inventoryService;
    private final StringRedisTemplate redisTemplate;

    public OrderEventConsumer(InventoryService inventoryService, StringRedisTemplate redisTemplate) {
        this.inventoryService = inventoryService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(OrderEventMessage msg) {
        // 1. 幂等检查
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + msg.getEventId();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "PROCESSING", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            log.debug("重复消息，跳过: eventId={}", msg.getEventId());
            return; // ACK，不重复处理
        }

        try {
            // 2. 按事件类型路由
            switch (msg.getEventType()) {
                case EventType.ORDER_CREATED -> handleOrderCreated(msg);
                case EventType.ORDER_CANCELLED -> handleOrderCancelled(msg);
                case EventType.ORDER_DELIVERED -> handleOrderDelivered(msg);
                default -> log.warn("未知事件类型: {}", msg.getEventType());
            }
            // 3. 处理成功 → 标记幂等值
            redisTemplate.opsForValue().set(idempotentKey, "OK", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            // 4. 失败 → 删除幂等 key，让 RocketMQ 自动重试
            log.error("消费 ORDER_EVENT 失败: eventId={}, eventType={}", msg.getEventId(), msg.getEventType(), e);
            redisTemplate.delete(idempotentKey);
            throw e; // RocketMQ 自动重试
        }
    }

    // 处理下单 — 同步 DB 锁定库存
    private void handleOrderCreated(OrderEventMessage msg) {
        List<StockOperationRequest.StockItem> stockItems = msg.getItems().stream()
                .map(i -> new StockOperationRequest.StockItem(i.getSkuId(), i.getQuantity()))
                .toList();
        StockOperationRequest request = new StockOperationRequest(msg.getOrderId(), stockItems);
        StockOperationResult result = inventoryService.lockStock(request);
        if (Boolean.FALSE.equals(result.getSuccess())) {
            throw new RuntimeException("DB 锁定库存失败: orderId=" + msg.getOrderId() + ", " + result.getMessage());
        }
        log.info("DB 锁定库存成功: orderId={}", msg.getOrderId());
    }

    // 处理取消 — 释放 DB 库存
    private void handleOrderCancelled(OrderEventMessage msg) {
        StockOperationRequest request = new StockOperationRequest(msg.getOrderId(), null);
        StockOperationResult result = inventoryService.releaseStock(request);
        // 可能已经释放过（幂等），只记日志不抛异常
        if (Boolean.FALSE.equals(result.getSuccess())) {
            log.warn("释放库存: orderId={}, result={}", msg.getOrderId(), result.getMessage());
        }
        log.info("DB 库存已释放: orderId={}", msg.getOrderId());
    }

    // 处理发货 — 扣减 DB 库存
    private void handleOrderDelivered(OrderEventMessage msg) {
        StockOperationRequest request = new StockOperationRequest(msg.getOrderId(), null);
        StockOperationResult result = inventoryService.deductStock(request);
        // 可能已经扣减过（幂等），只记日志不抛异常
        if (Boolean.FALSE.equals(result.getSuccess())) {
            log.warn("扣减库存: orderId={}, result={}", msg.getOrderId(), result.getMessage());
        }
        log.info("DB 库存已扣减: orderId={}", msg.getOrderId());
    }
}
