package org.monkey.common.mq.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.monkey.common.mq.constant.EventType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 订单事件消息体 —— ORDER_EVENT Topic 的消息载荷
 *
 * @author monkey
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderEventMessage {

    /** 消息唯一ID，幂等键 */
    private String eventId;

    /** 订单ID */
    private Long orderId;

    /** 事件类型：ORDER_CREATED / ORDER_CANCELLED / ORDER_DELIVERED */
    private String eventType;

    /** SKU + 数量列表（ORDER_CREATED 时有值） */
    private List<OrderSkuItem> items;

    /** 操作人ID */
    private Long operatorId;

    /** 事件发生时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime;

    /** 环境标识 */
    private String env;

    // -------- 工厂方法 --------

    /** 创建订单事件消息 */
    public static OrderEventMessage ofCreated(Long orderId, List<OrderSkuItem> items, Long operatorId, String env) {
        OrderEventMessage msg = new OrderEventMessage();
        msg.eventId = UUID.randomUUID().toString();
        msg.orderId = orderId;
        msg.eventType = EventType.ORDER_CREATED;
        msg.items = items;
        msg.operatorId = operatorId;
        msg.eventTime = LocalDateTime.now();
        msg.env = env;
        return msg;
    }

    /** 取消订单事件消息 */
    public static OrderEventMessage ofCancelled(Long orderId, Long operatorId, String env) {
        OrderEventMessage msg = new OrderEventMessage();
        msg.eventId = UUID.randomUUID().toString();
        msg.orderId = orderId;
        msg.eventType = EventType.ORDER_CANCELLED;
        msg.operatorId = operatorId;
        msg.eventTime = LocalDateTime.now();
        msg.env = env;
        return msg;
    }

    /** 发货事件消息 */
    public static OrderEventMessage ofDelivered(Long orderId, Long operatorId, String env) {
        OrderEventMessage msg = new OrderEventMessage();
        msg.eventId = UUID.randomUUID().toString();
        msg.orderId = orderId;
        msg.eventType = EventType.ORDER_DELIVERED;
        msg.operatorId = operatorId;
        msg.eventTime = LocalDateTime.now();
        msg.env = env;
        return msg;
    }

    // -------- getter / setter --------

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public List<OrderSkuItem> getItems() { return items; }
    public void setItems(List<OrderSkuItem> items) { this.items = items; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    public String getEnv() { return env; }
    public void setEnv(String env) { this.env = env; }

    /**
     * 订单事件中的 SKU 行项
     */
    public static class OrderSkuItem {
        /** SKU ID */
        private Long skuId;
        /** 数量 */
        private Integer quantity;

        public OrderSkuItem() {}

        public OrderSkuItem(Long skuId, Integer quantity) {
            this.skuId = skuId;
            this.quantity = quantity;
        }

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
