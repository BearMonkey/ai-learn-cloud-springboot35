package org.monkey.catalog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.catalog.api.dto.ProductDTO;
import org.monkey.catalog.dto.CreateProductRequest;
import org.monkey.catalog.dto.ProductListResponse;
import org.monkey.catalog.dto.ProductResponse;
import org.monkey.catalog.dto.UpdateProductRequest;
import org.monkey.catalog.api.enums.ProductStatus;

/**
 * 商品服务接口
 *
 * @author monkey
 */
public interface ProductService {

    /** 创建商品（含SKU） */
    ProductResponse createProduct(Long sellerId, CreateProductRequest request);

    /** 更新商品基本信息 */
    ProductResponse updateProduct(Long sellerId, Long productId, UpdateProductRequest request);

    /** 上架或下架商品 */
    void updateStatus(Long sellerId, Long productId, ProductStatus status);

    /** 卖家查询自己的商品列表 */
    Page<ProductResponse> getMyProducts(Long sellerId, int page, int size, ProductStatus status);

    /** 买家浏览在售商品 */
    Page<ProductListResponse> browseProducts(int page, int size, String keyword);

    /** 买家查看商品详情（含SKU列表） */
    ProductResponse getProductDetail(Long productId);

    /** 根据SKU ID查询所属商品（供 Feign 端点调用） */
    ProductResponse getProductBySkuId(Long skuId);
}
