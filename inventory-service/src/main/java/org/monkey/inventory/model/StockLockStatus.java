package org.monkey.inventory.model;

/**
 * 库存锁定状态枚举
 *
 * @author monkey
 */
public enum StockLockStatus {

    /** 已锁定 */
    LOCKED(1),

    /** 已释放 */
    RELEASED(2),

    /** 已扣减 */
    DEDUCTED(3);

    private final int value;

    StockLockStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static StockLockStatus fromValue(int value) {
        for (StockLockStatus s : values()) {
            if (s.value == value) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知的锁定状态值: " + value);
    }
}
