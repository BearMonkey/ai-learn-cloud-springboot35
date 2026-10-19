package org.monkey.common.mq.constant;

/**
 * 事件类型常量
 *
 * @author monkey
 */
public final class EventType {

    private EventType() {
        // 工具类禁止实例化
    }

    // =========================================商品=============================================
    /** 商品审核 —— 异步审核 */
    public static final String PRODUCT_AUDIT = "PRODUCT_AUDIT";

    /** 商品已创建 —— 初始化库存 */
    public static final String PRODUCT_CREATED = "PRODUCT_CREATED";

    /** 商品已删除 —— 删除库存 */
    public static final String PRODUCT_DELETED = "PRODUCT_DELETED";

    // =========================================订单=============================================
    /** 订单已创建 —— 异步同步 DB 库存锁定 */
    public static final String ORDER_CREATED = "ORDER_CREATED";

    /** 订单已取消 —— 释放库存 */
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    /** 订单已发货 —— 扣减库存 */
    public static final String ORDER_DELIVERED = "ORDER_DELIVERED";

    /** 订单超时检查 */
    public static final String ORDER_TIMEOUT_CHECK = "ORDER_TIMEOUT_CHECK";
}
