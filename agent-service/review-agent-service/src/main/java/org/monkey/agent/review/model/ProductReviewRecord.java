package org.monkey.agent.review.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@TableName("t_product_review_record")
@AllArgsConstructor
@NoArgsConstructor
public class ProductReviewRecord {
    /*
    CREATE TABLE `t_product_review_record` (
        `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
        `event_id`        VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '审核事件ID（幂等键，唯一）',
        `product_id`      BIGINT       NOT NULL                COMMENT '商品ID',
        `seller_id`       BIGINT       NOT NULL                COMMENT '卖家ID',
        `trace_id`        VARCHAR(64)  DEFAULT NULL            COMMENT '链路追踪ID',
        `decision`        SMALLINT(4)  NOT NULL DEFAULT 0      COMMENT '裁决：1=APPROVED/2=REJECTED/3=ESCALATED',
        `decision_reason` TEXT         DEFAULT NULL            COMMENT '裁决理由（含各节点驳回/转人工原因）',
        `node_results`    JSON         DEFAULT NULL            COMMENT '四路节点结论（verdict/reason）',
        `llm_usage`       JSON         DEFAULT NULL            COMMENT 'LLM 用量（token 计量后续接入，当前置空）',
        `graph_cost_ms`   BIGINT       DEFAULT NULL            COMMENT '整图执行耗时',
        `callback_status` SMALLINT     NOT NULL DEFAULT 0      COMMENT '回调状态：0=待回调,1=成功,2=失败,3=放弃',
        `callback_retry_count` INT     NOT NULL DEFAULT 0      COMMENT '回调重试次数（补偿扫描用）',
        `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
        `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
        PRIMARY KEY (`id`),
        UNIQUE KEY `uk_event_id` (`event_id`),
        KEY `idx_product_id` (`product_id`),
        KEY `idx_seller_id` (`seller_id`),
        KEY `idx_seller_product` (`seller_id`,`product_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品审核审计记录';
    */
    // 按表结构补齐字段，
    // 补齐字段的doc注释
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 审核事件ID（幂等键，唯一）
     */
    private String eventId;
    /**
     * 商品ID
     */
    private Long productId;
    /**
     * 卖家ID
     */
    private Long sellerId;
    /**
     * 链路追踪ID
     */
    private String traceId;
    /**
     * 裁决：1=APPROVED/2=REJECTED/3=ESCALATED
     */
    private Integer decision;
    /**
     * 裁决理由（含各节点驳回/转人工原因）
     */
    private String decisionReason;
    /**
     * 四路节点结论（verdict/reason）
     */
    private String nodeResults;
    /**
     * LLM 用量（token 计量后续接入，当前置空）
     */
    private String llmUsage;
    /**
     * 整图执行耗时
     */
    private Long graphCostMs;
    /**
     * 回调状态：0=待回调,1=成功,2=失败,3=放弃
     */
    private Integer callbackStatus;
    /**
     * 回调重试次数（补偿扫描用）
     */
    private Integer callbackRetryCount;
    /**
     * 创建时间
     */
    private String createTime;
    /**
     * 更新时间
     */
    private String updateTime;
}
