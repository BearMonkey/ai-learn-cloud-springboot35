package org.monkey.catalog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.monkey.catalog.dto.CreateProductRequest;
import org.monkey.catalog.dto.ProductResponse;
import org.monkey.catalog.dto.UpdateProductRequest;
import org.monkey.catalog.api.enums.ProductStatus;
import org.monkey.catalog.service.ProductService;
import org.monkey.common.core.context.UserContextHolder;
import org.monkey.common.core.dto.Result;
import org.springframework.web.bind.annotation.*;

/**
 * 卖家商品控制器 —— 商品 CRUD
 *
 * @author monkey
 */
@RestController
@RequestMapping("/catalog/seller")
public class SellerProductController {

    private final ProductService productService;

    public SellerProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 创建商品（含 SKU） */
    @PostMapping("/products")
    public Result<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        Long userId = UserContextHolder.getUserId();
        return Result.success(productService.createProduct(userId, request));
    }

    /** 更新商品基本信息 */
    @PutMapping("/products/{id}")
    public Result<ProductResponse> updateProduct(@PathVariable("id") Long productId,
                                                   @Valid @RequestBody UpdateProductRequest request) {
        Long userId = UserContextHolder.getUserId();
        return Result.success(productService.updateProduct(userId, productId, request));
    }

    /** 商品上架或下架 */
    @PutMapping("/products/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long productId,
                                       @RequestParam("status") int status) {
        Long userId = UserContextHolder.getUserId();
        productService.updateStatus(userId, productId, ProductStatus.fromValue(status));
        return Result.success();
    }

    /** 卖家查询自己的商品列表 */
    @GetMapping("/products/my")
    public Result<Page<ProductResponse>> getMyProducts(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size,
                                                         @RequestParam(required = false) Integer status) {
        Long userId = UserContextHolder.getUserId();
        ProductStatus productStatus = status != null ? ProductStatus.fromValue(status) : null;
        return Result.success(productService.getMyProducts(userId, page, size, productStatus));
    }
}
