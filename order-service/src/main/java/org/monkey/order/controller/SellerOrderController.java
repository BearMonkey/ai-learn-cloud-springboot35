package org.monkey.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.common.core.context.UserContextHolder;
import org.monkey.common.core.dto.Result;
import org.monkey.order.dto.OrderListResponse;
import org.monkey.order.service.OrderService;
import org.springframework.web.bind.annotation.*;

/**
 * 卖家订单控制器
 *
 * @author monkey
 */
@RestController
@RequestMapping("/order")
public class SellerOrderController {

    private final OrderService orderService;

    public SellerOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 卖家的订单列表 */
    @GetMapping("/orders/seller")
    public Result<Page<OrderListResponse>> getSellerOrders(@RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
        Long userId = UserContextHolder.getUserId();
        return Result.success(orderService.getSellerOrders(userId, page, size));
    }

    /** 发货 */
    @PutMapping("/orders/{id}/deliver")
    public Result<Void> deliver(@PathVariable("id") Long id) {
        Long userId = UserContextHolder.getUserId();
        orderService.deliver(userId, id);
        return Result.success();
    }
}
