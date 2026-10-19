package org.monkey.inventory.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 库存信息 DTO —— 对外暴露的库存数据结构
 *
 * @author monkey
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDTO implements Serializable {

    /** SKU ID */
    private Long skuId;

    /** 总库存 */
    private Integer totalStock;

    /** 已锁定数量 */
    private Integer lockedStock;

    /** 可用库存 */
    private Integer availableStock;
}
