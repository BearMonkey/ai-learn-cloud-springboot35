package org.monkey.catalog.service;

import org.monkey.catalog.entity.Category;

public interface CategoryService {
    /**
     * 根据类目ID查询类目
     * @param categoryId 类目ID
     * @return Category
     */
    Category getByCategoryId(Integer categoryId);
}
