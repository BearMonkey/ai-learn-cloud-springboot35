package org.monkey.inventory.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 库存操作结果
 *
 * @author monkey
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockOperationResult implements Serializable {

    /** 是否成功 */
    private Boolean success;

    /** 结果消息 */
    private String message;

    /** 成功的快捷工厂 */
    public static StockOperationResult ok() {
        return StockOperationResult.builder().success(true).message("操作成功").build();
    }

    /** 失败的快捷工厂 */
    public static StockOperationResult fail(String message) {
        return StockOperationResult.builder().success(false).message(message).build();
    }
}
