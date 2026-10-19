package org.monkey.order.api.enums;

/**
 * 订单状态枚举
 *
 * @author monkey
 */
public enum OrderStatus {

    /** 待支付 */
    PENDING_PAYMENT(1),

    /** 待发货 */
    PENDING_DELIVERY(2),

    /** 配送中 */
    IN_DELIVERY(3),

    /** 已完成 */
    COMPLETED(4),

    /** 已取消 */
    CANCELLED(5);

    private final int value;

    OrderStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static OrderStatus fromValue(int value) {
        for (OrderStatus s : values()) {
            if (s.value == value) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知的订单状态值: " + value);
    }
}
