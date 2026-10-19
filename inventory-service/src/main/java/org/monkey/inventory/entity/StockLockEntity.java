package org.monkey.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 库存锁定实体 —— 映射 t_stock_lock 表
 *
 * @author monkey
 */
@TableName("t_stock_lock")
public class StockLockEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("inventory_id")
    private Long inventoryId;

    @TableField("order_id")
    private Long orderId;

    @TableField("sku_id")
    private Long skuId;

    private Integer quantity;

    private Integer status;

    @TableField("lock_time")
    private LocalDateTime lockTime;

    @TableField("release_time")
    private LocalDateTime releaseTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    // ========== getter / setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInventoryId() { return inventoryId; }
    public void setInventoryId(Long inventoryId) { this.inventoryId = inventoryId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getLockTime() { return lockTime; }
    public void setLockTime(LocalDateTime lockTime) { this.lockTime = lockTime; }
    public LocalDateTime getReleaseTime() { return releaseTime; }
    public void setReleaseTime(LocalDateTime releaseTime) { this.releaseTime = releaseTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
