package org.monkey.inventory.service.impl;

import org.monkey.inventory.entity.InventoryEntity;
import org.monkey.inventory.entity.StockLockEntity;
import org.monkey.inventory.model.Inventory;
import org.monkey.inventory.model.StockLock;
import org.monkey.inventory.model.StockLockStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Inventory / StockLock Entity ↔ Domain Model 转换器
 *
 * @author monkey
 */
@Component
public class InventoryEntityMapper {

    /** 库存领域对象 → 实体 */
    public InventoryEntity toEntity(Inventory domain) {
        if (domain == null) {
            return null;
        }
        InventoryEntity entity = new InventoryEntity();
        entity.setId(domain.getId());
        entity.setWarehouseId(domain.getWarehouseId());
        entity.setSkuId(domain.getSkuId());
        entity.setSellerId(domain.getSellerId());
        entity.setTotalStock(domain.getTotalStock());
        entity.setLockedStock(domain.getLockedStock());
        entity.setAvailableStock(domain.getAvailableStock());
        entity.setSafetyStock(domain.getSafetyStock());
        entity.setCreateBy(domain.getCreateBy());
        entity.setUpdateBy(domain.getUpdateBy());
        entity.setCreateTime(domain.getCreateTime());
        entity.setUpdateTime(domain.getUpdateTime());
        return entity;
    }

    /** 库存实体 → 领域对象 */
    public Inventory toDomain(InventoryEntity entity) {
        if (entity == null) {
            return null;
        }
        return Inventory.reconstitute(
                entity.getId(), entity.getWarehouseId(), entity.getSkuId(), entity.getSellerId(),
                entity.getTotalStock(), entity.getLockedStock(), entity.getAvailableStock(),
                entity.getSafetyStock(), entity.getCreateBy(), entity.getUpdateBy(),
                entity.getCreateTime(), entity.getUpdateTime());
    }

    /** 锁定领域对象 → 实体 */
    public StockLockEntity toLockEntity(StockLock domain) {
        if (domain == null) {
            return null;
        }
        StockLockEntity entity = new StockLockEntity();
        entity.setId(domain.getId());
        entity.setInventoryId(domain.getInventoryId());
        entity.setOrderId(domain.getOrderId());
        entity.setSkuId(domain.getSkuId());
        entity.setQuantity(domain.getQuantity());
        entity.setStatus(domain.getStatus() != null ? domain.getStatus().getValue() : null);
        entity.setLockTime(domain.getLockTime());
        entity.setReleaseTime(domain.getReleaseTime());
        entity.setCreateTime(domain.getCreateTime());
        return entity;
    }

    /** 锁定实体 → 领域对象 */
    public StockLock toLockDomain(StockLockEntity entity) {
        if (entity == null) {
            return null;
        }
        return StockLock.reconstitute(
                entity.getId(), entity.getInventoryId(), entity.getOrderId(), entity.getSkuId(),
                entity.getQuantity(),
                entity.getStatus() != null ? StockLockStatus.fromValue(entity.getStatus()) : null,
                entity.getLockTime(), entity.getReleaseTime(), entity.getCreateTime());
    }

    /** 批量转换锁定实体 */
    public List<StockLock> toLockDomainList(List<StockLockEntity> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(this::toLockDomain).collect(Collectors.toList());
    }
}
