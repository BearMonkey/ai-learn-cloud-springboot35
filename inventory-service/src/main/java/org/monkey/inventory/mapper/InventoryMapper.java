package org.monkey.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.monkey.inventory.entity.InventoryEntity;

import java.util.List;
import java.util.Map;

/**
 * 库存 Mapper
 *
 * @author monkey
 */
@Mapper
public interface InventoryMapper extends BaseMapper<InventoryEntity> {

    /** 按 SKU 汇总库存（用于对账） */
    @Select("SELECT sku_id, SUM(total_stock) AS total_stock, SUM(locked_stock) AS locked_stock, " +
            "SUM(total_stock - locked_stock) AS available_stock " +
            "FROM t_inventory GROUP BY sku_id")
    List<Map<String, Object>> summaryBySku();
}
