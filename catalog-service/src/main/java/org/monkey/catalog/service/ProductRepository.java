package org.monkey.catalog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.catalog.model.Product;
import org.monkey.catalog.api.enums.ProductStatus;

import java.util.Optional;

/**
 * 商品仓储接口
 *
 * @author monkey
 */
public interface ProductRepository {

    /** 根据ID查询商品（含SKU） */
    Optional<Product> findById(Long id);

    /** 根据卖家ID分页查询商品 */
    Page<Product> findBySellerId(Long sellerId, Page<Product> page, ProductStatus status);

    /** 分页查询在售商品（买家浏览，可按标题搜索） */
    Page<Product> findOnSale(Page<Product> page, String keyword);

    /** 保存商品（新建或更新）并级联保存 SKU */
    Product save(Product product);

    /** 根据SKU ID查找所属商品 */
    Optional<Product> findBySkuId(Long skuId);
}
