package org.monkey.order.scheduler;

import org.monkey.common.mq.dto.OrderEventMessage;
import org.monkey.order.cache.StockRedisCache;
import org.monkey.order.model.Order;
import org.monkey.order.model.OrderItem;
import org.monkey.order.mq.OrderEventProducer;
import org.monkey.order.service.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时自动取消定时任务
 *
 * <p>每 5 秒扫描 PENDING_PAYMENT 状态且超过 15 分钟的订单，自动取消并释放库存
 *
 * @author monkey
 */
@Component
public class OrderTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutTask.class);

    // 超时阈值：15 分钟
    private static final int TIMEOUT_MINUTES = 15;

    private final OrderRepository orderRepository;
    private final StockRedisCache stockRedisCache;
    private final OrderEventProducer orderEventProducer;

    public OrderTimeoutTask(OrderRepository orderRepository,
                            StockRedisCache stockRedisCache,
                            OrderEventProducer orderEventProducer) {
        this.orderRepository = orderRepository;
        this.stockRedisCache = stockRedisCache;
        this.orderEventProducer = orderEventProducer;
    }

    /**
     * 每 5 秒扫描超时订单并取消
     */
    //@Scheduled(fixedDelay = 5_000)
    public void cancelTimeoutOrders() {
        log.info("[Scheduled][cancelTimeoutOrders] 每 5 秒扫描超时订单并取消");
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<Order> timeoutOrders = orderRepository.findPendingPaymentBefore(deadline);

        if (timeoutOrders.isEmpty()) {
            return;
        }

        log.info("发现 {} 个超时订单，开始自动取消", timeoutOrders.size());
        for (Order order : timeoutOrders) {
            try {
                // 先改状态（cancel() 内部有状态机校验，已取消的会抛异常被 catch 掉）
                order.cancel("超时未支付，自动取消");
                orderRepository.save(order);

                // Redis 回补库存
                for (OrderItem item : order.getItems()) {
                    stockRedisCache.releaseStock(item.getSkuId(), item.getQuantity());
                }

                // 发送 MQ 异步释放 DB 库存
                OrderEventMessage event = OrderEventMessage.ofCancelled(
                        order.getId(), order.getBuyerId(), null);
                orderEventProducer.sendOrderEvent(event);

                log.info("订单 {} 超时自动取消成功", order.getId());
            } catch (Exception e) {
                log.warn("订单 {} 超时取消处理异常: {}", order.getId(), e.getMessage());
            }
        }
    }
}
