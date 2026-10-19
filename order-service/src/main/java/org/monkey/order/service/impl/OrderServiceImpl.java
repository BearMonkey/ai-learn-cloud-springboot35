package org.monkey.order.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.catalog.api.dto.SkuDTO;
import org.monkey.catalog.api.feign.CatalogFeignClient;
import org.monkey.common.core.dto.Result;
import org.monkey.common.mq.dto.OrderEventMessage;
import org.monkey.order.api.dto.OrderDTO;
import org.monkey.order.cache.StockRedisCache;
import org.monkey.order.mq.OrderEventProducer;
import org.monkey.order.dto.CreateOrderRequest;
import org.monkey.order.dto.OrderItemRequest;
import org.monkey.order.dto.OrderListResponse;
import org.monkey.order.dto.OrderResponse;
import org.monkey.order.model.BusinessException;
import org.monkey.order.model.Order;
import org.monkey.order.model.OrderItem;
import org.monkey.order.api.enums.OrderStatus;
import org.monkey.order.service.OrderRepository;
import org.monkey.order.service.OrderService;
import org.monkey.order.service.convertor.OrderConvertor;
import org.monkey.order.service.convertor.OrderItemConvertor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单服务实现
 *
 * @author monkey
 */
@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final OrderConvertor convertor = OrderConvertor.of();

    private static final OrderItemConvertor orderItemConvertor = OrderItemConvertor.of();

    private final OrderRepository orderRepository;
    private final CatalogFeignClient catalogFeignClient;
    private final OrderEventProducer orderEventProducer;
    private final StockRedisCache stockRedisCache;

    public OrderServiceImpl(OrderRepository orderRepository,
                             CatalogFeignClient catalogFeignClient,
                             OrderEventProducer orderEventProducer,
                             StockRedisCache stockRedisCache) {
        this.orderRepository = orderRepository;
        this.catalogFeignClient = catalogFeignClient;
        this.orderEventProducer = orderEventProducer;
        this.stockRedisCache = stockRedisCache;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(Long buyerId, CreateOrderRequest request) {
        // 1. 通过 Feign 查询每个 SKU 的信息（价格、规格、所属商品、所属卖家），做快照
        List<OrderItemSnapshot> snapshots = new ArrayList<>();
        Long sellerId = null;

        for (OrderItemRequest item : request.getItems()) {
            // 调用 catalog 查 SKU —— 读操作，保持同步
            log.info("查询sku： skuId={}", item.getSkuId());
            Result<SkuDTO> skuRet = catalogFeignClient.getSkuById(item.getSkuId());
            log.info("查询sku： skuId={}, resp={}", item.getSkuId(), JSONObject.toJSONString(skuRet));
            SkuDTO sku = skuRet.getData();
            if (sku == null) {
                throw new BusinessException(404, "SKU " + item.getSkuId() + " 不存在");
            }
            // 所有行项必须是同一个卖家
            if (sellerId == null) {
                sellerId = sku.getSellerId();
            } else if (!sellerId.equals(sku.getSellerId())) {
                throw new BusinessException("不允许跨卖家下单");
            }
            snapshots.add(new OrderItemSnapshot(sku, item.getQuantity()));
        }

        // 2. Redis Lua 原子预扣库存（防超卖核心）
        for (OrderItemSnapshot snap : snapshots) {
            StockRedisCache.LockResult lockResult = stockRedisCache.lockStock(
                    snap.sku.getId(), snap.quantity);
            if (!lockResult.isSuccess()) {
                // 预扣失败 → 回滚已预扣的 Redis 库存
                rollbackRedisLocks(snapshots, snap);
                long available = lockResult.getAvailable() == -1
                        ? 0 : lockResult.getAvailable();
                throw new BusinessException(
                        "SKU " + snap.sku.getId() + " 库存不足，可用:" + available + " 需要:" + snap.quantity);
            }
        }

        // 3. 创建订单聚合根（PENDING_PAYMENT）
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemSnapshot snap : snapshots) {
            orderItems.add(OrderItem.create(buyerId, sellerId,
                    snap.sku.getProductId(), snap.sku.getId(),
                    snap.sku.getSpecName(), snap.quantity, snap.sku.getPrice()));
        }
        Order order = Order.create(buyerId, sellerId, request.getAddress(), orderItems);
        order = orderRepository.save(order);

        // 4. 发送 MQ 消息 —— inventory-service 异步同步 DB 库存锁定
        List<OrderEventMessage.OrderSkuItem> skuItems = snapshots.stream()
                .map(s -> new OrderEventMessage.OrderSkuItem(s.sku.getId(), s.quantity))
                .collect(Collectors.toList());
        OrderEventMessage event = OrderEventMessage.ofCreated(order.getId(), skuItems, buyerId, null);
        try {
            orderEventProducer.sendOrderEvent(event);
        } catch (Exception e) {
            // MQ 发送失败不阻塞下单，对账任务兜底
            log.error("订单 {} 发送 ORDER_CREATED 消息失败", order.getId(), e);
        }

        return OrderResponse.from(order);
    }

    /** Redis 预扣失败时回滚已锁定的库存 */
    private void rollbackRedisLocks(List<OrderItemSnapshot> snapshots, OrderItemSnapshot failedAt) {
        for (OrderItemSnapshot snap : snapshots) {
            if (snap == failedAt) {
                break; // 只回滚失败项之前的
            }
            stockRedisCache.releaseStock(snap.sku.getId(), snap.quantity);
        }
    }

    @Override
    @Transactional
    public void pay(Long buyerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!order.belongsToBuyer(buyerId)) {
            throw new BusinessException(403, "无权操作此订单");
        }
        order.pay();
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void cancel(Long buyerId, Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!order.belongsToBuyer(buyerId)) {
            throw new BusinessException(403, "无权操作此订单");
        }
        OrderStatus oldStatus = order.getStatus();
        order.cancel(reason);
        orderRepository.save(order);

        // 如果取消前是待支付或待发货，需要释放库存
        if (oldStatus == OrderStatus.PENDING_PAYMENT || oldStatus == OrderStatus.PENDING_DELIVERY) {
            // Redis 回补库存
            for (OrderItem item : order.getItems()) {
                stockRedisCache.releaseStock(item.getSkuId(), item.getQuantity());
            }

            // 发送 MQ 异步释放 DB 库存
            OrderEventMessage event = OrderEventMessage.ofCancelled(orderId, buyerId, null);
            try {
                orderEventProducer.sendOrderEvent(event);
            } catch (Exception e) {
                log.error("订单 {} 发送 ORDER_CANCELLED 消息失败", orderId, e);
            }
        }
    }

    @Override
    @Transactional
    public void complete(Long buyerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!order.belongsToBuyer(buyerId)) {
            throw new BusinessException(403, "无权操作此订单");
        }
        order.complete();
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void deliver(Long sellerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!order.belongsToSeller(sellerId)) {
            throw new BusinessException(403, "无权操作此订单");
        }
        order.deliver();
        orderRepository.save(order);

        // 发送 MQ 异步扣减 DB 库存
        OrderEventMessage event = OrderEventMessage.ofDelivered(orderId, sellerId, null);
        try {
            orderEventProducer.sendOrderEvent(event);
        } catch (Exception e) {
            log.error("订单 {} 发送 ORDER_DELIVERED 消息失败", orderId, e);
        }
    }

    @Override
    public Page<OrderListResponse> getBuyerOrders(Long buyerId, int page, int size) {
        Page<Order> domainPage = new Page<>(page, size);
        domainPage = orderRepository.findByBuyerId(buyerId, domainPage);
        List<OrderListResponse> responses = domainPage.getRecords().stream()
                .map(OrderListResponse::from)
                .collect(Collectors.toList());
        Page<OrderListResponse> result = new Page<>(page, size, domainPage.getTotal());
        result.setRecords(responses);
        return result;
    }

    @Override
    public Page<OrderListResponse> getSellerOrders(Long sellerId, int page, int size) {
        Page<Order> domainPage = new Page<>(page, size);
        domainPage = orderRepository.findBySellerId(sellerId, domainPage);
        List<OrderListResponse> responses = domainPage.getRecords().stream()
                .map(OrderListResponse::from)
                .collect(Collectors.toList());
        Page<OrderListResponse> result = new Page<>(page, size, domainPage.getTotal());
        result.setRecords(responses);
        return result;
    }

    @Override
    public OrderResponse getOrderDetail(Long orderId) {
        log.info("[order-service][getOrderDetail] 查询订单明细");
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        return OrderResponse.from(order);
    }

    @Override
    public OrderDTO getOrderById(Long orderId) {
        log.info("[order-service][getOrderDetail] 查询订单明细");
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        return OrderDTO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .buyerId(order.getBuyerId())
                .sellerId(order.getSellerId())
                .totalAmount(order.getTotalAmount())
                .address(order.getAddress())
                .status(order.getStatus().getValue())
                .cancelReason(order.getCancelReason())
                .payTime(order.getPayTime())
                .deliverTime(order.getDeliverTime())
                .completeTime(order.getCompleteTime())
                .cancelTime(order.getCancelTime())
                .items(orderItemConvertor.buildOrderItemsDTO(order.getItems()))
                .createTime(order.getCreateTime())
                .build();
    }

    /** SKU 信息快照（内部类） */
    private static class OrderItemSnapshot {
        final SkuDTO sku;
        final int quantity;
        OrderItemSnapshot(SkuDTO sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }
    }
}
