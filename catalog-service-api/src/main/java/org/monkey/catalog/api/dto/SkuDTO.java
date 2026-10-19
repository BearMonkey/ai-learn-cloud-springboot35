package org.monkey.catalog.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SKU 信息 DTO —— 对外暴露的 SKU 数据结构
 *
 * <p>供其他微服务通过 Feign 调用时使用，包含规格名称、价格等
 *
 * @author monkey
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuDTO implements Serializable {

    /** SKU ID */
    private Long id;

    /** 所属商品ID */
    private Long productId;

    /** 规格名称（如：红色-256G） */
    private String specName;

    /** 规格图片URL */
    private String specImage;

    /** 价格 */
    private BigDecimal price;

    /** 卖家用户ID */
    private Long sellerId;

    /** 状态：1=在售, 0=停售 */
    private Integer status;
}
