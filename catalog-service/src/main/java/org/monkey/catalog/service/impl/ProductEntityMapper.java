package org.monkey.catalog.service.impl;

import org.monkey.catalog.entity.ProductEntity;
import org.monkey.catalog.entity.ProductSkuEntity;
import org.monkey.catalog.model.Product;
import org.monkey.catalog.model.ProductSku;
import org.monkey.catalog.api.enums.ProductStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Entity ↔ Domain Model 转换器
 *
 * @author monkey
 */
@Component
public class ProductEntityMapper {

    /** 领域对象 → 商品实体 */
    public ProductEntity toEntity(Product domain) {
        if (domain == null) {
            return null;
        }
        ProductEntity entity = new ProductEntity();
        entity.setId(domain.getId());
        entity.setTitle(domain.getTitle());
        entity.setDescription(domain.getDescription());
        entity.setImage(domain.getImage());
        entity.setSellerId(domain.getSellerId());
        entity.setCategoryLeafId(domain.getCategoryLeafId());
        entity.setCategoryMainId(domain.getCategoryMainId());
        entity.setCategoryPath(domain.getCategoryPath());
        entity.setStatus(domain.getStatus() != null ? domain.getStatus().getValue() : 0);
        entity.setCreateBy(domain.getCreateBy());
        entity.setUpdateBy(domain.getUpdateBy());
        entity.setCreateTime(domain.getCreateTime());
        entity.setUpdateTime(domain.getUpdateTime());
        return entity;
    }

    /** 领域对象 → SKU 实体 */
    public ProductSkuEntity toSkuEntity(ProductSku sku) {
        if (sku == null) {
            return null;
        }
        ProductSkuEntity entity = new ProductSkuEntity();
        entity.setId(sku.getId());
        entity.setProductId(sku.getProductId());
        entity.setSpecName(sku.getSpecName());
        entity.setSpecImage(sku.getSpecImage());
        entity.setPrice(sku.getPrice());
        entity.setSellerId(sku.getSellerId());
        entity.setStatus(sku.getStatus() != null ? sku.getStatus().getValue() : null);
        return entity;
    }

    /** 商品实体 + SKU 实体列表 → 领域对象 */
    public Product toDomain(ProductEntity entity, List<ProductSkuEntity> skuEntities) {
        if (entity == null) {
            return null;
        }
        List<ProductSku> skus = null;
        if (skuEntities != null) {
            skus = skuEntities.stream()
                    .map(this::toSkuDomain)
                    .collect(Collectors.toList());
        }
        return Product.reconstitute(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getImage(),
                entity.getSellerId(),
                entity.getCategoryLeafId(),
                entity.getCategoryMainId(),
                entity.getCategoryPath(),
                entity.getStatus() != null ? ProductStatus.fromValue(entity.getStatus()) : null,
                entity.getCreateBy(), entity.getUpdateBy(),
                entity.getCreateTime(), entity.getUpdateTime(),
                skus);
    }

    /** SKU 实体 → 领域值对象 */
    public ProductSku toSkuDomain(ProductSkuEntity entity) {
        if (entity == null) {
            return null;
        }
        return ProductSku.reconstitute(
                entity.getId(), entity.getProductId(), entity.getSpecName(), entity.getSpecImage(),
                entity.getPrice(),
                entity.getStatus() != null ? ProductStatus.fromValue(entity.getStatus()) : null,
                entity.getSellerId());
    }
}
