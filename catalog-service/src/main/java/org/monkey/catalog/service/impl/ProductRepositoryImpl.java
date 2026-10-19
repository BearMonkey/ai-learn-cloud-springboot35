package org.monkey.catalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.catalog.entity.ProductEntity;
import org.monkey.catalog.entity.ProductSkuEntity;
import org.monkey.catalog.mapper.ProductMapper;
import org.monkey.catalog.mapper.ProductSkuMapper;
import org.monkey.catalog.model.Product;
import org.monkey.catalog.api.enums.ProductStatus;
import org.monkey.catalog.service.ProductRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 商品仓储实现
 *
 * @author monkey
 */
@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductEntityMapper mapper;

    public ProductRepositoryImpl(ProductMapper productMapper, ProductSkuMapper skuMapper, ProductEntityMapper mapper) {
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.mapper = mapper;
    }

    @Override
    public Optional<Product> findById(Long id) {
        ProductEntity entity = productMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }

        // 查询关联的 SKU 列表
        List<ProductSkuEntity> skuEntities = skuMapper.selectByProductId(entity.getId());
        return Optional.of(mapper.toDomain(entity, skuEntities));
    }

    @Override
    public Page<Product> findBySellerId(Long sellerId, Page<Product> page, ProductStatus status) {
        // 构建查询条件
        LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<ProductEntity>()
                .eq(ProductEntity::getSellerId, sellerId);
        if (status != null) {
            wrapper.eq(ProductEntity::getStatus, status.getValue());
        }
        wrapper.orderByDesc(ProductEntity::getCreateTime);

        // 查询实体分页
        Page<ProductEntity> entityPage = new Page<>(page.getCurrent(), page.getSize());
        entityPage = productMapper.selectPage(entityPage, wrapper);

        // 转换为领域对象分页
        List<Product> records = new ArrayList<>();
        for (ProductEntity entity : entityPage.getRecords()) {
            List<ProductSkuEntity> skuEntities = skuMapper.selectList(
                    new QueryWrapper<ProductSkuEntity>().eq("product_id", entity.getId()));
            records.add(mapper.toDomain(entity, skuEntities));
        }
        Page<Product> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public Page<Product> findOnSale(Page<Product> page, String keyword) {
        // 构建查询条件：只查在售商品
        LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<ProductEntity>()
                .eq(ProductEntity::getStatus, ProductStatus.ON_SALE.getValue());
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(ProductEntity::getTitle, keyword);
        }
        wrapper.orderByDesc(ProductEntity::getCreateTime);

        // 查询实体分页
        Page<ProductEntity> entityPage = new Page<>(page.getCurrent(), page.getSize());
        entityPage = productMapper.selectPage(entityPage, wrapper);

        // 转换为领域对象分页（浏览列表不需要 SKU 明细）
        List<Product> records = new ArrayList<>();
        for (ProductEntity entity : entityPage.getRecords()) {
            List<ProductSkuEntity> skuEntities = skuMapper.selectList(
                    new QueryWrapper<ProductSkuEntity>().eq("product_id", entity.getId()));
            records.add(mapper.toDomain(entity, skuEntities));
        }
        Page<Product> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    @Transactional
    public Product save(Product product) {
        ProductEntity entity = mapper.toEntity(product);
        // 插入或更新商品
        if (entity.getId() == null) {
            productMapper.insert(entity);
            product.setId(entity.getId());
        } else {
            productMapper.updateById(entity);
            // 先删除旧 SKU，再重新插入（简单策略，避免复杂 diff）
            skuMapper.delete(new QueryWrapper<ProductSkuEntity>().eq("product_id", entity.getId()));
        }
        // 保存 SKU 列表
        if (product.getSkus() != null) {
            for (var sku : product.getSkus()) {
                sku.setProductId(entity.getId());
                ProductSkuEntity skuEntity = mapper.toSkuEntity(sku);
                skuMapper.insert(skuEntity);
                sku.setId(skuEntity.getId());
            }
        }
        return findById(entity.getId()).orElseThrow();
    }

    @Override
    public Optional<Product> findBySkuId(Long skuId) {
        ProductSkuEntity skuEntity = skuMapper.selectById(skuId);
        if (skuEntity == null) {
            return Optional.empty();
        }
        // 通过 SKU 关联的 product_id 查找所属商品
        return findById(skuEntity.getProductId());
    }
}
