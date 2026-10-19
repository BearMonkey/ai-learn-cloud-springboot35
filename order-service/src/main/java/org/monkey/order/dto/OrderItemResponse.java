package org.monkey.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.monkey.order.model.OrderItem;

import java.math.BigDecimal;

/**
 * 订单行项响应
 *
 * @author monkey
 */
@Data
@Builder
@AllArgsConstructor
public class OrderItemResponse {

    private Long id;
    private Long productId;
    private Long skuId;
    private String specName;
    private Integer quantity;
    private BigDecimal price;

    public static OrderItemResponse from(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .skuId(item.getSkuId())
                .specName(item.getSpecName())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .build();
    }
}
