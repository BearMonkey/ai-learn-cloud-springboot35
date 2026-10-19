package org.monkey.order.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单 DTO —— 对外暴露的订单数据结构
 *
 * @author monkey
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO implements Serializable {

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
    private List<OrderItemDTO> items;
    private LocalDateTime createTime;
}
