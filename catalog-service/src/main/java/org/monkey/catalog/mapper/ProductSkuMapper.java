package org.monkey.catalog.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.monkey.catalog.entity.ProductSkuEntity;

import java.util.List;

/**
 * SKU Mapper —— MyBatis-Plus BaseMapper 提供基本 CRUD
 *
 * @author monkey
 */
@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSkuEntity> {
    default List<ProductSkuEntity> selectByProductId(Long id) {
        LambdaQueryWrapper<ProductSkuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductSkuEntity::getProductId, id);
        return this.selectList(wrapper);
    }
}
