package org.monkey.inventory.model;

import java.time.LocalDateTime;

/**
 * Warehouse 聚合根
 *
 * @author monkey
 */
public class Warehouse {

    private Long id;
    private String code;
    private String name;
    private Long sellerId;
    private String address;
    private WarehouseStatus status;
    private Long createBy;
    private Long updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 创建新仓库 */
    public static Warehouse create(String code, String name, Long sellerId, String address) {
        Warehouse w = new Warehouse();
        w.code = code;
        w.name = name;
        w.sellerId = sellerId;
        w.address = address;
        w.status = WarehouseStatus.ENABLED;
        w.createBy = sellerId;
        w.updateBy = sellerId;
        w.createTime = LocalDateTime.now();
        w.updateTime = LocalDateTime.now();
        return w;
    }

    /** 从持久化数据重建 */
    public static Warehouse reconstitute(Long id, String code, String name, Long sellerId,
                                          String address, WarehouseStatus status,
                                          Long createBy, Long updateBy,
                                          LocalDateTime createTime, LocalDateTime updateTime) {
        Warehouse w = new Warehouse();
        w.id = id;
        w.code = code;
        w.name = name;
        w.sellerId = sellerId;
        w.address = address;
        w.status = status;
        w.createBy = createBy;
        w.updateBy = updateBy;
        w.createTime = createTime;
        w.updateTime = updateTime;
        return w;
    }

    /** 更新仓库基本信息 */
    public void updateInfo(String name, String address) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        this.address = address;
        this.updateTime = LocalDateTime.now();
    }

    /** 启用 */
    public void enable() {
        if (this.status == WarehouseStatus.ENABLED) {
            throw new BusinessException("仓库已启用");
        }
        this.status = WarehouseStatus.ENABLED;
        this.updateTime = LocalDateTime.now();
    }

    /** 停用 */
    public void disable() {
        if (this.status == WarehouseStatus.DISABLED) {
            throw new BusinessException("仓库已停用");
        }
        this.status = WarehouseStatus.DISABLED;
        this.updateTime = LocalDateTime.now();
    }

    /** 是否属于指定卖家 */
    public boolean belongsTo(Long sellerId) {
        return this.sellerId.equals(sellerId);
    }

    // ========== getter / setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public WarehouseStatus getStatus() { return status; }
    public void setStatus(WarehouseStatus status) { this.status = status; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
