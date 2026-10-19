package org.monkey.catalog.service.convertor;

import lombok.extern.slf4j.Slf4j;
import org.monkey.catalog.api.dto.SkuDTO;
import org.monkey.catalog.dto.SkuResponse;

import java.util.List;

@Slf4j
public class ProductSkuConvertor {
    public static ProductSkuConvertor of() {
        return new ProductSkuConvertor();
    }

    /**
     * Convert a list of SkuResponse to a list of SkuDTO.
     *
     * @param skus the list of SkuResponse
     * @return the list of SkuDTO
     */
    public List<SkuDTO> buildSkus(List<SkuResponse> skus) {
        return skus.stream().map(sku -> SkuDTO.builder()
                .id(sku.getId())
                .sellerId(sku.getSellerId())
                .productId(sku.getProductId())
                .specName(sku.getSpecName())
                .specImage(sku.getSpecImage())
                .price(sku.getPrice())
                .status(sku.getStatus())
                .build()).toList();
    }
}
