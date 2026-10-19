package org.monkey.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.monkey.inventory.model.Inventory;

import java.time.LocalDateTime;

/**
 * 库存响应
 *
 * @author monkey
 */
@Data
@Builder
@AllArgsConstructor
public class InventoryResponse {

    private Long id;
    private Long warehouseId;
    private Long skuId;
    private Long sellerId;
    private Integer totalStock;
    private Integer lockedStock;
    private Integer availableStock;
    private Integer safetyStock;
    private LocalDateTime updateTime;

    public static InventoryResponse from(Inventory inv) {
        return InventoryResponse.builder()
                .id(inv.getId())
                .warehouseId(inv.getWarehouseId())
                .skuId(inv.getSkuId())
                .sellerId(inv.getSellerId())
                .totalStock(inv.getTotalStock())
                .lockedStock(inv.getLockedStock())
                .availableStock(inv.getAvailableStock())
                .safetyStock(inv.getSafetyStock())
                .updateTime(inv.getUpdateTime())
                .build();
    }
}
