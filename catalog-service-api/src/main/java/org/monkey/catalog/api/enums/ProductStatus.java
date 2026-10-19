package org.monkey.catalog.api.enums;

/**
 * 商品状态枚举
 *
 * @author monkey
 */
public enum ProductStatus {

    /** 审核不通过 */
    REJECTED(9),

    /** 人工审核中 */
    HUMAN_REVIEW(3),

    /** 审核中 */
    ON_REVIEW(2),

    /** 在售 */
    ON_SALE(1),

    /** 下架 */
    OFF_SALE(0);

    private final int value;

    ProductStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * 根据数据库值反查枚举
     *
     * @param value 状态值
     * @return 对应的枚举
     * @throws IllegalArgumentException 无效的状态值
     */
    public static ProductStatus fromValue(int value) {
        for (ProductStatus s : values()) {
            if (s.value == value) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知的商品状态值: " + value);
    }
}
