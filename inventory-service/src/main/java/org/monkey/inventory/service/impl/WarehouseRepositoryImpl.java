package org.monkey.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.inventory.entity.WarehouseEntity;
import org.monkey.inventory.mapper.WarehouseMapper;
import org.monkey.inventory.model.Warehouse;
import org.monkey.inventory.service.WarehouseRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 仓库仓储实现
 *
 * @author monkey
 */
@Repository
public class WarehouseRepositoryImpl implements WarehouseRepository {

    private final WarehouseMapper warehouseMapper;
    private final WarehouseEntityMapper mapper;

    public WarehouseRepositoryImpl(WarehouseMapper warehouseMapper, WarehouseEntityMapper mapper) {
        this.warehouseMapper = warehouseMapper;
        this.mapper = mapper;
    }

    @Override
    public Optional<Warehouse> findById(Long id) {
        WarehouseEntity entity = warehouseMapper.selectById(id);
        return Optional.ofNullable(mapper.toDomain(entity));
    }

    @Override
    public Optional<Warehouse> findByCode(String code) {
        WarehouseEntity entity = warehouseMapper.selectOne(
                new LambdaQueryWrapper<WarehouseEntity>().eq(WarehouseEntity::getCode, code));
        return Optional.ofNullable(mapper.toDomain(entity));
    }

    @Override
    public Page<Warehouse> findBySellerId(Long sellerId, Page<Warehouse> page) {
        LambdaQueryWrapper<WarehouseEntity> wrapper = new LambdaQueryWrapper<WarehouseEntity>()
                .eq(WarehouseEntity::getSellerId, sellerId)
                .orderByDesc(WarehouseEntity::getCreateTime);
        Page<WarehouseEntity> entityPage = new Page<>(page.getCurrent(), page.getSize());
        entityPage = warehouseMapper.selectPage(entityPage, wrapper);
        // 转换为领域分页
        List<Warehouse> records = new ArrayList<>();
        for (WarehouseEntity entity : entityPage.getRecords()) {
            records.add(mapper.toDomain(entity));
        }
        Page<Warehouse> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public Warehouse save(Warehouse warehouse) {
        WarehouseEntity entity = mapper.toEntity(warehouse);
        if (entity.getId() == null) {
            warehouseMapper.insert(entity);
            warehouse.setId(entity.getId());
        } else {
            warehouseMapper.updateById(entity);
        }
        return findById(entity.getId()).orElseThrow();
    }
}
