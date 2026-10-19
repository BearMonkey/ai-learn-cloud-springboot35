package org.monkey.order.service.impl;

import org.monkey.order.entity.OrderEntity;
import org.monkey.order.entity.OrderItemEntity;
import org.monkey.order.model.Order;
import org.monkey.order.model.OrderItem;
import org.monkey.order.api.enums.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Order Entity ↔ Domain Model 转换器
 *
 * @author monkey
 */
@Component
public class OrderEntityMapper {

    /** 订单领域对象 → 实体 */
    public OrderEntity toEntity(Order domain) {
        if (domain == null) return null;
        OrderEntity e = new OrderEntity();
        e.setId(domain.getId());
        e.setOrderNo(domain.getOrderNo());
        e.setBuyerId(domain.getBuyerId());
        e.setSellerId(domain.getSellerId());
        e.setTotalAmount(domain.getTotalAmount());
        e.setAddress(domain.getAddress());
        e.setStatus(domain.getStatus() != null ? domain.getStatus().getValue() : null);
        e.setCancelReason(domain.getCancelReason());
        e.setPayTime(domain.getPayTime());
        e.setDeliverTime(domain.getDeliverTime());
        e.setCompleteTime(domain.getCompleteTime());
        e.setCancelTime(domain.getCancelTime());
        e.setCreateBy(domain.getCreateBy());
        e.setUpdateBy(domain.getUpdateBy());
        e.setCreateTime(domain.getCreateTime());
        e.setUpdateTime(domain.getUpdateTime());
        return e;
    }

    /** 订单行项领域对象 → 实体 */
    public OrderItemEntity toItemEntity(OrderItem item) {
        if (item == null) return null;
        OrderItemEntity e = new OrderItemEntity();
        e.setId(item.getId());
        e.setOrderId(item.getOrderId());
        e.setBuyerId(item.getBuyerId());
        e.setSellerId(item.getSellerId());
        e.setProductId(item.getProductId());
        e.setSkuId(item.getSkuId());
        e.setSpecName(item.getSpecName());
        e.setQuantity(item.getQuantity());
        e.setPrice(item.getPrice());
        return e;
    }

    /** 订单实体 + 行项实体 → 领域对象 */
    public Order toDomain(OrderEntity entity, List<OrderItemEntity> itemEntities) {
        if (entity == null) return null;
        List<OrderItem> items = null;
        if (itemEntities != null) {
            items = itemEntities.stream().map(this::toItemDomain).collect(Collectors.toList());
        }
        return Order.reconstitute(
                entity.getId(), entity.getOrderNo(), entity.getBuyerId(), entity.getSellerId(),
                entity.getTotalAmount(), entity.getAddress(),
                entity.getStatus() != null ? OrderStatus.fromValue(entity.getStatus()) : null,
                entity.getCancelReason(),
                entity.getPayTime(), entity.getDeliverTime(), entity.getCompleteTime(), entity.getCancelTime(),
                entity.getCreateBy(), entity.getUpdateBy(),
                entity.getCreateTime(), entity.getUpdateTime(),
                items);
    }

    /** 行项实体 → 领域值对象 */
    public OrderItem toItemDomain(OrderItemEntity entity) {
        if (entity == null) return null;
        return OrderItem.reconstitute(
                entity.getId(), entity.getOrderId(), entity.getBuyerId(), entity.getSellerId(),
                entity.getProductId(), entity.getSkuId(), entity.getSpecName(),
                entity.getQuantity(), entity.getPrice());
    }
}
