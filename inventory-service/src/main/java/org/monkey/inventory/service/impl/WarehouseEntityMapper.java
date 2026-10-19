package org.monkey.inventory.service.impl;

import org.monkey.inventory.entity.WarehouseEntity;
import org.monkey.inventory.model.Warehouse;
import org.monkey.inventory.model.WarehouseStatus;
import org.springframework.stereotype.Component;

/**
 * Warehouse Entity ↔ Domain Model 转换器
 *
 * @author monkey
 */
@Component
public class WarehouseEntityMapper {

    /** 领域对象 → 实体 */
    public WarehouseEntity toEntity(Warehouse domain) {
        if (domain == null) {
            return null;
        }
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(domain.getId());
        entity.setCode(domain.getCode());
        entity.setName(domain.getName());
        entity.setSellerId(domain.getSellerId());
        entity.setAddress(domain.getAddress());
        entity.setStatus(domain.getStatus() != null ? domain.getStatus().getValue() : null);
        entity.setCreateBy(domain.getCreateBy());
        entity.setUpdateBy(domain.getUpdateBy());
        entity.setCreateTime(domain.getCreateTime());
        entity.setUpdateTime(domain.getUpdateTime());
        return entity;
    }

    /** 实体 → 领域对象 */
    public Warehouse toDomain(WarehouseEntity entity) {
        if (entity == null) {
            return null;
        }
        return Warehouse.reconstitute(
                entity.getId(), entity.getCode(), entity.getName(), entity.getSellerId(),
                entity.getAddress(),
                entity.getStatus() != null ? WarehouseStatus.fromValue(entity.getStatus()) : null,
                entity.getCreateBy(), entity.getUpdateBy(),
                entity.getCreateTime(), entity.getUpdateTime());
    }
}
