package org.monkey.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.inventory.api.dto.BatchInventorySetupRequest;
import org.monkey.inventory.api.dto.InventoryDTO;
import org.monkey.inventory.api.dto.StockOperationRequest;
import org.monkey.inventory.api.dto.StockOperationResult;
import org.monkey.inventory.dto.InventoryResponse;

import java.util.List;

/**
 * 库存服务接口
 *
 * @author monkey
 */
public interface InventoryService {

    /** 设置库存（创建或更新） */
    InventoryResponse setStock(Long sellerId, Long warehouseId, Long skuId, Integer totalStock, Integer safetyStock);

    /** 卖家库存总览 */
    Page<InventoryResponse> getMyInventory(Long sellerId, int page, int size);

    /** 仓库库存明细 */
    Page<InventoryResponse> getWarehouseInventory(Long warehouseId, int page, int size);

    /** 查询SKU可用库存（Feign） */
    InventoryDTO checkStock(Long skuId);

    /** 锁定库存（Feign） */
    StockOperationResult lockStock(StockOperationRequest request);

    /** 扣减库存（Feign） */
    StockOperationResult deductStock(StockOperationRequest request);

    /** 释放库存（Feign） */
    StockOperationResult releaseStock(StockOperationRequest request);

    /** 批量初始化库存（Feign —— 创建商品时由 catalog 调用） */
    void batchSetupInventory(List<BatchInventorySetupRequest> requests);
}
