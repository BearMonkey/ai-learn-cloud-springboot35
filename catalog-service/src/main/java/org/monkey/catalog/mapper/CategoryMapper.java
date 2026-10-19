package org.monkey.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.monkey.catalog.entity.Category;

/**
 * @author Monkey
 * @description Category mapper
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
