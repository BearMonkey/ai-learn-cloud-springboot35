package org.monkey.inventory.model;

/**
 * 仓库状态枚举
 *
 * @author monkey
 */
public enum WarehouseStatus {

    /** 启用 */
    ENABLED(1),

    /** 停用 */
    DISABLED(0);

    private final int value;

    WarehouseStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static WarehouseStatus fromValue(int value) {
        for (WarehouseStatus s : values()) {
            if (s.value == value) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知的仓库状态值: " + value);
    }
}
