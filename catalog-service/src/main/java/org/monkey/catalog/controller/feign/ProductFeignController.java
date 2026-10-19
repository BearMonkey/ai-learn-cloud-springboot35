package org.monkey.catalog.controller.feign;

import lombok.extern.slf4j.Slf4j;
import org.monkey.catalog.api.dto.ProductDTO;
import org.monkey.catalog.api.dto.SkuDTO;
import org.monkey.catalog.api.enums.ProductStatus;
import org.monkey.catalog.dto.ProductResponse;
import org.monkey.catalog.service.ProductService;
import org.monkey.catalog.service.convertor.ProductSkuConvertor;
import org.monkey.common.core.dto.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * 商品控制器 —— 内部 Feign 端点
 *
 * @author monkey
 */
@Slf4j
@RestController
@RequestMapping("/catalog")
public class ProductFeignController {

    private final ProductSkuConvertor skuConvertor = ProductSkuConvertor.of();

    private final ProductService productService;

    public ProductFeignController(ProductService productService) {
        this.productService = productService;
    }
    /**
     * 根据商品ID查询商品信息
     *
     * @param id 商品ID
     * @return 商品信息 DTO
     */
    @GetMapping("/products/{id}")
    public Result<ProductDTO> getProductById(@PathVariable("id") Long id) {
        ProductResponse productDetail = productService.getProductDetail(id);
        if (Objects.nonNull(productDetail)) {
            return Result.success(ProductDTO.builder()
                    .id(productDetail.getId())
                    .title(productDetail.getTitle())
                    .description(productDetail.getDescription())
                    .image(productDetail.getImage())
                    .sellerId(productDetail.getSellerId())
                    .categoryLeafId(productDetail.getCategoryLeafId())
                    .categoryMainId(productDetail.getCategoryMainId())
                    .categoryPath(productDetail.getCategoryPath())
                    .status(productDetail.getStatus())
                    .skus(skuConvertor.buildSkus(productDetail.getSkus()))
                    .createTime(productDetail.getCreateTime())
                    .updateTime(productDetail.getUpdateTime())
                    .build());
        }
        return Result.error("商品不存在");
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

    /**
     * 商品审核通过
     *
     * @param sellerId 卖家ID
     * @param productId 商品ID
     * @param status 审核状态
     */
    @PutMapping("/products/audit")
    public void productAudit(@RequestParam("sellerId") Long sellerId,
                             @RequestParam("productId") Long productId,
                             @RequestParam("status") ProductStatus status) {
        productService.updateStatus(sellerId, productId, status);
    }

    /**
     * 商品审核拒绝
     *
     * @param sellerId 卖家ID
     * @param productId 商品ID
     */
    @PutMapping("/products/audit/reject")
    public void productAuditReject(@RequestParam("sellerId") Long sellerId, @RequestParam("productId") Long productId) {
        productService.updateStatus(sellerId, productId, ProductStatus.REJECTED);
    }
}
