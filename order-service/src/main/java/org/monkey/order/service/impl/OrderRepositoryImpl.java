package org.monkey.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.order.entity.OrderEntity;
import org.monkey.order.entity.OrderItemEntity;
import org.monkey.order.mapper.OrderItemMapper;
import org.monkey.order.mapper.OrderMapper;
import org.monkey.order.model.Order;
import org.monkey.order.service.OrderRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 订单仓储实现
 *
 * @author monkey
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderEntityMapper mapper;

    public OrderRepositoryImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper, OrderEntityMapper mapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.mapper = mapper;
    }

    @Override
    public Optional<Order> findById(Long id) {
        OrderEntity entity = orderMapper.selectById(id);
        if (entity == null) return Optional.empty();
        List<OrderItemEntity> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItemEntity>().eq(OrderItemEntity::getOrderId, id));
        return Optional.of(mapper.toDomain(entity, items));
    }

    @Override
    public Optional<Order> findByOrderNo(String orderNo) {
        OrderEntity entity = orderMapper.selectOne(
                new LambdaQueryWrapper<OrderEntity>().eq(OrderEntity::getOrderNo, orderNo));
        if (entity == null) return Optional.empty();
        List<OrderItemEntity> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItemEntity>().eq(OrderItemEntity::getOrderId, entity.getId()));
        return Optional.of(mapper.toDomain(entity, items));
    }

    @Override
    public Page<Order> findByBuyerId(Long buyerId, Page<Order> page) {
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getBuyerId, buyerId)
                .orderByDesc(OrderEntity::getCreateTime);
        return queryPage(page, wrapper);
    }

    @Override
    public Page<Order> findBySellerId(Long sellerId, Page<Order> page) {
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getSellerId, sellerId)
                .orderByDesc(OrderEntity::getCreateTime);
        return queryPage(page, wrapper);
    }

    private Page<Order> queryPage(Page<Order> page, LambdaQueryWrapper<OrderEntity> wrapper) {
        Page<OrderEntity> entityPage = new Page<>(page.getCurrent(), page.getSize());
        entityPage = orderMapper.selectPage(entityPage, wrapper);
        List<Order> records = new ArrayList<>();
        for (OrderEntity entity : entityPage.getRecords()) {
            List<OrderItemEntity> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItemEntity>().eq(OrderItemEntity::getOrderId, entity.getId()));
            records.add(mapper.toDomain(entity, items));
        }
        Page<Order> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public List<Order> findPendingPaymentBefore(LocalDateTime deadline) {
        // 查询状态为 PENDING_PAYMENT(1) 且创建时间早于 deadline 的订单
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getStatus, 1)
                .lt(OrderEntity::getCreateTime, deadline)
                .last("LIMIT 100");
        List<OrderEntity> entities = orderMapper.selectList(wrapper);
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<Order> orders = new ArrayList<>();
        for (OrderEntity entity : entities) {
            List<OrderItemEntity> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItemEntity>().eq(OrderItemEntity::getOrderId, entity.getId()));
            orders.add(mapper.toDomain(entity, items));
        }
        return orders;
    }

    @Override
    @Transactional
    public Order save(Order order) {
        OrderEntity entity = mapper.toEntity(order);
        if (entity.getId() == null) {
            orderMapper.insert(entity);
            order.setId(entity.getId());
        } else {
            orderMapper.updateById(entity);
            // 更新时删除旧行项，重新插入
            orderItemMapper.delete(
                    new LambdaQueryWrapper<OrderItemEntity>().eq(OrderItemEntity::getOrderId, entity.getId()));
        }
        if (order.getItems() != null) {
            for (var item : order.getItems()) {
                item.setOrderId(entity.getId());
                OrderItemEntity itemEntity = mapper.toItemEntity(item);
                orderItemMapper.insert(itemEntity);
                item.setId(itemEntity.getId());
            }
        }
        return findById(entity.getId()).orElseThrow();
    }
}
