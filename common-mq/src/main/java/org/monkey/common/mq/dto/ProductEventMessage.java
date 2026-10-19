package org.monkey.common.mq.dto;

import org.monkey.common.mq.constant.EventType;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 商品事件消息体 —— PRODUCT_EVENT Topic 的消息载荷
 *
 * @author monkey
 */
public class ProductEventMessage {

    /** 跟踪ID */
    private String traceId;

    /** 消息唯一ID */
    private String eventId;

    /** 事件类型 */
    private String eventType;

    /** 商品ID */
    private Long productId;

    /** 卖家ID */
    private Long sellerId;

    /** SKU 库存配置列表 */
    private List<SkuInventorySetup> skuSetups;

    /** 事件发生时间 */
    private LocalDateTime eventTime;

    /** 环境标识 */
    @Value("${rocketmq.env}")
    private String env;

    /** 创建商品事件消息 */
    public static ProductEventMessage ofCreated(String traceId, Long productId, Long sellerId,
                                                 List<SkuInventorySetup> skuSetups, String env, String eventType) {
        ProductEventMessage msg = new ProductEventMessage();
        msg.traceId = traceId;
        msg.eventId = UUID.randomUUID().toString();
        msg.eventType = eventType;
        msg.productId = productId;
        msg.sellerId = sellerId;
        msg.skuSetups = skuSetups;
        msg.eventTime = LocalDateTime.now();
        //msg.env = env; // 直接取环境变量
        return msg;
    }

    // -------- getter / setter --------

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public List<SkuInventorySetup> getSkuSetups() { return skuSetups; }
    public void setSkuSetups(List<SkuInventorySetup> skuSetups) { this.skuSetups = skuSetups; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    public String getEnv() { return env; }
    public void setEnv(String env) { this.env = env; }

    /**
     * SKU 库存配置
     */
    public static class SkuInventorySetup {
        /** SKU ID */
        private Long skuId;
        /** 各仓库库存分配 */
        private List<WarehouseStockEntry> warehouseStocks;

        public SkuInventorySetup() {}

        public SkuInventorySetup(Long skuId, List<WarehouseStockEntry> warehouseStocks) {
            this.skuId = skuId;
            this.warehouseStocks = warehouseStocks;
        }

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public List<WarehouseStockEntry> getWarehouseStocks() { return warehouseStocks; }
        public void setWarehouseStocks(List<WarehouseStockEntry> warehouseStocks) { this.warehouseStocks = warehouseStocks; }
    }

    /**
     * 仓库库存条目
     */
    public static class WarehouseStockEntry {
        /** 仓库ID */
        private Long warehouseId;
        /** 库存数量 */
        private Integer totalStock;

        public WarehouseStockEntry() {}

        public WarehouseStockEntry(Long warehouseId, Integer totalStock) {
            this.warehouseId = warehouseId;
            this.totalStock = totalStock;
        }

        public Long getWarehouseId() { return warehouseId; }
        public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
        public Integer getTotalStock() { return totalStock; }
        public void setTotalStock(Integer totalStock) { this.totalStock = totalStock; }
    }
}
