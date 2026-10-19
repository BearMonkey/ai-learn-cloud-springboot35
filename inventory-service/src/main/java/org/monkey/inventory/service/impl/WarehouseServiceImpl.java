package org.monkey.inventory.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.inventory.dto.WarehouseResponse;
import org.monkey.inventory.model.BusinessException;
import org.monkey.inventory.model.Warehouse;
import org.monkey.inventory.model.WarehouseStatus;
import org.monkey.inventory.service.WarehouseRepository;
import org.monkey.inventory.service.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 仓库服务实现
 *
 * @author monkey
 */
@Service
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public WarehouseServiceImpl(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Override
    @Transactional
    public WarehouseResponse createWarehouse(Long sellerId, String code, String name, String address) {
        // 校验编码唯一
        if (warehouseRepository.findByCode(code).isPresent()) {
            throw new BusinessException("仓库编码已存在");
        }
        Warehouse warehouse = Warehouse.create(code, name, sellerId, address);
        warehouse = warehouseRepository.save(warehouse);
        return WarehouseResponse.from(warehouse);
    }

    @Override
    @Transactional
    public WarehouseResponse updateWarehouse(Long sellerId, Long warehouseId, String name, String address) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new BusinessException(404, "仓库不存在"));
        if (!warehouse.belongsTo(sellerId)) {
            throw new BusinessException(403, "无权操作此仓库");
        }
        warehouse.updateInfo(name, address);
        warehouse = warehouseRepository.save(warehouse);
        return WarehouseResponse.from(warehouse);
    }

    @Override
    @Transactional
    public void updateStatus(Long sellerId, Long warehouseId, int status) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new BusinessException(404, "仓库不存在"));
        if (!warehouse.belongsTo(sellerId)) {
            throw new BusinessException(403, "无权操作此仓库");
        }
        WarehouseStatus target = WarehouseStatus.fromValue(status);
        if (target == WarehouseStatus.ENABLED) {
            warehouse.enable();
        } else {
            warehouse.disable();
        }
        warehouseRepository.save(warehouse);
    }

    @Override
    public Page<WarehouseResponse> getMyWarehouses(Long sellerId, int page, int size) {
        Page<Warehouse> domainPage = new Page<>(page, size);
        domainPage = warehouseRepository.findBySellerId(sellerId, domainPage);
        List<WarehouseResponse> responses = domainPage.getRecords().stream()
                .map(WarehouseResponse::from)
                .collect(Collectors.toList());
        Page<WarehouseResponse> result = new Page<>(page, size, domainPage.getTotal());
        result.setRecords(responses);
        return result;
    }

    @Override
    public WarehouseResponse getWarehouseDetail(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "仓库不存在"));
        return WarehouseResponse.from(warehouse);
    }
}
