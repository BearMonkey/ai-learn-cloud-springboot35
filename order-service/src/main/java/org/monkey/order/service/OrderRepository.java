package org.monkey.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.order.model.Order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 订单仓储接口
 *
 * @author monkey
 */
public interface OrderRepository {

    /** 根据ID查询（含订单行项） */
    Optional<Order> findById(Long id);

    /** 根据订单号查询 */
    Optional<Order> findByOrderNo(String orderNo);

    /** 买家分页查询订单 */
    Page<Order> findByBuyerId(Long buyerId, Page<Order> page);

    /** 卖家分页查询订单 */
    Page<Order> findBySellerId(Long sellerId, Page<Order> page);

    /** 保存订单（含行项） */
    Order save(Order order);

    /** 查询超过指定时间仍未支付（PENDING_PAYMENT）的订单，最多返回100条 */
    List<Order> findPendingPaymentBefore(LocalDateTime deadline);
}
