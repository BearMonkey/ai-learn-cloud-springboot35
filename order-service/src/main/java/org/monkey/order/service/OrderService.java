package org.monkey.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.order.api.dto.OrderDTO;
import org.monkey.order.dto.CreateOrderRequest;
import org.monkey.order.dto.OrderListResponse;
import org.monkey.order.dto.OrderResponse;

/**
 * 订单服务接口
 *
 * @author monkey
 */
public interface OrderService {

    /** 创建订单 */
    OrderResponse createOrder(Long buyerId, CreateOrderRequest request);

    /** 支付（模拟） */
    void pay(Long buyerId, Long orderId);

    /** 取消订单 */
    void cancel(Long buyerId, Long orderId, String reason);

    /** 确认收货 */
    void complete(Long buyerId, Long orderId);

    /** 发货 */
    void deliver(Long sellerId, Long orderId);

    /** 买家订单列表 */
    Page<OrderListResponse> getBuyerOrders(Long buyerId, int page, int size);

    /** 卖家订单列表 */
    Page<OrderListResponse> getSellerOrders(Long sellerId, int page, int size);

    /** 订单详情 */
    OrderResponse getOrderDetail(Long orderId);

    /** 订单详情 */
    OrderDTO getOrderById(Long orderId);
}
