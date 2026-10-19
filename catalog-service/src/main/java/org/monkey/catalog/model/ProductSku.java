package org.monkey.catalog.model;

import java.math.BigDecimal;

/**
 * 商品 SKU 值对象 —— Product 聚合内部的子实体
 *
 * @author monkey
 */
public class ProductSku {

    /** SKU ID（新建时为空，持久化后赋值） */
    private Long id;

    /** 所属商品ID */
    private Long productId;

    /** 规格名称（如：红色-256G） */
    private String specName;

    /** 规格图片URL */
    private String specImage;

    /** 价格 */
    private BigDecimal price;

    /** 状态：1=在售, 0=停售 */
    private org.monkey.catalog.api.enums.ProductStatus status;

    /** 卖家用户ID */
    private Long sellerId;

    // 仅通过 Product 聚合根创建
    ProductSku() {}

    /**
     * 创建新 SKU（用于新增商品时附带）
     */
    public static ProductSku create(String specName, String specImage, BigDecimal price, Long sellerId) {
        ProductSku sku = new ProductSku();
        sku.specName = specName;
        sku.specImage = specImage;
        sku.price = price;
        sku.sellerId = sellerId;
        sku.status = org.monkey.catalog.api.enums.ProductStatus.ON_SALE;
        return sku;
    }

    /**
     * 从持久化数据重建
     */
    public static ProductSku reconstitute(Long id, Long productId, String specName, String specImage,
                                          BigDecimal price, org.monkey.catalog.api.enums.ProductStatus status, Long sellerId) {
        ProductSku sku = new ProductSku();
        sku.id = id;
        sku.productId = productId;
        sku.specName = specName;
        sku.specImage = specImage;
        sku.price = price;
        sku.status = status;
        sku.sellerId = sellerId;
        return sku;
    }

    /** 绑定到指定商品 */
    void bindToProduct(Long productId) {
        this.productId = productId;
    }

    // ========== getter / setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getSpecName() { return specName; }
    public void setSpecName(String specName) { this.specName = specName; }
    public String getSpecImage() { return specImage; }
    public void setSpecImage(String specImage) { this.specImage = specImage; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public org.monkey.catalog.api.enums.ProductStatus getStatus() { return status; }
    public void setStatus(org.monkey.catalog.api.enums.ProductStatus status) { this.status = status; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
}
