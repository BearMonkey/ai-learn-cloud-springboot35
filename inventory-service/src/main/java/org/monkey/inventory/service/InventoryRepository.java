package org.monkey.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.inventory.model.Inventory;
import org.monkey.inventory.model.StockLock;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 库存仓储接口
 *
 * @author monkey
 */
public interface InventoryRepository {

    /** 根据ID查询 */
    Optional<Inventory> findById(Long id);

    /** 根据仓库+SKU查询 */
    Optional<Inventory> findByWarehouseAndSku(Long warehouseId, Long skuId);

    /** 查询某SKU所有仓库中可用库存最大的记录 */
    List<Inventory> findBySkuIdOrderByAvailableStockDesc(Long skuId);

    /** 查询某SKU的库存总量（跨仓库汇总） */
    int getTotalAvailableStockBySkuId(Long skuId);

    /** 卖家分页查询库存 */
    Page<Inventory> findBySellerId(Long sellerId, Page<Inventory> page);

    /** 根据仓库分页查询库存 */
    Page<Inventory> findByWarehouseId(Long warehouseId, Page<Inventory> page);

    /** 保存库存 */
    Inventory save(Inventory inventory);

    /** 根据订单ID查询锁定记录 */
    List<StockLock> findLocksByOrderId(Long orderId);

    /** 保存锁定记录 */
    StockLock saveLock(StockLock lock);

    /** 批量更新锁定记录 */
    void updateLocks(List<StockLock> locks);

    /** 按 SKU 汇总库存（用于 Redis ↔ DB 对账） */
    List<Map<String, Object>> summaryBySku();
}
