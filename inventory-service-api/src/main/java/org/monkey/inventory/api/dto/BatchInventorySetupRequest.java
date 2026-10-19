package org.monkey.inventory.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 批量库存初始化请求 —— 创建商品时同步设置库存
 *
 * @author monkey
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchInventorySetupRequest implements Serializable {

    /** SKU ID（由 catalog 创建后回填） */
    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    /** 卖家ID */
    @NotNull(message = "卖家ID不能为空")
    private Long sellerId;

    /** 仓库库存分配 */
    @NotEmpty(message = "仓库库存分配不能为空")
    @Valid
    private List<WarehouseStock> warehouseStocks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WarehouseStock implements Serializable {

        /** 仓库ID */
        @NotNull(message = "仓库ID不能为空")
        private Long warehouseId;

        /** 库存数量 */
        @NotNull(message = "库存数量不能为空")
        private Integer totalStock;
    }
}
