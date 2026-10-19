package org.monkey.catalog.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品信息 DTO —— 对外暴露的商品数据结构
 *
 * <p>供其他微服务通过 Feign 调用时使用，包含商品基本信息和状态
 *
 * @author monkey
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO implements Serializable {

    /** 商品ID */
    private Long id;

    /** 商品标题 */
    private String title;

    /** 商品描述 */
    private String description;

    /** 商品主图URL */
    private String image;

    /** 卖家用户ID */
    private Long sellerId;

    /**
     * 叶子类目id
     */
    private Integer categoryLeafId;
    /**
     * 主类目id
     */
    private Integer categoryMainId;
    /**
     * 类目路径
     */
    private String categoryPath; // json格式[1,5,12]

    /** 状态：1=在售, 0=下架 */
    private Integer status;

    /** 商品SKU列表 */
    private List<SkuDTO> skus;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
