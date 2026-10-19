package org.monkey.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.monkey.catalog.model.ProductSku;

import java.math.BigDecimal;

/**
 * SKU 响应
 *
 * @author monkey
 */
@Data
@Builder
@AllArgsConstructor
public class SkuResponse {

    /** SKU ID */
    private Long id;

    /** 卖家ID */
    private Long sellerId;

    /** 商品ID */
    private Long productId;

    /** 规格名称 */
    private String specName;

    /** 规格图片URL */
    private String specImage;

    /** 价格 */
    private BigDecimal price;

    /** 状态：1=在售, 0=停售 */
    private Integer status;

    /** 从领域对象构造 */
    public static SkuResponse from(ProductSku sku) {
        return SkuResponse.builder()
                .id(sku.getId())
                .sellerId(sku.getSellerId())
                .productId(sku.getProductId())
                .specName(sku.getSpecName())
                .specImage(sku.getSpecImage())
                .price(sku.getPrice())
                .status(sku.getStatus() != null ? sku.getStatus().getValue() : null)
                .build();
    }
}
