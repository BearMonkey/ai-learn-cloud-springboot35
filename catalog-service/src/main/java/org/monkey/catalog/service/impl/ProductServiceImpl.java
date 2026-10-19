package org.monkey.catalog.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.monkey.catalog.dto.CreateProductRequest;
import org.monkey.catalog.dto.CreateSkuRequest;
import org.monkey.catalog.dto.ProductListResponse;
import org.monkey.catalog.dto.ProductResponse;
import org.monkey.catalog.dto.UpdateProductRequest;
import org.monkey.catalog.entity.Category;
import org.monkey.catalog.model.BusinessException;
import org.monkey.catalog.model.Product;
import org.monkey.catalog.model.ProductSku;
import org.monkey.catalog.api.enums.ProductStatus;
import org.monkey.catalog.service.CategoryService;
import org.monkey.catalog.service.ProductRepository;
import org.monkey.catalog.service.ProductService;
import org.monkey.catalog.mq.ProductEventProducer;
import org.monkey.common.core.filter.TraceIdFilter;
import org.monkey.common.mq.constant.EventType;
import org.monkey.common.mq.dto.ProductEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品服务实现
 *
 * @author monkey
 */
@Service
@Slf4j
public class ProductServiceImpl implements ProductService {

    @Autowired
    private CategoryService categoryService;

    private final ProductRepository productRepository;
    private final ProductEventProducer productEventProducer;

    public ProductServiceImpl(ProductRepository productRepository, ProductEventProducer productEventProducer) {
        this.productRepository = productRepository;
        this.productEventProducer = productEventProducer;
    }

    @Override
    @Transactional
    public ProductResponse createProduct(Long sellerId, CreateProductRequest request) {

        // 校验请求参数

        // 将请求 DTO 中的 SKU 转为领域对象
        List<ProductSku> skus = request.getSkus().stream()
                .map(s -> ProductSku.create(s.getSpecName(), s.getSpecImage(), s.getPrice(), sellerId))
                .collect(Collectors.toList());
        // 创建聚合根
        Product product = Product.create(
                request.getTitle(), request.getDescription(), request.getImage(), sellerId, skus);

        // 类目参数补充
        Category category = categoryService.getByCategoryId(request.getCategoryId());
        product.setCategoryLeafId(category.getCategoryId());
        String path = category.getPath();
        product.setCategoryPath(path);
        List<Integer> pathArr = JSONArray.parseArray(path, Integer.class);
        product.setCategoryMainId(pathArr.get(0));

        // 持久化
        product = productRepository.save(product);

        try {
            // 发送 MQ 消息异步初始化库存（替代原先的 Feign 同步调用）
            List<ProductEventMessage.SkuInventorySetup> skuSetups = new ArrayList<>();
            for (int i = 0; i < product.getSkus().size(); i++) {
                ProductSku sku = product.getSkus().get(i);
                CreateSkuRequest skuReq = request.getSkus().get(i);

                List<ProductEventMessage.WarehouseStockEntry> entries = skuReq.getStockEntries().stream()
                        .map(e -> new ProductEventMessage.WarehouseStockEntry(
                                e.getWarehouseId(), e.getTotalStock()))
                        .collect(Collectors.toList());

                ProductEventMessage.SkuInventorySetup setup =
                        new ProductEventMessage.SkuInventorySetup(sku.getId(), entries);
                skuSetups.add(setup);
            }

            String traceId = MDC.get(TraceIdFilter.MDC_KEY);
            log.info("发送 PRODUCT_CREATED 消息: traceId={}, productId={}", traceId, product.getId());
            ProductEventMessage event = ProductEventMessage.ofCreated(traceId,
                    product.getId(), product.getSellerId(), skuSetups, null, EventType.PRODUCT_AUDIT);
            productEventProducer.sendProductEvent(event);
        } catch (Exception e) {
            log.warn("发送 PRODUCT_CREATED 消息失败，对账任务将兜底: productId={}", product.getId(), e);
            throw new BusinessException(500, "发送 PRODUCT_CREATED 消息失败");
        }

        return ProductResponse.from(product);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long sellerId, Long productId, UpdateProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(404, "商品不存在"));
        // 校验归属
        if (!product.belongsTo(sellerId)) {
            throw new BusinessException(403, "无权操作此商品");
        }
        // 更新基本信息
        product.updateInfo(request.getTitle(), request.getDescription(), request.getImage());
        product = productRepository.save(product);
        return ProductResponse.from(product);
    }

    @Override
    @Transactional
    public void updateStatus(Long sellerId, Long productId, ProductStatus status) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(404, "商品不存在"));
        if (!product.belongsTo(sellerId)) {
            throw new BusinessException(403, "无权操作此商品");
        }
        product.setStatus(status);
        productRepository.save(product);
    }

    @Override
    public Page<ProductResponse> getMyProducts(Long sellerId, int page, int size, ProductStatus status) {
        Page<Product> domainPage = new Page<>(page, size);
        domainPage = productRepository.findBySellerId(sellerId, domainPage, status);
        // 转为响应 DTO 分页
        List<ProductResponse> responses = domainPage.getRecords().stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
        Page<ProductResponse> result = new Page<>(page, size, domainPage.getTotal());
        result.setRecords(responses);
        return result;
    }

    @Override
    public Page<ProductListResponse> browseProducts(int page, int size, String keyword) {
        Page<Product> domainPage = new Page<>(page, size);
        domainPage = productRepository.findOnSale(domainPage, keyword);
        // 转为浏览响应 DTO 分页
        List<ProductListResponse> responses = domainPage.getRecords().stream()
                .map(ProductListResponse::from)
                .collect(Collectors.toList());
        Page<ProductListResponse> result = new Page<>(page, size, domainPage.getTotal());
        result.setRecords(responses);
        return result;
    }

    @Override
    public ProductResponse getProductDetail(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(404, "商品不存在"));
        return ProductResponse.from(product);
    }

    @Override
    public ProductResponse getProductBySkuId(Long skuId) {
        Product product = productRepository.findBySkuId(skuId)
                .orElseThrow(() -> new BusinessException(404, "SKU不存在"));
        return ProductResponse.from(product);
    }
}
