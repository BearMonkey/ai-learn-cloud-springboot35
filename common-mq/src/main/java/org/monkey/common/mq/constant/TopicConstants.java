package org.monkey.common.mq.constant;

/**
 * RocketMQ Topic 常量
 *
 * @author monkey
 */
public final class TopicConstants {

    private TopicConstants() {
        // 工具类禁止实例化
    }

    /** 订单事件 Topic —— 下单/取消/发货，order-service 生产，inventory-service 消费 */
    public static final String ORDER_EVENT = "ORDER_EVENT";

    /** 商品事件 Topic —— 创建商品时初始化库存，catalog-service 生产，inventory-service 消费 */
    public static final String PRODUCT_EVENT = "PRODUCT_EVENT";

    /** 订单超时检查 Topic —— 延迟消息，order-service 自产自消，用于超时自动取消 */
    public static final String ORDER_TIMEOUT_CHECK = "ORDER_TIMEOUT_CHECK";
}
