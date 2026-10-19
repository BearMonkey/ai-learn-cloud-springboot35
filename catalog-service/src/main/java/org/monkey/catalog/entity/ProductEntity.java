package org.monkey.catalog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品实体 —— 映射 t_product 表
 *
 * @author monkey
 */
@TableName("t_product")
@Data
public class ProductEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private String image;

    @TableField("seller_id")
    private Long sellerId;

    @TableField("category_leaf_id")
    private Integer categoryLeafId;

    @TableField("category_path")
    private String categoryPath;

    @TableField("category_main_id")
    private Integer categoryMainId;

    private Integer status;

    @TableField("create_by")
    private Long createBy;

    @TableField("update_by")
    private Long updateBy;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
