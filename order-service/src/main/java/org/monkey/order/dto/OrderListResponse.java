package org.monkey.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.monkey.order.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表响应（不含行项明细，列表页轻量显示）
 *
 * @author monkey
 */
@Data
@Builder
@AllArgsConstructor
public class OrderListResponse {

    private Long id;
    private String orderNo;
    private Long buyerId;
    private Long sellerId;
    private BigDecimal totalAmount;
    private String address;
    private Integer status;
    private LocalDateTime payTime;
    private LocalDateTime deliverTime;
    private LocalDateTime createTime;

    public static OrderListResponse from(Order order) {
        return OrderListResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .buyerId(order.getBuyerId())
                .sellerId(order.getSellerId())
                .totalAmount(order.getTotalAmount())
                .address(order.getAddress())
                .status(order.getStatus() != null ? order.getStatus().getValue() : null)
                .payTime(order.getPayTime())
                .deliverTime(order.getDeliverTime())
                .createTime(order.getCreateTime())
                .build();
    }
}
