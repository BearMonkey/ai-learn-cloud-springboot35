package org.monkey.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.monkey.order.entity.OrderEntity;

/**
 * 订单 Mapper
 *
 * @author monkey
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {
}
