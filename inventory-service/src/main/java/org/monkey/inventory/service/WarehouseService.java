package org.monkey.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.inventory.dto.WarehouseResponse;

/**
 * 仓库服务接口
 *
 * @author monkey
 */
public interface WarehouseService {

    /** 创建仓库 */
    WarehouseResponse createWarehouse(Long sellerId, String code, String name, String address);

    /** 更新仓库信息 */
    WarehouseResponse updateWarehouse(Long sellerId, Long warehouseId, String name, String address);

    /** 启用/停用仓库 */
    void updateStatus(Long sellerId, Long warehouseId, int status);

    /** 卖家仓库列表 */
    Page<WarehouseResponse> getMyWarehouses(Long sellerId, int page, int size);

    /** 仓库详情 */
    WarehouseResponse getWarehouseDetail(Long id);
}
