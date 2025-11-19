package com.lcsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lcsc.entity.CategoryLevel2Code;
import com.lcsc.mapper.CategoryLevel2CodeMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 二级分类服务层
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Service
public class CategoryLevel2CodeService extends ServiceImpl<CategoryLevel2CodeMapper, CategoryLevel2Code> {

    /**
     * 分页查询二级分类
     */
    public IPage<CategoryLevel2Code> getCategoryLevel2Page(int current, int size, String categoryName, Integer categoryLevel1Id) {
        Page<CategoryLevel2Code> page = new Page<>(current, size);
        LambdaQueryWrapper<CategoryLevel2Code> wrapper = new LambdaQueryWrapper<>();
        
        if (categoryName != null && !categoryName.trim().isEmpty()) {
            wrapper.like(CategoryLevel2Code::getCategoryLevel2Name, categoryName);
        }
        if (categoryLevel1Id != null) {
            wrapper.eq(CategoryLevel2Code::getCategoryLevel1Id, categoryLevel1Id);
        }
        
        wrapper.orderByAsc(CategoryLevel2Code::getCategoryLevel1Id)
               .orderByAsc(CategoryLevel2Code::getCategoryLevel2Name);
        
        return page(page, wrapper);
    }

    /**
     * 根据一级分类ID查询二级分类列表
     */
    public List<CategoryLevel2Code> getCategoryLevel2ListByLevel1Id(Integer categoryLevel1Id) {
        LambdaQueryWrapper<CategoryLevel2Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel2Code::getCategoryLevel1Id, categoryLevel1Id);
        wrapper.orderByAsc(CategoryLevel2Code::getCategoryLevel2Name);
        return list(wrapper);
    }

    /**
     * 获取所有二级分类列表
     */
    public List<CategoryLevel2Code> getAllCategoryLevel2List() {
        LambdaQueryWrapper<CategoryLevel2Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(CategoryLevel2Code::getCategoryLevel1Id)
               .orderByAsc(CategoryLevel2Code::getCategoryLevel2Name);
        return list(wrapper);
    }

    /**
     * 根据分类名称和一级分类ID查询二级分类
     */
    public CategoryLevel2Code getByCategoryNameAndLevel1Id(String categoryName, Integer categoryLevel1Id) {
        LambdaQueryWrapper<CategoryLevel2Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel2Code::getCategoryLevel2Name, categoryName);
        wrapper.eq(CategoryLevel2Code::getCategoryLevel1Id, categoryLevel1Id);
        return getOne(wrapper);
    }

    /**
     * 根据分类名称和一级分类ID查询二级分类（别名方法，供CategoryPersistenceService使用）
     */
    public CategoryLevel2Code getByNameAndLevel1Id(String categoryName, Integer categoryLevel1Id) {
        return getByCategoryNameAndLevel1Id(categoryName, categoryLevel1Id);
    }

    /**
     * 检查分类名称在指定一级分类下是否存在
     */
    public boolean existsByCategoryNameAndLevel1Id(String categoryName, Integer categoryLevel1Id) {
        LambdaQueryWrapper<CategoryLevel2Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel2Code::getCategoryLevel2Name, categoryName);
        wrapper.eq(CategoryLevel2Code::getCategoryLevel1Id, categoryLevel1Id);
        return count(wrapper) > 0;
    }

    /**
     * 检查分类名称在指定一级分类下是否存在（排除指定ID）
     */
    public boolean existsByCategoryNameAndLevel1Id(String categoryName, Integer categoryLevel1Id, Integer excludeId) {
        LambdaQueryWrapper<CategoryLevel2Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel2Code::getCategoryLevel2Name, categoryName);
        wrapper.eq(CategoryLevel2Code::getCategoryLevel1Id, categoryLevel1Id);
        if (excludeId != null) {
            wrapper.ne(CategoryLevel2Code::getId, excludeId);
        }
        return count(wrapper) > 0;
    }

    /**
     * 保存或更新二级分类
     */
    public boolean saveOrUpdateCategory(CategoryLevel2Code category) {
        if (category.getId() != null) {
            // 更新时检查名称是否重复
            if (existsByCategoryNameAndLevel1Id(category.getCategoryLevel2Name(), 
                    category.getCategoryLevel1Id(), category.getId())) {
                throw new RuntimeException("该一级分类下已存在相同的二级分类名称");
            }
        } else {
            // 新增时检查名称是否重复
            if (existsByCategoryNameAndLevel1Id(category.getCategoryLevel2Name(), 
                    category.getCategoryLevel1Id())) {
                throw new RuntimeException("该一级分类下已存在相同的二级分类名称");
            }
        }
        return saveOrUpdate(category);
    }

    /**
     * 删除二级分类
     */
    public boolean deleteCategoryById(Integer id) {
        // TODO: 检查是否有关联的产品，如果有则不允许删除
        return removeById(id);
    }

    /**
     * 批量删除二级分类
     */
    public boolean deleteCategoryBatch(List<Integer> ids) {
        // TODO: 检查是否有关联的产品，如果有则不允许删除
        return removeByIds(ids);
    }

    /**
     * 获取指定二级分类的店铺分类码
     */
    public String getShopCategoryCode(Integer categoryLevel2Id, Integer shopId) {
        CategoryLevel2Code category = getById(categoryLevel2Id);
        if (category == null || category.getShopCategoryCodes() == null) {
            return null;
        }
        
        // TODO: 解析JSON格式的店铺分类码，根据shopId获取对应的分类码
        // 这里需要根据实际的JSON结构来实现
        return "default-category-code";
    }

    /**
     * 更新指定二级分类的店铺分类码
     */
    public boolean updateShopCategoryCode(Integer categoryLevel2Id, Integer shopId, String categoryCode) {
        CategoryLevel2Code category = getById(categoryLevel2Id);
        if (category == null) {
            return false;
        }
        
        // TODO: 更新JSON格式的店铺分类码
        // 这里需要根据实际的JSON结构来实现
        // 解析现有的shopCategoryCodes，更新指定shopId的分类码，然后保存
        
        return updateById(category);
    }

    /**
     * 根据一级分类ID统计二级分类数量
     */
    public long countByLevel1Id(Integer categoryLevel1Id) {
        LambdaQueryWrapper<CategoryLevel2Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel2Code::getCategoryLevel1Id, categoryLevel1Id);
        return count(wrapper);
    }

    /**
     * 批量保存或更新二级分类
     */
    public boolean saveOrUpdateBatch(List<CategoryLevel2Code> categories) {
        // 验证每个分类的唯一性
        for (CategoryLevel2Code category : categories) {
            if (category.getId() != null) {
                if (existsByCategoryNameAndLevel1Id(category.getCategoryLevel2Name(), 
                        category.getCategoryLevel1Id(), category.getId())) {
                    throw new RuntimeException("该一级分类下已存在相同的二级分类名称: " + category.getCategoryLevel2Name());
                }
            } else {
                if (existsByCategoryNameAndLevel1Id(category.getCategoryLevel2Name(), 
                        category.getCategoryLevel1Id())) {
                    throw new RuntimeException("该一级分类下已存在相同的二级分类名称: " + category.getCategoryLevel2Name());
                }
            }
        }
        return saveOrUpdateBatch(categories);
    }
}