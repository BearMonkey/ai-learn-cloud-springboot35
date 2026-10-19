-- ================================================================
-- 商城微服务数据库初始化脚本
-- 每个微服务拥有独立数据库，实现物理隔离
-- ================================================================

-- ================================================================
-- 1. iam_db —— 认证服务
-- ================================================================
CREATE DATABASE IF NOT EXISTS iam_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE iam_db;

-- 用户表
CREATE TABLE `t_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(50)  NOT NULL                COMMENT '用户名（登录账号）',
    `password`      VARCHAR(255) NOT NULL                COMMENT '密码（加密存储）',
    `nickname`      VARCHAR(50)  DEFAULT NULL            COMMENT '昵称',
    `real_name`     VARCHAR(50)  DEFAULT NULL            COMMENT '真实姓名',
    `email`         VARCHAR(100) DEFAULT NULL            COMMENT '邮箱',
    `phone`         VARCHAR(20)  DEFAULT NULL            COMMENT '手机号',
    `avatar`        VARCHAR(500) DEFAULT NULL            COMMENT '头像URL',
    `status`        SMALLINT     NOT NULL DEFAULT 1      COMMENT '状态：1=正常, 0=禁用',
    `create_by`     BIGINT       DEFAULT NULL            COMMENT '创建人',
    `update_by`     BIGINT       DEFAULT NULL            COMMENT '更新人',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 用户角色表（支持一个用户同时拥有多个角色：BUYER + SELLER）
CREATE TABLE `t_user_role` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT      NOT NULL                COMMENT '用户ID',
    `role`          VARCHAR(20) NOT NULL                COMMENT '角色：BUYER=买家, SELLER=卖家',
    `create_by`     BIGINT      DEFAULT NULL            COMMENT '创建人',
    `create_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role`),
    KEY `idx_role_user` (`role`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色表';

-- ================================================================
-- 2. catalog_db —— 产品目录服务
-- ================================================================
CREATE DATABASE IF NOT EXISTS catalog_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE catalog_db;

-- 商品表
CREATE TABLE `t_product` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`         VARCHAR(200) NOT NULL                COMMENT '商品标题',
    `description`   TEXT         DEFAULT NULL            COMMENT '商品描述',
    `image`         VARCHAR(500) DEFAULT NULL            COMMENT '商品主图URL',
    `seller_id`     BIGINT       NOT NULL                COMMENT '卖家用户ID',
    `status`        SMALLINT     NOT NULL DEFAULT 1      COMMENT '状态：1=在售, 0=下架',
    `create_by`     BIGINT       DEFAULT NULL            COMMENT '创建人',
    `update_by`     BIGINT       DEFAULT NULL            COMMENT '更新人',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_product_seller` (`seller_id`),
    KEY `idx_product_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 商品SKU表（不含库存字段，库存由 inventory-service 管理）
CREATE TABLE `t_product_sku` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_id`    BIGINT       NOT NULL                COMMENT '商品ID',
    `spec_name`     VARCHAR(100) NOT NULL                COMMENT '规格名称（如：红色-256G）',
    `spec_image`    VARCHAR(500) DEFAULT NULL            COMMENT '规格图片URL',
    `seller_id`     BIGINT       NOT NULL                COMMENT '卖家用户ID',
    `price`         DECIMAL(10,2) NOT NULL               COMMENT '价格',
    `status`        SMALLINT     NOT NULL DEFAULT 1      COMMENT '状态：1=在售, 0=停售',
    `create_by`     BIGINT       DEFAULT NULL            COMMENT '创建人',
    `update_by`     BIGINT       DEFAULT NULL            COMMENT '更新人',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_sku_product` (`product_id`),
    KEY `idx_sku_seller` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表';

-- ================================================================
-- 3. inventory_db —— 库存服务
-- ================================================================
CREATE DATABASE IF NOT EXISTS inventory_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE inventory_db;

-- 仓库表
CREATE TABLE `t_warehouse` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`          VARCHAR(50)  NOT NULL                COMMENT '仓库编码（唯一）',
    `name`          VARCHAR(100) NOT NULL                COMMENT '仓库名称',
    `seller_id`     BIGINT       NOT NULL                COMMENT '所属卖家用户ID',
    `address`       VARCHAR(255) DEFAULT NULL            COMMENT '仓库地址',
    `status`        SMALLINT     NOT NULL DEFAULT 1      COMMENT '状态：1=启用, 0=停用',
    `create_by`     BIGINT       DEFAULT NULL            COMMENT '创建人',
    `update_by`     BIGINT       DEFAULT NULL            COMMENT '更新人',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_warehouse_seller` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库表';

-- 库存表（核心业务不变性：available_stock - locked_stock >= 0，由应用层保证）
CREATE TABLE `t_inventory` (
    `id`                BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `warehouse_id`      BIGINT  NOT NULL                COMMENT '仓库ID',
    `sku_id`            BIGINT  NOT NULL                COMMENT 'SKU ID（来自catalog-service）',
    `seller_id`         BIGINT  NOT NULL                COMMENT '卖家用户ID',
    `total_stock`       INT     NOT NULL DEFAULT 0      COMMENT '总库存数量',
    `locked_stock`      INT     NOT NULL DEFAULT 0      COMMENT '已锁定数量（下单未发货）',
    `available_stock`   INT     NOT NULL DEFAULT 0      COMMENT '可用库存（total_stock - locked_stock）',
    `safety_stock`      INT     NOT NULL DEFAULT 0      COMMENT '安全库存阈值',
    `create_by`         BIGINT  DEFAULT NULL            COMMENT '创建人',
    `update_by`         BIGINT  DEFAULT NULL            COMMENT '更新人',
    `create_time`       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_warehouse_sku` (`warehouse_id`, `sku_id`),
    KEY `idx_inventory_sku` (`sku_id`),
    KEY `idx_inventory_seller` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表';

-- 库存锁定记录表（下单时锁定，发货后扣减，取消时释放）
CREATE TABLE `t_stock_lock` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `inventory_id`  BIGINT      NOT NULL                COMMENT '库存记录ID',
    `order_id`      BIGINT      NOT NULL                COMMENT '订单ID（来自order-service）',
    `sku_id`        BIGINT      NOT NULL                COMMENT 'SKU ID',
    `quantity`      INT         NOT NULL                COMMENT '锁定数量',
    `status`        SMALLINT    NOT NULL DEFAULT 1      COMMENT '状态：1=已锁定, 2=已释放, 3=已扣减',
    `lock_time`     DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '锁定时间',
    `release_time`  DATETIME    DEFAULT NULL            COMMENT '释放/扣减时间',
    `create_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_lock_inventory` (`inventory_id`),
    KEY `idx_lock_order` (`order_id`),
    KEY `idx_lock_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存锁定记录表';

-- ================================================================
-- 4. order_db —— 订单服务
-- ================================================================
CREATE DATABASE IF NOT EXISTS order_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE order_db;

-- 订单表
-- 状态机：PENDING_PAYMENT → PENDING_DELIVERY → IN_DELIVERY → COMPLETED
--                                               PENDING_PAYMENT → CANCELLED
--                                               PENDING_DELIVERY → CANCELLED
CREATE TABLE `t_order` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`      VARCHAR(32)    NOT NULL                COMMENT '订单编号（唯一）',
    `buyer_id`      BIGINT         NOT NULL                COMMENT '买家用户ID',
    `seller_id`     BIGINT         NOT NULL                COMMENT '卖家用户ID',
    `total_amount`  DECIMAL(10,2)  NOT NULL                COMMENT '订单总金额',
    `address`       VARCHAR(255)   DEFAULT NULL            COMMENT '收货地址',
    `status`        SMALLINT       NOT NULL DEFAULT 1      COMMENT '状态：1=待支付, 2=待发货, 3=配送中, 4=已完成, 5=已取消',
    `cancel_reason` VARCHAR(200)   DEFAULT NULL            COMMENT '取消原因',
    `pay_time`      DATETIME       DEFAULT NULL            COMMENT '支付时间',
    `deliver_time`  DATETIME       DEFAULT NULL            COMMENT '发货时间',
    `complete_time` DATETIME       DEFAULT NULL            COMMENT '完成时间',
    `cancel_time`   DATETIME       DEFAULT NULL            COMMENT '取消时间',
    `create_by`     BIGINT         DEFAULT NULL            COMMENT '创建人',
    `update_by`     BIGINT         DEFAULT NULL            COMMENT '更新人',
    `create_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_order_buyer` (`buyer_id`),
    KEY `idx_order_seller` (`seller_id`),
    KEY `idx_order_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 订单明细表
CREATE TABLE `t_order_item` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`      BIGINT         NOT NULL                COMMENT '订单ID',
    `buyer_id`      BIGINT         NOT NULL                COMMENT '买家用户ID',
    `seller_id`     BIGINT         NOT NULL                COMMENT '卖家用户ID',
    `product_id`    BIGINT         NOT NULL                COMMENT '商品ID（来自catalog-service）',
    `sku_id`        BIGINT         NOT NULL                COMMENT 'SKU ID（来自catalog-service）',
    `spec_name`     VARCHAR(100)   DEFAULT NULL            COMMENT '下单时的规格名称快照',
    `quantity`      INT            NOT NULL                COMMENT '购买数量',
    `price`         DECIMAL(10,2)  NOT NULL                COMMENT '下单时的单价',
    `create_by`     BIGINT         DEFAULT NULL            COMMENT '创建人',
    `update_by`     BIGINT         DEFAULT NULL            COMMENT '更新人',
    `create_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_item_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';
