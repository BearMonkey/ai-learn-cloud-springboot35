package org.monkey.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.monkey.inventory.entity.WarehouseEntity;

/**
 * 仓库 Mapper
 *
 * @author monkey
 */
@Mapper
public interface WarehouseMapper extends BaseMapper<WarehouseEntity> {
}
