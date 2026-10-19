package org.monkey.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.inventory.entity.InventoryEntity;
import org.monkey.inventory.entity.StockLockEntity;
import org.monkey.inventory.mapper.InventoryMapper;
import org.monkey.inventory.mapper.StockLockMapper;
import org.monkey.inventory.model.Inventory;
import org.monkey.inventory.model.StockLock;
import org.monkey.inventory.service.InventoryRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 库存仓储实现
 *
 * @author monkey
 */
@Repository
public class InventoryRepositoryImpl implements InventoryRepository {

    private final InventoryMapper inventoryMapper;
    private final StockLockMapper stockLockMapper;
    private final InventoryEntityMapper mapper;

    public InventoryRepositoryImpl(InventoryMapper inventoryMapper, StockLockMapper stockLockMapper,
                                    InventoryEntityMapper mapper) {
        this.inventoryMapper = inventoryMapper;
        this.stockLockMapper = stockLockMapper;
        this.mapper = mapper;
    }

    @Override
    public Optional<Inventory> findById(Long id) {
        InventoryEntity entity = inventoryMapper.selectById(id);
        return Optional.ofNullable(mapper.toDomain(entity));
    }

    @Override
    public Optional<Inventory> findByWarehouseAndSku(Long warehouseId, Long skuId) {
        InventoryEntity entity = inventoryMapper.selectOne(
                new LambdaQueryWrapper<InventoryEntity>()
                        .eq(InventoryEntity::getWarehouseId, warehouseId)
                        .eq(InventoryEntity::getSkuId, skuId));
        return Optional.ofNullable(mapper.toDomain(entity));
    }

    @Override
    public List<Inventory> findBySkuIdOrderByAvailableStockDesc(Long skuId) {
        List<InventoryEntity> entities = inventoryMapper.selectList(
                new LambdaQueryWrapper<InventoryEntity>()
                        .eq(InventoryEntity::getSkuId, skuId)
                        .orderByDesc(InventoryEntity::getAvailableStock));
        List<Inventory> result = new ArrayList<>();
        for (InventoryEntity entity : entities) {
            result.add(mapper.toDomain(entity));
        }
        return result;
    }

    @Override
    public int getTotalAvailableStockBySkuId(Long skuId) {
        List<InventoryEntity> entities = inventoryMapper.selectList(
                new LambdaQueryWrapper<InventoryEntity>()
                        .eq(InventoryEntity::getSkuId, skuId));
        return entities.stream().mapToInt(InventoryEntity::getAvailableStock).sum();
    }

    @Override
    public Page<Inventory> findBySellerId(Long sellerId, Page<Inventory> page) {
        LambdaQueryWrapper<InventoryEntity> wrapper = new LambdaQueryWrapper<InventoryEntity>()
                .eq(InventoryEntity::getSellerId, sellerId)
                .orderByDesc(InventoryEntity::getCreateTime);
        Page<InventoryEntity> entityPage = new Page<>(page.getCurrent(), page.getSize());
        entityPage = inventoryMapper.selectPage(entityPage, wrapper);
        List<Inventory> records = new ArrayList<>();
        for (InventoryEntity entity : entityPage.getRecords()) {
            records.add(mapper.toDomain(entity));
        }
        Page<Inventory> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public Page<Inventory> findByWarehouseId(Long warehouseId, Page<Inventory> page) {
        LambdaQueryWrapper<InventoryEntity> wrapper = new LambdaQueryWrapper<InventoryEntity>()
                .eq(InventoryEntity::getWarehouseId, warehouseId)
                .orderByDesc(InventoryEntity::getCreateTime);
        Page<InventoryEntity> entityPage = new Page<>(page.getCurrent(), page.getSize());
        entityPage = inventoryMapper.selectPage(entityPage, wrapper);
        List<Inventory> records = new ArrayList<>();
        for (InventoryEntity entity : entityPage.getRecords()) {
            records.add(mapper.toDomain(entity));
        }
        Page<Inventory> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    @Transactional
    public Inventory save(Inventory inventory) {
        InventoryEntity entity = mapper.toEntity(inventory);
        if (entity.getId() == null) {
            inventoryMapper.insert(entity);
            inventory.setId(entity.getId());
        } else {
            inventoryMapper.updateById(entity);
        }
        return findById(entity.getId()).orElseThrow();
    }

    @Override
    public List<StockLock> findLocksByOrderId(Long orderId) {
        List<StockLockEntity> entities = stockLockMapper.selectList(
                new LambdaQueryWrapper<StockLockEntity>()
                        .eq(StockLockEntity::getOrderId, orderId));
        return mapper.toLockDomainList(entities);
    }

    @Override
    public StockLock saveLock(StockLock lock) {
        StockLockEntity entity = mapper.toLockEntity(lock);
        if (entity.getId() == null) {
            stockLockMapper.insert(entity);
            lock.setId(entity.getId());
        } else {
            stockLockMapper.updateById(entity);
        }
        return lock;
    }

    @Override
    public void updateLocks(List<StockLock> locks) {
        for (StockLock lock : locks) {
            StockLockEntity entity = mapper.toLockEntity(lock);
            stockLockMapper.updateById(entity);
        }
    }

    @Override
    public List<Map<String, Object>> summaryBySku() {
        return inventoryMapper.summaryBySku();
    }
}
