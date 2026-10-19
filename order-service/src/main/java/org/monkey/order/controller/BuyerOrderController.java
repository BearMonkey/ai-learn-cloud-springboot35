package org.monkey.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.Data;
import org.monkey.common.core.context.UserContextHolder;
import org.monkey.common.core.dto.Result;
import org.monkey.order.dto.CreateOrderRequest;
import org.monkey.order.dto.OrderListResponse;
import org.monkey.order.dto.OrderResponse;
import org.monkey.order.service.OrderService;
import org.springframework.web.bind.annotation.*;

/**
 * 买家订单控制器
 *
 * @author monkey
 */
@RestController
@RequestMapping("/order")
public class BuyerOrderController {

    private final OrderService orderService;

    public BuyerOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 创建订单 */
    @PostMapping("/orders")
    public Result<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Long userId = UserContextHolder.getUserId();
        return Result.success(orderService.createOrder(userId, request));
    }

    /** 我的订单列表 */
    @GetMapping("/orders/my")
    public Result<Page<OrderListResponse>> getMyOrders(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        Long userId = UserContextHolder.getUserId();
        return Result.success(orderService.getBuyerOrders(userId, page, size));
    }

    /** 订单详情 */
    @GetMapping("/orders/{id}")
    public Result<OrderResponse> getOrderDetail(@PathVariable("id") Long id) {
        return Result.success(orderService.getOrderDetail(id));
    }

    /** 支付（模拟） */
    @PutMapping("/orders/{id}/pay")
    public Result<Void> pay(@PathVariable("id") Long id) {
        Long userId = UserContextHolder.getUserId();
        orderService.pay(userId, id);
        return Result.success();
    }

    /** 取消订单 */
    @PutMapping("/orders/{id}/cancel")
    public Result<Void> cancel(@PathVariable("id") Long id,
                                 @Valid @RequestBody CancelRequest request) {
        Long userId = UserContextHolder.getUserId();
        orderService.cancel(userId, id, request.getReason());
        return Result.success();
    }

    /** 确认收货 */
    @PutMapping("/orders/{id}/complete")
    public Result<Void> complete(@PathVariable("id") Long id) {
        Long userId = UserContextHolder.getUserId();
        orderService.complete(userId, id);
        return Result.success();
    }

    @Data
    public static class CancelRequest {
        private String reason;
    }
}
