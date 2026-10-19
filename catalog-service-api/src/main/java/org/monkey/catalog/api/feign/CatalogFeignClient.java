package org.monkey.catalog.api.feign;

import org.monkey.catalog.api.dto.ProductDTO;
import org.monkey.catalog.api.dto.SkuDTO;
import org.monkey.catalog.api.enums.ProductStatus;
import org.monkey.common.core.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 产品目录服务 Feign 客户端接口
 *
 * <p>供其他微服务声明式调用 catalog-service 的商品和 SKU 查询接口。
 * 路径 "/catalog" 与网关 StripPrefix 后的路由保持一致
 *
 * <p>使用示例：
 * <pre>{@code
 * @Autowired
 * private CatalogFeignClient catalogFeignClient;
 *
 * ProductDTO product = catalogFeignClient.getProductById(1L);
 * SkuDTO sku = catalogFeignClient.getSkuById(10L);
 * }</pre>
 *
 * @author monkey
 */
@FeignClient(name = "catalog-service", path = "/catalog")
public interface CatalogFeignClient {

    /**
     * 根据商品ID查询商品信息
     *
     * @param id 商品ID
     * @return 商品信息 DTO
     */
    @GetMapping("/products/{id}")
    Result<ProductDTO> getProductById(@PathVariable("id") Long id);

    /**
     * 根据SKU ID查询SKU信息
     *
     * @param id SKU ID
     * @return SKU 信息 DTO
     */
    @GetMapping("/skus/{id}")
    Result<SkuDTO> getSkuById(@PathVariable("id") Long id);

    /**
     * 商品审核通过
     *
     * @param sellerId 卖家ID
     * @param productId 商品ID
     * @param status 审核状态
     */
    @PutMapping("/products/audit")
    void productAudit(@RequestParam("sellerId") Long sellerId,
                      @RequestParam("productId") Long productId,
                      @RequestParam("status") ProductStatus status);
}
