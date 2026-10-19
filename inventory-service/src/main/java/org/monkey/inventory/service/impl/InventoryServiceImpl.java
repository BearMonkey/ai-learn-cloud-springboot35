package org.monkey.inventory.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.inventory.api.dto.BatchInventorySetupRequest;
import org.monkey.inventory.api.dto.InventoryDTO;
import org.monkey.inventory.api.dto.StockOperationRequest;
import org.monkey.inventory.api.dto.StockOperationResult;
import org.monkey.inventory.dto.InventoryResponse;
import org.monkey.inventory.model.BusinessException;
import org.monkey.inventory.model.Inventory;
import org.monkey.inventory.model.StockLock;
import org.monkey.inventory.service.InventoryRepository;
import org.monkey.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 库存服务实现
 *
 * @author monkey
 */
@Service
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final InventoryRepository inventoryRepository;

    public InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional
    public InventoryResponse setStock(Long sellerId, Long warehouseId, Long skuId,
                                       Integer totalStock, Integer safetyStock) {
        // 查询是否已有库存记录
        Inventory inventory = inventoryRepository.findByWarehouseAndSku(warehouseId, skuId)
                .orElse(null);
        if (inventory == null) {
            // 新建库存记录
            inventory = Inventory.create(warehouseId, skuId, sellerId, totalStock, safetyStock);
        } else {
            if (!inventory.belongsTo(sellerId)) {
                throw new BusinessException(403, "无权操作此库存");
            }
            inventory.setTotalStock(totalStock);
            if (safetyStock != null) {
                inventory.setSafetyStock(safetyStock);
            }
        }
        inventory = inventoryRepository.save(inventory);
        return InventoryResponse.from(inventory);
    }

    @Override
    public Page<InventoryResponse> getMyInventory(Long sellerId, int page, int size) {
        Page<Inventory> domainPage = new Page<>(page, size);
        domainPage = inventoryRepository.findBySellerId(sellerId, domainPage);
        List<InventoryResponse> responses = domainPage.getRecords().stream()
                .map(InventoryResponse::from)
                .collect(Collectors.toList());
        Page<InventoryResponse> result = new Page<>(page, size, domainPage.getTotal());
        result.setRecords(responses);
        return result;
    }

    @Override
    public Page<InventoryResponse> getWarehouseInventory(Long warehouseId, int page, int size) {
        Page<Inventory> domainPage = new Page<>(page, size);
        domainPage = inventoryRepository.findByWarehouseId(warehouseId, domainPage);
        List<InventoryResponse> responses = domainPage.getRecords().stream()
                .map(InventoryResponse::from)
                .collect(Collectors.toList());
        Page<InventoryResponse> result = new Page<>(page, size, domainPage.getTotal());
        result.setRecords(responses);
        return result;
    }

    @Override
    public InventoryDTO checkStock(Long skuId) {
        int totalAvailable = inventoryRepository.getTotalAvailableStockBySkuId(skuId);
        // 计算总库存和已锁定
        List<Inventory> inventories = inventoryRepository.findBySkuIdOrderByAvailableStockDesc(skuId);
        int totalStock = inventories.stream().mapToInt(Inventory::getTotalStock).sum();
        int lockedStock = inventories.stream().mapToInt(Inventory::getLockedStock).sum();
        return InventoryDTO.builder()
                .skuId(skuId)
                .totalStock(totalStock)
                .lockedStock(lockedStock)
                .availableStock(totalAvailable)
                .build();
    }

    @Override
    @Transactional
    public StockOperationResult lockStock(StockOperationRequest request) {
        List<StockOperationRequest.StockItem> items = request.getItems();
        // 收集每个 SKU 的锁定信息：最多可用库存的仓库 + 数量
        List<Inventory> lockedInventories = new ArrayList<>();
        List<StockLock> newLocks = new ArrayList<>();

        // 第一步：为每个 SKU 找到库存最充足的仓库并锁定
        for (StockOperationRequest.StockItem item : items) {
            List<Inventory> candidates = inventoryRepository.findBySkuIdOrderByAvailableStockDesc(item.getSkuId());
            if (candidates.isEmpty()) {
                // 回滚已锁定的库存
                rollbackLocks(lockedInventories);
                return StockOperationResult.fail("SKU " + item.getSkuId() + " 没有库存记录");
            }
            // 取可用库存最多的仓库
            Inventory best = candidates.get(0);
            if (best.getAvailableStock() < item.getQuantity()) {
                rollbackLocks(lockedInventories);
                return StockOperationResult.fail("SKU " + item.getSkuId() + " 库存不足，可用:" + best.getAvailableStock() + " 需要:" + item.getQuantity());
            }
            // 锁定库存
            best.lock(item.getQuantity());
            inventoryRepository.save(best);
            lockedInventories.add(best);
            // 创建锁定记录
            StockLock lock = StockLock.create(best.getId(), request.getOrderId(), item.getSkuId(), item.getQuantity());
            inventoryRepository.saveLock(lock);
            newLocks.add(lock);
        }
        log.info("订单 {} 锁定库存成功，共 {} 条锁定记录", request.getOrderId(), newLocks.size());
        return StockOperationResult.ok();
    }

    @Override
    @Transactional
    public StockOperationResult deductStock(StockOperationRequest request) {
        List<StockLock> locks = inventoryRepository.findLocksByOrderId(request.getOrderId());
        if (locks.isEmpty()) {
            return StockOperationResult.fail("订单 " + request.getOrderId() + " 没有锁定记录");
        }
        for (StockLock lock : locks) {
            if (!lock.isLocked()) {
                return StockOperationResult.fail("锁定记录 " + lock.getId() + " 状态不是已锁定");
            }
            // 扣减库存
            Inventory inventory = inventoryRepository.findById(lock.getInventoryId())
                    .orElseThrow(() -> new BusinessException("库存记录不存在: " + lock.getInventoryId()));
            inventory.deduct(lock.getQuantity());
            inventoryRepository.save(inventory);
            // 更新锁定记录状态
            lock.deduct();
        }
        inventoryRepository.updateLocks(locks);
        log.info("订单 {} 扣减库存成功", request.getOrderId());
        return StockOperationResult.ok();
    }

    @Override
    @Transactional
    public StockOperationResult releaseStock(StockOperationRequest request) {
        List<StockLock> locks = inventoryRepository.findLocksByOrderId(request.getOrderId());
        if (locks.isEmpty()) {
            return StockOperationResult.fail("订单 " + request.getOrderId() + " 没有锁定记录");
        }
        for (StockLock lock : locks) {
            if (!lock.isLocked()) {
                continue; // 已处理过的跳过
            }
            // 释放库存
            Inventory inventory = inventoryRepository.findById(lock.getInventoryId())
                    .orElseThrow(() -> new BusinessException("库存记录不存在: " + lock.getInventoryId()));
            inventory.release(lock.getQuantity());
            inventoryRepository.save(inventory);
            // 更新锁定记录状态
            lock.release();
        }
        inventoryRepository.updateLocks(locks);
        log.info("订单 {} 释放库存成功", request.getOrderId());
        return StockOperationResult.ok();
    }

    @Override
    @Transactional
    public void batchSetupInventory(List<BatchInventorySetupRequest> requests) {
        for (BatchInventorySetupRequest req : requests) {
            for (BatchInventorySetupRequest.WarehouseStock ws : req.getWarehouseStocks()) {
                // 检查是否已有库存记录（避免重复设置）
                Inventory inventory = inventoryRepository.findByWarehouseAndSku(ws.getWarehouseId(), req.getSkuId())
                        .orElse(null);
                if (inventory == null) {
                    inventory = Inventory.create(ws.getWarehouseId(), req.getSkuId(), req.getSellerId(), ws.getTotalStock(), 0);
                } else {
                    // 已有记录则累加库存
                    inventory.setTotalStock(inventory.getTotalStock() + ws.getTotalStock());
                }
                inventoryRepository.save(inventory);
            }
        }
        log.info("批量初始化库存完成，共 {} 个 SKU", requests.size());
    }

    /** 回滚已锁定的库存（锁定失败时调用） */
    private void rollbackLocks(List<Inventory> inventories) {
        for (Inventory inv : inventories) {
            try {
                Inventory current = inventoryRepository.findById(inv.getId()).orElse(null);
                if (current != null) {
                    current.release(inv.getLockedStock() - current.getLockedStock());
                    // 简化处理：直接还原
                }
            } catch (Exception e) {
                log.error("回滚库存锁定时出错", e);
            }
        }
    }
}
