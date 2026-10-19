package org.monkey.inventory.mq;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.monkey.common.mq.constant.EventType;
import org.monkey.common.mq.constant.TopicConstants;
import org.monkey.common.mq.dto.ProductEventMessage;
import org.monkey.inventory.api.dto.BatchInventorySetupRequest;
import org.monkey.inventory.cache.StockRedisCache;
import org.monkey.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        consumerGroup = "inventory-product-consumer_${rocketmq.env}"
        //selectorExpression = "env = '${rocketmq.env}'"
)
public class ProductEventConsumer implements RocketMQListener<ProductEventMessage> {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);

    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:";
    private static final int IDEMPOTENT_TTL_HOURS = 72;

    private final InventoryService inventoryService;
    private final StockRedisCache stockRedisCache;
    private final StringRedisTemplate redisTemplate;

    public ProductEventConsumer(InventoryService inventoryService,
                                StockRedisCache stockRedisCache,
                                StringRedisTemplate redisTemplate) {
        this.inventoryService = inventoryService;
        this.stockRedisCache = stockRedisCache;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(ProductEventMessage msg) {
        log.info("收到消息: {}", msg);
        // 1. 幂等检查
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + msg.getEventId();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "PROCESSING", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            log.debug("重复消息，跳过: eventId={}", msg.getEventId());
            return;
        }

        try {
            if (EventType.PRODUCT_CREATED.equals(msg.getEventType())) {
                handleProductCreated(msg);
            }
            redisTemplate.opsForValue().set(idempotentKey, "OK", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("消费 PRODUCT_EVENT 失败: eventId={}", msg.getEventId(), e);
            redisTemplate.delete(idempotentKey);
            throw e;
        }
    }

    // 处理商品创建 — 初始化 DB 库存 + Redis 缓存
    private void handleProductCreated(ProductEventMessage msg) {
        // 转换为 BatchInventorySetupRequest
        List<BatchInventorySetupRequest> requests = msg.getSkuSetups().stream()
                .map(sku -> {
                    List<BatchInventorySetupRequest.WarehouseStock> warehouseStocks = sku.getWarehouseStocks()
                            .stream()
                            .map(ws -> new BatchInventorySetupRequest.WarehouseStock(
                                    ws.getWarehouseId(), ws.getTotalStock()))
                            .toList();
                    return new BatchInventorySetupRequest(sku.getSkuId(), msg.getSellerId(), warehouseStocks);
                })
                .toList();

        // 写入 DB
        inventoryService.batchSetupInventory(requests);

        // 写入 Redis 缓存（按 SKU 聚合总库存）
        for (ProductEventMessage.SkuInventorySetup sku : msg.getSkuSetups()) {
            int totalStock = sku.getWarehouseStocks().stream()
                    .mapToInt(ProductEventMessage.WarehouseStockEntry::getTotalStock)
                    .sum();
            stockRedisCache.initStock(sku.getSkuId(), totalStock);
        }

        log.info("商品创建库存初始化完成: productId={}, 共 {} 个SKU", msg.getProductId(), msg.getSkuSetups().size());
    }
}
