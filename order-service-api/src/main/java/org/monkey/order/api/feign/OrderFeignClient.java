package org.monkey.order.api.feign;

import org.monkey.common.core.dto.Result;
import org.monkey.order.api.dto.OrderDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 订单服务 Feign 客户端接口
 *
 * @author monkey
 */
@FeignClient(name = "order-service", path = "/feign/order")
public interface OrderFeignClient {

    /**
     * 根据ID查询订单
     *
     * @param id 订单ID
     * @return 订单信息
     */
    @GetMapping("/orders/{id}")
    Result<OrderDTO> getOrderById(@PathVariable("id") Long id, @RequestHeader("X-User-Id") Long userId);


    /** 取消订单 */
    @PutMapping("/orders/{id}/cancel")
    Result<Boolean> cancelOrder(@PathVariable("id") Long id);
}
