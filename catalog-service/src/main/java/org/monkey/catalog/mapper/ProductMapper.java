package org.monkey.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.monkey.catalog.entity.ProductEntity;

/**
 * 商品 Mapper —— MyBatis-Plus BaseMapper 提供基本 CRUD
 *
 * @author monkey
 */
@Mapper
public interface ProductMapper extends BaseMapper<ProductEntity> {
}
