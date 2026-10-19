package org.monkey.catalog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.catalog.api.dto.SkuDTO;
import org.monkey.catalog.dto.ProductListResponse;
import org.monkey.catalog.dto.ProductResponse;
import org.monkey.catalog.service.ProductService;
import org.monkey.common.core.dto.Result;
import org.springframework.web.bind.annotation.*;

/**
 * 商品控制器 —— 买家浏览 + 内部 Feign 端点
 *
 * @author monkey
 */
@RestController
@RequestMapping("/catalog/buyer")
public class BuyerProductController {

    private final ProductService productService;

    public BuyerProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 买家浏览在售商品列表
     *
     * @param page    页码（从1开始）
     * @param size    每页数量
     * @param keyword 标题搜索关键词（可选）
     * @return 商品摘要分页列表
     */
    @GetMapping("/products")
    public Result<Page<ProductListResponse>> browseProducts(@RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "10") int size,
                                                              @RequestParam(required = false) String keyword) {
        return Result.success(productService.browseProducts(page, size, keyword));
    }

    /**
     * 商品详情（含 SKU 列表）
     *
     * @param id 商品ID
     * @return 商品详情
     */
    @GetMapping("/products/{id}")
    public Result<ProductResponse> getProductDetail(@PathVariable("id") Long id) {
        return Result.success(productService.getProductDetail(id));
    }

    /**
     * 根据SKU ID查询SKU信息（供 inventory/order 服务通过 Feign 调用）
     *
     * @param id SKU ID
     * @return SKU 信息
     */
    @GetMapping("/skus/{id}")
    public Result<SkuDTO> getSkuById(@PathVariable("id") Long id) {
        // 通过商品详情获取 SKU，再转换为 SkuDTO
        ProductResponse product = productService.getProductBySkuId(id);
        org.monkey.catalog.dto.SkuResponse sku = product.getSkus().stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new org.monkey.catalog.model.BusinessException(404, "SKU不存在"));
        SkuDTO dto = SkuDTO.builder()
                .id(sku.getId())
                .productId(product.getId())
                .specName(sku.getSpecName())
                .specImage(sku.getSpecImage())
                .price(sku.getPrice())
                .sellerId(product.getSellerId())
                .status(sku.getStatus())
                .build();
        return Result.success(dto);
    }
}
