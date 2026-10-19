package org.monkey.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.monkey.common.core.context.UserContextHolder;
import org.monkey.common.core.dto.Result;
import org.monkey.inventory.dto.WarehouseResponse;
import org.monkey.inventory.service.WarehouseService;
import org.springframework.web.bind.annotation.*;

/**
 * 仓库控制器 —— 卖家管理仓库
 *
 * @author monkey
 */
@RestController
@RequestMapping("/inventory")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    /** 创建仓库 */
    @PostMapping("/warehouses")
    public Result<WarehouseResponse> create(@Valid @RequestBody CreateWarehouseRequest request) {
        Long userId = UserContextHolder.getUserId();
        return Result.success(warehouseService.createWarehouse(userId, request.getCode(), request.getName(), request.getAddress()));
    }

    /** 更新仓库信息 */
    @PutMapping("/warehouses/{id}")
    public Result<WarehouseResponse> update(@PathVariable("id") Long id,
                                              @Valid @RequestBody UpdateWarehouseRequest request) {
        Long userId = UserContextHolder.getUserId();
        return Result.success(warehouseService.updateWarehouse(userId, id, request.getName(), request.getAddress()));
    }

    /** 启用/停用仓库 */
    @PutMapping("/warehouses/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id,
                                       @RequestParam("status") int status) {
        Long userId = UserContextHolder.getUserId();
        warehouseService.updateStatus(userId, id, status);
        return Result.success();
    }

    /** 我的仓库列表 */
    @GetMapping("/warehouses/my")
    public Result<Page<WarehouseResponse>> getMyWarehouses(@RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
        Long userId = UserContextHolder.getUserId();
        return Result.success(warehouseService.getMyWarehouses(userId, page, size));
    }

    /** 仓库详情 */
    @GetMapping("/warehouses/{id}")
    public Result<WarehouseResponse> getDetail(@PathVariable("id") Long id) {
        return Result.success(warehouseService.getWarehouseDetail(id));
    }

    @Data
    public static class CreateWarehouseRequest {
        @NotBlank(message = "仓库编码不能为空")
        private String code;
        @NotBlank(message = "仓库名称不能为空")
        private String name;
        private String address;
    }

    @Data
    public static class UpdateWarehouseRequest {
        private String name;
        private String address;
    }
}
