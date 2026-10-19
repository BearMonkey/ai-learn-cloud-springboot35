package org.monkey.inventory.model;

import java.time.LocalDateTime;

/**
 * Inventory 聚合根 —— 仓库中某个 SKU 的库存记录
 *
 * <p>业务不变性：availableStock = totalStock - lockedStock >= 0
 *
 * @author monkey
 */
public class Inventory {

    private Long id;
    private Long warehouseId;
    private Long skuId;
    private Long sellerId;
    private Integer totalStock;
    private Integer lockedStock;
    private Integer availableStock;
    private Integer safetyStock;
    private Long createBy;
    private Long updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 创建新库存记录 */
    public static Inventory create(Long warehouseId, Long skuId, Long sellerId, Integer totalStock, Integer safetyStock) {
        Inventory inv = new Inventory();
        inv.warehouseId = warehouseId;
        inv.skuId = skuId;
        inv.sellerId = sellerId;
        inv.totalStock = totalStock;
        inv.lockedStock = 0;
        inv.availableStock = totalStock;
        inv.safetyStock = safetyStock != null ? safetyStock : 0;
        inv.createBy = sellerId;
        inv.updateBy = sellerId;
        inv.createTime = LocalDateTime.now();
        inv.updateTime = LocalDateTime.now();
        return inv;
    }

    /** 从持久化数据重建 */
    public static Inventory reconstitute(Long id, Long warehouseId, Long skuId, Long sellerId,
                                          Integer totalStock, Integer lockedStock, Integer availableStock,
                                          Integer safetyStock, Long createBy, Long updateBy,
                                          LocalDateTime createTime, LocalDateTime updateTime) {
        Inventory inv = new Inventory();
        inv.id = id;
        inv.warehouseId = warehouseId;
        inv.skuId = skuId;
        inv.sellerId = sellerId;
        inv.totalStock = totalStock;
        inv.lockedStock = lockedStock;
        inv.availableStock = availableStock;
        inv.safetyStock = safetyStock;
        inv.createBy = createBy;
        inv.updateBy = updateBy;
        inv.createTime = createTime;
        inv.updateTime = updateTime;
        return inv;
    }

    /** 设置库存总量（同时更新可用库存） */
    public void setTotalStock(Integer totalStock) {
        if (totalStock < this.lockedStock) {
            throw new BusinessException("总库存不能小于已锁定库存 " + this.lockedStock);
        }
        this.totalStock = totalStock;
        this.availableStock = totalStock - this.lockedStock;
        this.updateTime = LocalDateTime.now();
    }

    /** 锁定库存 */
    public void lock(Integer quantity) {
        if (quantity <= 0) {
            throw new BusinessException("锁定数量必须大于0");
        }
        if (this.availableStock < quantity) {
            throw new BusinessException("可用库存不足，SKU " + this.skuId + " 可用:" + this.availableStock + " 需要:" + quantity);
        }
        this.lockedStock += quantity;
        this.availableStock = this.totalStock - this.lockedStock;
        this.updateTime = LocalDateTime.now();
    }

    /** 扣减库存（发货后实际减少） */
    public void deduct(Integer quantity) {
        if (this.lockedStock < quantity) {
            throw new BusinessException("已锁定库存不足，无法扣减");
        }
        this.totalStock -= quantity;
        this.lockedStock -= quantity;
        // availableStock 不变（因为锁定时已扣减过）
        this.updateTime = LocalDateTime.now();
    }

    /** 释放锁定库存 */
    public void release(Integer quantity) {
        if (this.lockedStock < quantity) {
            throw new BusinessException("已锁定库存不足，无法释放");
        }
        this.lockedStock -= quantity;
        this.availableStock = this.totalStock - this.lockedStock;
        this.updateTime = LocalDateTime.now();
    }

    /** 是否属于指定卖家 */
    public boolean belongsTo(Long sellerId) {
        return this.sellerId.equals(sellerId);
    }

    // ========== getter / setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public Integer getTotalStock() { return totalStock; }
    // setTotalStock 是业务方法（含校验），见上方
    public Integer getLockedStock() { return lockedStock; }
    public void setLockedStock(Integer lockedStock) { this.lockedStock = lockedStock; }
    public Integer getAvailableStock() { return availableStock; }
    public void setAvailableStock(Integer availableStock) { this.availableStock = availableStock; }
    public Integer getSafetyStock() { return safetyStock; }
    public void setSafetyStock(Integer safetyStock) { this.safetyStock = safetyStock; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
