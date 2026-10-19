package org.monkey.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Monkey
 * @description Category entity
 *CREATE TABLE `t_category` (
 *     `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
 *     `category_id` int(11) NOT NULL COMMENT '类目id',
 *     `category_name` varchar(50) NOT NULL COMMENT '类目名称',
 *     `leaf_flag` smallint(6) NOT NULL DEFAULT '0' COMMENT '是否叶子节点：1=是, 0=否',
 *     `parent_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '父级类目ID',
 *     `category_level` smallint(6) NOT NULL COMMENT '类目层级 1/2/3',
 *     `path` json DEFAULT NULL COMMENT '类目路径 如 [1,5,12]',
 *     `sort` int(11) DEFAULT '0' COMMENT '排序',
 *     `category_status` smallint(6) DEFAULT '1' COMMENT '类目状态：1=启用, 0=停用',
 *     `create_by` bigint(20) DEFAULT NULL COMMENT '创建人',
 *     `update_by` bigint(20) DEFAULT NULL COMMENT '更新人',
 *     `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 *     `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 *     PRIMARY KEY (`id`),
 *     KEY `idx_category_id` (`category_id`),
 *     KEY `idx_parent_id` (`parent_id`),
 *     KEY `idx_category_level` (`category_level`)
 *     ) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COMMENT='商品类目表';
 */
@Data
@TableName("t_category")
public class Category {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 类目id
     */
    private Integer categoryId;
    /**
     * 类目名称
     */
    private String categoryName;
    /**
     * 是否叶子节点：1=是, 0=否
     */
    private Integer leafFlag;
    /**
     * 父级类目ID
     */
    private Long parentId;
    /**
     * 类目层级 1/2/3
     */
    private Integer categoryLevel;
    /**
     * 类目路径 如 [1,5,12]
     */
    private String path;
    /**
     * 排序
     */
    private Integer sort;
    /**
     * 类目状态：1=启用, 0=停用
     */
    private Integer categoryStatus;
    /**
     * 创建人
     */
    private Long createBy;
    /**
     * 更新人
     */
    private Long updateBy;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
