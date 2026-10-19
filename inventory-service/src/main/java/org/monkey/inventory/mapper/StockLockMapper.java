package org.monkey.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.monkey.inventory.entity.StockLockEntity;

/**
 * 库存锁定 Mapper
 *
 * @author monkey
 */
@Mapper
public interface StockLockMapper extends BaseMapper<StockLockEntity> {
}
