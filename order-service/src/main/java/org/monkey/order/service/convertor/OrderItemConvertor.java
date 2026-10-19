package org.monkey.order.service.convertor;

import com.google.common.collect.Lists;
import org.monkey.order.api.dto.OrderItemDTO;
import org.monkey.order.model.OrderItem;

import java.util.List;

public class OrderItemConvertor {
    public static OrderItemConvertor of() {
        return new OrderItemConvertor();
    }

    public List<OrderItemDTO> buildOrderItemsDTO(List<OrderItem> items) {
        List<OrderItemDTO> orderItemDTOS = Lists.newArrayList();
        for (OrderItem item : items) {
            orderItemDTOS.add(OrderItemDTO.builder()
                    .id(item.getId())
                    .productId(item.getProductId())
                    .skuId(item.getSkuId())
                    .specName(item.getSpecName())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .build());
        }
        return orderItemDTOS;
    }
}
