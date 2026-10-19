package org.monkey.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.monkey.catalog.model.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品浏览列表响应（摘要，不展示 SKU 明细）
 *
 * @author monkey
 */
@Data
@Builder
@AllArgsConstructor
public class ProductListResponse {

    /** 商品ID */
    private Long id;

    /** 商品标题 */
    private String title;

    /** 商品主图URL */
    private String image;

    /** 卖家用户ID */
    private Long sellerId;

    /** 状态 */
    private Integer status;

    /** 最低价（取所有 SKU 的最低价） */
    private BigDecimal minPrice;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 从领域对象构造 */
    public static ProductListResponse from(Product product) {
        return ProductListResponse.builder()
                .id(product.getId())
                .title(product.getTitle())
                .image(product.getImage())
                .sellerId(product.getSellerId())
                .status(product.getStatus() != null ? product.getStatus().getValue() : null)
                .minPrice(product.getMinPrice())
                .createTime(product.getCreateTime())
                .build();
    }
}
