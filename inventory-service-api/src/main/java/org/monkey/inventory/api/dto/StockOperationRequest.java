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
 * 库存操作请求 —— 锁定/扣减/释放库存
 *
 * @author monkey
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockOperationRequest implements Serializable {

    /** 订单ID */
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    /** 操作明细 */
    @NotEmpty(message = "操作明细不能为空")
    @Valid
    private List<StockItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockItem implements Serializable {

        /** SKU ID */
        @NotNull(message = "SKU ID不能为空")
        private Long skuId;

        /** 数量 */
        @NotNull(message = "数量不能为空")
        private Integer quantity;
    }
}
