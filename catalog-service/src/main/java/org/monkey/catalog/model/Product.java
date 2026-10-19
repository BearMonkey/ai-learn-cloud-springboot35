package org.monkey.catalog.model;

import lombok.Data;
import org.monkey.catalog.api.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Product 聚合根
 *
 * <p>商品是聚合根，SKU 是聚合内部的子实体。所有对 SKU 的操作都通过 Product 进行
 *
 * @author monkey
 */
@Data
public class Product {

    private Long id;
    private String title;
    private String description;
    private String image;
    private Long sellerId;
    private Integer categoryLeafId;
    private Integer categoryMainId;
    private String categoryPath; // json格式[1,5,12]
    private ProductStatus status;
    private Long createBy;
    private Long updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 聚合内部的 SKU 列表 */
    private List<ProductSku> skus = new ArrayList<>();

    /**
     * 创建新商品（含 SKU 列表）
     */
    public static Product create(String title, String description, String image,
                                  Long sellerId, List<ProductSku> skus) {
        Product product = new Product();
        product.title = title;
        product.description = description;
        product.image = image;
        product.sellerId = sellerId;
        product.status = ProductStatus.ON_REVIEW;
        product.createBy = sellerId;
        product.updateBy = sellerId;
        product.createTime = LocalDateTime.now();
        product.updateTime = LocalDateTime.now();
        // 绑定 SKU 列表到本商品
        product.skus = skus != null ? new ArrayList<>(skus) : new ArrayList<>();
        return product;
    }

    /**
     * 从持久化数据重建聚合
     */
    public static Product reconstitute(Long id, String title, String description, String image,
                                        Long sellerId,
                                        Integer categoryLeafId, Integer categoryMainId, String categoryPath,
                                        ProductStatus status,
                                        Long createBy, Long updateBy,
                                        LocalDateTime createTime, LocalDateTime updateTime,
                                        List<ProductSku> skus) {
        Product product = new Product();
        product.id = id;
        product.title = title;
        product.description = description;
        product.image = image;
        product.sellerId = sellerId;
        product.categoryLeafId = categoryLeafId;
        product.categoryMainId = categoryMainId;
        product.categoryPath = categoryPath;
        product.status = status;
        product.createBy = createBy;
        product.updateBy = updateBy;
        product.createTime = createTime;
        product.updateTime = updateTime;
        product.skus = skus != null ? new ArrayList<>(skus) : new ArrayList<>();
        return product;
    }

    /**
     * 更新商品基本信息（标题、描述、主图）
     */
    public void updateInfo(String title, String description, String image) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        this.description = description;
        if (image != null) {
            this.image = image;
        }
        this.updateTime = LocalDateTime.now();
    }

    /** 上架 */
    public void putOnSale() {
        if (this.status == org.monkey.catalog.api.enums.ProductStatus.ON_SALE) {
            throw new BusinessException("商品已在售");
        }
        this.status = org.monkey.catalog.api.enums.ProductStatus.ON_SALE;
        this.updateTime = LocalDateTime.now();
    }

    /** 下架 */
    public void takeOffSale() {
        if (this.status == org.monkey.catalog.api.enums.ProductStatus.OFF_SALE) {
            throw new BusinessException("商品已下架");
        }
        this.status = ProductStatus.OFF_SALE;
        this.updateTime = LocalDateTime.now();
    }

    /** 是否为当前卖家所有 */
    public boolean belongsTo(Long sellerId) {
        return this.sellerId.equals(sellerId);
    }

    /** 是否在售 */
    public boolean isOnSale() {
        return this.status == org.monkey.catalog.api.enums.ProductStatus.ON_SALE;
    }

    /** 获取所有 SKU 中最小的价格 */
    public BigDecimal getMinPrice() {
        return skus.stream()
                .map(ProductSku::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }
}
