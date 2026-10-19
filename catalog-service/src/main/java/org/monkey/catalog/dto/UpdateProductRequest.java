package org.monkey.catalog.dto;

import lombok.Data;

/**
 * 更新商品请求
 *
 * @author monkey
 */
@Data
public class UpdateProductRequest {

    /** 商品标题 */
    private String title;

    /** 商品描述 */
    private String description;

    /** 商品主图URL */
    private String image;
}
