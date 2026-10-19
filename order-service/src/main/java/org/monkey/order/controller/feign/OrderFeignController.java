package org.monkey.order.controller.feign;

import lombok.extern.slf4j.Slf4j;
import org.monkey.common.core.context.UserContextHolder;
import org.monkey.common.core.dto.Result;
import org.monkey.order.api.dto.OrderDTO;
import org.monkey.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/feign/order")
@RestController
@Slf4j
public class OrderFeignController {

    @Autowired
    private OrderService orderService;

    /** 订单详情 */
    @GetMapping("/orders/{id}")
    public Result<OrderDTO> getOrderDetail(@PathVariable("id") Long id, @RequestHeader("X-User-Id") String userId) {
        log.info("query order by id, userId={}, orderId={}", userId, id);
        return Result.success(orderService.getOrderById(id));
    }
    /** 取消订单 */
    @PutMapping("/orders/{id}/cancel")
    public Result<Boolean> cancel(@PathVariable("id") Long id) {
        Long userId = UserContextHolder.getUserId();
        log.info("cancel order userId={}, orderId={}", userId, id);
        try {
            orderService.cancel(userId, id, "test AI cancel");
            return Result.success(Boolean.TRUE);
        } catch (Exception e) {
            return Result.success(Boolean.FALSE);
        }
    }
}
