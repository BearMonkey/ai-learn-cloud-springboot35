package org.monkey.catalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.monkey.catalog.entity.Category;
import org.monkey.catalog.mapper.CategoryMapper;
import org.monkey.catalog.service.CategoryService;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {
    @Override
    public Category getByCategoryId(Integer categoryId) {
        log.info("getByCategoryId: {}", categoryId);
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getCategoryId, categoryId);
        return this.getOne(queryWrapper);
    }
}
