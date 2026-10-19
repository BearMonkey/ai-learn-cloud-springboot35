package org.monkey.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.monkey.order.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单响应（含行项）
 *
 * @author monkey
 */
@Data
@Builder
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private String orderNo;
    private Long buyerId;
    private Long sellerId;
    private BigDecimal totalAmount;
    private String address;
    private Integer status;
    private String cancelReason;
    private LocalDateTime payTime;
    private LocalDateTime deliverTime;
    private LocalDateTime completeTime;
    private LocalDateTime cancelTime;
    private List<OrderItemResponse> items;
    private LocalDateTime createTime;

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .buyerId(order.getBuyerId())
                .sellerId(order.getSellerId())
                .totalAmount(order.getTotalAmount())
                .address(order.getAddress())
                .status(order.getStatus() != null ? order.getStatus().getValue() : null)
                .cancelReason(order.getCancelReason())
                .payTime(order.getPayTime())
                .deliverTime(order.getDeliverTime())
                .completeTime(order.getCompleteTime())
                .cancelTime(order.getCancelTime())
                .items(order.getItems().stream().map(OrderItemResponse::from).collect(Collectors.toList()))
                .createTime(order.getCreateTime())
                .build();
    }
}
