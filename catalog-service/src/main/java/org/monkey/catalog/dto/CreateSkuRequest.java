package org.monkey.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建 SKU 请求
 *
 * @author monkey
 */
@Data
public class CreateSkuRequest {

    /** 规格名称，如："红色-256G" */
    @NotBlank(message = "规格名称不能为空")
    private String specName;

    /** 规格图片URL */
    private String specImage;

    /** 价格 */
    @NotNull(message = "价格不能为空")
    private BigDecimal price;

    /** 初始库存分配（仓库 + 数量） */
    @NotEmpty(message = "至少需要设置一个仓库的库存")
    @Valid
    private List<StockEntry> stockEntries;

    @Data
    public static class StockEntry {

        /** 仓库ID */
        @NotNull(message = "仓库ID不能为空")
        private Long warehouseId;

        /** 初始库存数量 */
        @NotNull(message = "库存数量不能为空")
        private Integer totalStock;
    }
}
