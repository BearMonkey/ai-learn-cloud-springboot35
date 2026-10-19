package org.monkey.order.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Order 聚合根
 *
 * <p>状态机：
 * <pre>
 * PENDING_PAYMENT(1) ──pay──→ PENDING_DELIVERY(2) ──deliver──→ IN_DELIVERY(3) ──complete──→ COMPLETED(4)
 *        │                           │
 *        └──────cancel────→ CANCELLED(5) ←────cancel──────────┘
 * </pre>
 *
 * @author monkey
 */
public class Order {

    private Long id;
    private String orderNo;
    private Long buyerId;
    private Long sellerId;
    private BigDecimal totalAmount;
    private String address;
    private org.monkey.order.api.enums.OrderStatus status;
    private String cancelReason;
    private LocalDateTime payTime;
    private LocalDateTime deliverTime;
    private LocalDateTime completeTime;
    private LocalDateTime cancelTime;
    private Long createBy;
    private Long updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private List<OrderItem> items = new ArrayList<>();

    /** 创建订单 */
    public static Order create(Long buyerId, Long sellerId, String address, List<OrderItem> items) {
        Order order = new Order();
        order.orderNo = generateOrderNo();
        order.buyerId = buyerId;
        order.sellerId = sellerId;
        order.address = address;
        order.status = org.monkey.order.api.enums.OrderStatus.PENDING_PAYMENT;
        order.createBy = buyerId;
        order.updateBy = buyerId;
        order.createTime = LocalDateTime.now();
        order.updateTime = LocalDateTime.now();
        // 绑定行项，计算总金额
        order.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        order.totalAmount = order.calculateTotal();
        return order;
    }

    public static Order reconstitute(Long id, String orderNo, Long buyerId, Long sellerId,
                                      BigDecimal totalAmount, String address, org.monkey.order.api.enums.OrderStatus status,
                                      String cancelReason, LocalDateTime payTime, LocalDateTime deliverTime,
                                      LocalDateTime completeTime, LocalDateTime cancelTime,
                                      Long createBy, Long updateBy,
                                      LocalDateTime createTime, LocalDateTime updateTime,
                                      List<OrderItem> items) {
        Order order = new Order();
        order.id = id;
        order.orderNo = orderNo;
        order.buyerId = buyerId;
        order.sellerId = sellerId;
        order.totalAmount = totalAmount;
        order.address = address;
        order.status = status;
        order.cancelReason = cancelReason;
        order.payTime = payTime;
        order.deliverTime = deliverTime;
        order.completeTime = completeTime;
        order.cancelTime = cancelTime;
        order.createBy = createBy;
        order.updateBy = updateBy;
        order.createTime = createTime;
        order.updateTime = updateTime;
        order.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        return order;
    }

    // ========== 状态转换（聚合根内建状态机） ==========

    /** 支付 */
    public void pay() {
        if (status != org.monkey.order.api.enums.OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException("当前状态不可支付");
        }
        status = org.monkey.order.api.enums.OrderStatus.PENDING_DELIVERY;
        payTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    /** 发货 */
    public void deliver() {
        if (status != org.monkey.order.api.enums.OrderStatus.PENDING_DELIVERY) {
            throw new BusinessException("当前状态不可发货");
        }
        status = org.monkey.order.api.enums.OrderStatus.IN_DELIVERY;
        deliverTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    /** 确认收货 */
    public void complete() {
        if (status != org.monkey.order.api.enums.OrderStatus.IN_DELIVERY) {
            throw new BusinessException("当前状态不可确认收货");
        }
        status = org.monkey.order.api.enums.OrderStatus.COMPLETED;
        completeTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    /** 取消订单 */
    public void cancel(String reason) {
        if (status == org.monkey.order.api.enums.OrderStatus.COMPLETED || status == org.monkey.order.api.enums.OrderStatus.CANCELLED) {
            throw new BusinessException("当前状态不可取消");
        }
        status = org.monkey.order.api.enums.OrderStatus.CANCELLED;
        cancelReason = reason;
        cancelTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    // ========== 领域方法 ==========

    public boolean belongsToBuyer(Long buyerId) {
        return this.buyerId.equals(buyerId);
    }

    public boolean belongsToSeller(Long sellerId) {
        return this.sellerId.equals(sellerId);
    }

    private BigDecimal calculateTotal() {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String generateOrderNo() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    // ========== getter / setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public org.monkey.order.api.enums.OrderStatus getStatus() { return status; }
    public void setStatus(org.monkey.order.api.enums.OrderStatus status) { this.status = status; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public LocalDateTime getPayTime() { return payTime; }
    public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }
    public LocalDateTime getDeliverTime() { return deliverTime; }
    public void setDeliverTime(LocalDateTime deliverTime) { this.deliverTime = deliverTime; }
    public LocalDateTime getCompleteTime() { return completeTime; }
    public void setCompleteTime(LocalDateTime completeTime) { this.completeTime = completeTime; }
    public LocalDateTime getCancelTime() { return cancelTime; }
    public void setCancelTime(LocalDateTime cancelTime) { this.cancelTime = cancelTime; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public void setItems(List<OrderItem> items) { this.items = items != null ? new ArrayList<>(items) : new ArrayList<>(); }
}
