package org.monkey.inventory.model;

import java.time.LocalDateTime;

/**
 * StockLock 实体 —— 库存锁定记录
 *
 * @author monkey
 */
public class StockLock {

    private Long id;
    private Long inventoryId;
    private Long orderId;
    private Long skuId;
    private Integer quantity;
    private StockLockStatus status;
    private LocalDateTime lockTime;
    private LocalDateTime releaseTime;
    private LocalDateTime createTime;

    /** 创建锁定记录 */
    public static StockLock create(Long inventoryId, Long orderId, Long skuId, Integer quantity) {
        StockLock lock = new StockLock();
        lock.inventoryId = inventoryId;
        lock.orderId = orderId;
        lock.skuId = skuId;
        lock.quantity = quantity;
        lock.status = StockLockStatus.LOCKED;
        lock.lockTime = LocalDateTime.now();
        lock.createTime = LocalDateTime.now();
        return lock;
    }

    /** 从持久化数据重建 */
    public static StockLock reconstitute(Long id, Long inventoryId, Long orderId, Long skuId,
                                          Integer quantity, StockLockStatus status,
                                          LocalDateTime lockTime, LocalDateTime releaseTime,
                                          LocalDateTime createTime) {
        StockLock lock = new StockLock();
        lock.id = id;
        lock.inventoryId = inventoryId;
        lock.orderId = orderId;
        lock.skuId = skuId;
        lock.quantity = quantity;
        lock.status = status;
        lock.lockTime = lockTime;
        lock.releaseTime = releaseTime;
        lock.createTime = createTime;
        return lock;
    }

    /** 释放锁定 */
    public void release() {
        this.status = StockLockStatus.RELEASED;
        this.releaseTime = LocalDateTime.now();
    }

    /** 扣减锁定 */
    public void deduct() {
        this.status = StockLockStatus.DEDUCTED;
        this.releaseTime = LocalDateTime.now();
    }

    public boolean isLocked() {
        return this.status == StockLockStatus.LOCKED;
    }

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
    public StockLockStatus getStatus() { return status; }
    public void setStatus(StockLockStatus status) { this.status = status; }
    public LocalDateTime getLockTime() { return lockTime; }
    public void setLockTime(LocalDateTime lockTime) { this.lockTime = lockTime; }
    public LocalDateTime getReleaseTime() { return releaseTime; }
    public void setReleaseTime(LocalDateTime releaseTime) { this.releaseTime = releaseTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
