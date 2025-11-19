package com.lcsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lcsc.entity.CategoryLevel1Code;
import com.lcsc.mapper.CategoryLevel1CodeMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 一级分类服务层
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Service
public class CategoryLevel1CodeService extends ServiceImpl<CategoryLevel1CodeMapper, CategoryLevel1Code> {

    /**
     * 分页查询一级分类
     */
    public IPage<CategoryLevel1Code> getCategoryLevel1Page(int current, int size, String categoryName, String categoryCode) {
        Page<CategoryLevel1Code> page = new Page<>(current, size);
        LambdaQueryWrapper<CategoryLevel1Code> wrapper = new LambdaQueryWrapper<>();
        
        if (categoryName != null && !categoryName.trim().isEmpty()) {
            wrapper.like(CategoryLevel1Code::getCategoryLevel1Name, categoryName);
        }
        if (categoryCode != null && !categoryCode.trim().isEmpty()) {
            wrapper.like(CategoryLevel1Code::getCategoryCode, categoryCode);
        }
        
        wrapper.orderByAsc(CategoryLevel1Code::getCategoryLevel1Name);
        
        return page(page, wrapper);
    }

    /**
     * 获取所有一级分类列表
     */
    public List<CategoryLevel1Code> getAllCategoryLevel1List() {
        LambdaQueryWrapper<CategoryLevel1Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(CategoryLevel1Code::getCategoryLevel1Name);
        return list(wrapper);
    }

    /**
     * 根据分类名称查询一级分类
     */
    public CategoryLevel1Code getByCategoryName(String categoryName) {
        LambdaQueryWrapper<CategoryLevel1Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel1Code::getCategoryLevel1Name, categoryName);
        return getOne(wrapper);
    }

    /**
     * 根据分类名称查询一级分类（别名方法，供CategoryPersistenceService使用）
     */
    public CategoryLevel1Code getByName(String categoryName) {
        return getByCategoryName(categoryName);
    }

    /**
     * 根据分类码查询一级分类
     */
    public CategoryLevel1Code getByCategoryCode(String categoryCode) {
        LambdaQueryWrapper<CategoryLevel1Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel1Code::getCategoryCode, categoryCode);
        return getOne(wrapper);
    }

    /**
     * 检查分类名称是否存在
     */
    public boolean existsByCategoryName(String categoryName) {
        LambdaQueryWrapper<CategoryLevel1Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel1Code::getCategoryLevel1Name, categoryName);
        return count(wrapper) > 0;
    }

    /**
     * 检查分类名称是否存在（排除指定ID）
     */
    public boolean existsByCategoryName(String categoryName, Integer excludeId) {
        LambdaQueryWrapper<CategoryLevel1Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel1Code::getCategoryLevel1Name, categoryName);
        if (excludeId != null) {
            wrapper.ne(CategoryLevel1Code::getId, excludeId);
        }
        return count(wrapper) > 0;
    }

    /**
     * 保存或更新一级分类
     */
    public boolean saveOrUpdateCategory(CategoryLevel1Code category) {
        if (category.getId() != null) {
            // 更新时检查名称是否重复
            if (existsByCategoryName(category.getCategoryLevel1Name(), category.getId())) {
                throw new RuntimeException("分类名称已存在");
            }
        } else {
            // 新增时检查名称是否重复
            if (existsByCategoryName(category.getCategoryLevel1Name())) {
                throw new RuntimeException("分类名称已存在");
            }
        }
        return saveOrUpdate(category);
    }

    /**
     * 删除一级分类
     */
    public boolean deleteCategoryById(Integer id) {
        // TODO: 检查是否有关联的二级分类，如果有则不允许删除
        return removeById(id);
    }

    /**
     * 批量删除一级分类
     */
    public boolean deleteCategoryBatch(List<Integer> ids) {
        // TODO: 检查是否有关联的二级分类，如果有则不允许删除
        return removeByIds(ids);
    }
}