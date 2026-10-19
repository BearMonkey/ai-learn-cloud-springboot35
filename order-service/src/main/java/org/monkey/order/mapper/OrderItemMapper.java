package org.monkey.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.monkey.order.entity.OrderItemEntity;

/**
 * 订单明细 Mapper
 *
 * @author monkey
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemEntity> {
}
