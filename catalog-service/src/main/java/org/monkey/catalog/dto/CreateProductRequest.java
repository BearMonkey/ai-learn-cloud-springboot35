package org.monkey.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 创建商品请求
 *
 * @author monkey
 */
@Data
public class CreateProductRequest {

    /** 商品标题 */
    @NotBlank(message = "商品标题不能为空")
    private String title;

    /** 商品描述 */
    private String description;

    /** 商品主图URL */
    private String image;

    /** 商品类目ID */
    private Integer categoryId;

    /** SKU 列表（至少一个） */
    @NotEmpty(message = "至少需要一个 SKU")
    @Valid
    private List<CreateSkuRequest> skus;
}
