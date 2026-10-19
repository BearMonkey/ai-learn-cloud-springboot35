package org.monkey.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.Data;
import org.monkey.common.core.context.UserContextHolder;
import org.monkey.common.core.dto.Result;
import org.monkey.inventory.api.dto.BatchInventorySetupRequest;
import org.monkey.inventory.api.dto.InventoryDTO;
import org.monkey.inventory.api.dto.StockOperationRequest;
import org.monkey.inventory.api.dto.StockOperationResult;
import org.monkey.inventory.dto.InventoryResponse;
import org.monkey.inventory.service.InventoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库存控制器 —— 卖家管理库存 + Feign 库存操作
 *
 * @author monkey
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    // ==================== 卖家端 ====================

    /** 设置库存（创建或更新） */
    @PutMapping("/warehouses/{warehouseId}/skus/{skuId}")
    public Result<InventoryResponse> setStock(@PathVariable Long warehouseId,
                                                @PathVariable Long skuId,
                                                @Valid @RequestBody SetStockRequest request) {
        Long userId = UserContextHolder.getUserId();
        return Result.success(inventoryService.setStock(userId, warehouseId, skuId,
                request.getTotalStock(), request.getSafetyStock()));
    }

    /** 我的库存总览 */
    @GetMapping("/my")
    public Result<Page<InventoryResponse>> getMyInventory(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        Long userId = UserContextHolder.getUserId();
        return Result.success(inventoryService.getMyInventory(userId, page, size));
    }

    /** 仓库库存明细 */
    @GetMapping("/warehouses/{warehouseId}")
    public Result<Page<InventoryResponse>> getWarehouseInventory(@PathVariable Long warehouseId,
                                                                    @RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "10") int size) {
        return Result.success(inventoryService.getWarehouseInventory(warehouseId, page, size));
    }

    // ==================== Feign 端（供 order-service 调用） ====================

    /** 查询SKU可用库存 */
    @GetMapping("/skus/{skuId}/stock")
    public Result<InventoryDTO> checkStock(@PathVariable Long skuId) {
        return Result.success(inventoryService.checkStock(skuId));
    }

    /** 锁定库存 */
    @PostMapping("/lock")
    public Result<StockOperationResult> lockStock(@Valid @RequestBody StockOperationRequest request) {
        return Result.success(inventoryService.lockStock(request));
    }

    /** 扣减库存 */
    @PostMapping("/deduct")
    public Result<StockOperationResult> deductStock(@Valid @RequestBody StockOperationRequest request) {
        return Result.success(inventoryService.deductStock(request));
    }

    /** 释放库存 */
    @PostMapping("/release")
    public Result<StockOperationResult> releaseStock(@Valid @RequestBody StockOperationRequest request) {
        return Result.success(inventoryService.releaseStock(request));
    }

    /** 批量初始化库存（Feign —— 创建商品时由 catalog 调用） */
    @PostMapping("/batch-setup")
    public Result<Void> batchSetupInventory(@Valid @RequestBody List<BatchInventorySetupRequest> requests) {
        inventoryService.batchSetupInventory(requests);
        return Result.success();
    }

    @Data
    public static class SetStockRequest {
        private Integer totalStock;
        private Integer safetyStock;
    }
}
