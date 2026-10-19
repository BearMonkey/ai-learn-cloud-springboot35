package org.monkey.order.model;

import java.math.BigDecimal;

/**
 * OrderItem 值对象 —— Order 聚合内部的订单行项
 *
 * @author monkey
 */
public class OrderItem {

    private Long id;
    private Long orderId;
    private Long buyerId;
    private Long sellerId;
    private Long productId;
    private Long skuId;
    private String specName;   // 下单时的规格名称快照
    private Integer quantity;
    private BigDecimal price;  // 下单时的单价快照

    OrderItem() {}

    /** 创建订单行项（快照价格和规格） */
    public static OrderItem create(Long buyerId, Long sellerId, Long productId, Long skuId,
                             String specName, Integer quantity, BigDecimal price) {
        OrderItem item = new OrderItem();
        item.buyerId = buyerId;
        item.sellerId = sellerId;
        item.productId = productId;
        item.skuId = skuId;
        item.specName = specName;
        item.quantity = quantity;
        item.price = price;
        return item;
    }

    public static OrderItem reconstitute(Long id, Long orderId, Long buyerId, Long sellerId,
                                   Long productId, Long skuId, String specName,
                                   Integer quantity, BigDecimal price) {
        OrderItem item = new OrderItem();
        item.id = id;
        item.orderId = orderId;
        item.buyerId = buyerId;
        item.sellerId = sellerId;
        item.productId = productId;
        item.skuId = skuId;
        item.specName = specName;
        item.quantity = quantity;
        item.price = price;
        return item;
    }

    void bindToOrder(Long orderId) {
        this.orderId = orderId;
    }

    BigDecimal subtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    // ========== getter / setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public String getSpecName() { return specName; }
    public void setSpecName(String specName) { this.specName = specName; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
