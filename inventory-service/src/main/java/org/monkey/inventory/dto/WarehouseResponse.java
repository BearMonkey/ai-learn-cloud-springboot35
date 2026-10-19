package org.monkey.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.monkey.inventory.model.Warehouse;

import java.time.LocalDateTime;

/**
 * 仓库响应
 *
 * @author monkey
 */
@Data
@Builder
@AllArgsConstructor
public class WarehouseResponse {

    private Long id;
    private String code;
    private String name;
    private Long sellerId;
    private String address;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static WarehouseResponse from(Warehouse w) {
        return WarehouseResponse.builder()
                .id(w.getId())
                .code(w.getCode())
                .name(w.getName())
                .sellerId(w.getSellerId())
                .address(w.getAddress())
                .status(w.getStatus() != null ? w.getStatus().getValue() : null)
                .createTime(w.getCreateTime())
                .updateTime(w.getUpdateTime())
                .build();
    }
}
