package org.monkey.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.monkey.catalog.model.Product;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品响应（含 SKU 列表）
 *
 * @author monkey
 */
@Data
@Builder
@AllArgsConstructor
public class ProductResponse {

    /** 商品ID */
    private Long id;

    /** 商品标题 */
    private String title;

    /** 商品描述 */
    private String description;

    /** 商品主图URL */
    private String image;

    /** 卖家用户ID */
    private Long sellerId;

    /**
     * 叶子类目id
     */
    private Integer categoryLeafId;
    /**
     * 主类目id
     */
    private Integer categoryMainId;
    /**
     * 类目路径
     */
    private String categoryPath; // json格式[1,5,12]

    /** 状态：1=在售, 0=下架 */
    private Integer status;

    /** SKU 列表 */
    private List<SkuResponse> skus;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 从领域对象构造 */
    public static ProductResponse from(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .image(product.getImage())
                .sellerId(product.getSellerId())
                .categoryLeafId(product.getCategoryLeafId())
                .categoryMainId(product.getCategoryMainId())
                .categoryPath(product.getCategoryPath())
                .status(product.getStatus() != null ? product.getStatus().getValue() : null)
                .skus(product.getSkus().stream().map(SkuResponse::from).collect(Collectors.toList()))
                .createTime(product.getCreateTime())
                .updateTime(product.getUpdateTime())
                .build();
    }
}
