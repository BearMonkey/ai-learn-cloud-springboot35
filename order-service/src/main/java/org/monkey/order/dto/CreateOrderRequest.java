package org.monkey.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 创建订单请求
 *
 * @author monkey
 */
@Data
public class CreateOrderRequest {

    /** 订单行项 */
    @NotEmpty(message = "订单商品不能为空")
    @Valid
    private List<OrderItemRequest> items;

    /** 收货地址 */
    private String address;
}
