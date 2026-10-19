package org.monkey.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.inventory.model.Warehouse;

import java.util.Optional;

/**
 * 仓库仓储接口
 *
 * @author monkey
 */
public interface WarehouseRepository {

    /** 根据ID查询 */
    Optional<Warehouse> findById(Long id);

    /** 根据编码查询 */
    Optional<Warehouse> findByCode(String code);

    /** 卖家分页查询仓库列表 */
    Page<Warehouse> findBySellerId(Long sellerId, Page<Warehouse> page);

    /** 保存（创建或更新） */
    Warehouse save(Warehouse warehouse);
}
