package org.monkey.inventory.api.feign;

import org.monkey.inventory.api.dto.BatchInventorySetupRequest;
import org.monkey.inventory.api.dto.InventoryDTO;
import org.monkey.inventory.api.dto.StockOperationRequest;
import org.monkey.inventory.api.dto.StockOperationResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 库存服务 Feign 客户端接口
 *
 * <p>供 order-service 调用库存锁定/扣减/释放操作
 *
 * @author monkey
 */
@FeignClient(name = "inventory-service", path = "/inventory")
public interface InventoryFeignClient {

    /**
     * 查询 SKU 可用库存总量
     *
     * @param skuId SKU ID
     * @return 库存信息
     */
    @GetMapping("/skus/{skuId}/stock")
    InventoryDTO checkStock(@PathVariable("skuId") Long skuId);

    /**
     * 锁定库存（下单时调用）
     *
     * @param request 锁定请求（订单ID + SKU列表）
     * @return 操作结果
     */
    @PostMapping("/lock")
    StockOperationResult lockStock(@RequestBody StockOperationRequest request);

    /**
     * 扣减库存（发货时调用）
     *
     * @param request 扣减请求（订单ID）
     * @return 操作结果
     */
    @PostMapping("/deduct")
    StockOperationResult deductStock(@RequestBody StockOperationRequest request);

    /**
     * 释放库存（取消订单时调用）
     *
     * @param request 释放请求（订单ID）
     * @return 操作结果
     */
    @PostMapping("/release")
    StockOperationResult releaseStock(@RequestBody StockOperationRequest request);

    /**
     * 批量初始化库存（创建商品时由 catalog-service 调用）
     *
     * @param requests 多个 SKU 的库存设置（一个 SKU 可分配多个仓库）
     */
    @PostMapping("/batch-setup")
    void batchSetupInventory(@RequestBody List<BatchInventorySetupRequest> requests);
}
