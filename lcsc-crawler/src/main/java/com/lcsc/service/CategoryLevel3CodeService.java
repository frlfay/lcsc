package com.lcsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lcsc.entity.CategoryLevel3Code;
import com.lcsc.mapper.CategoryLevel3CodeMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 三级分类服务层
 *
 * @author lcsc-crawler
 * @since 2025-11-20
 */
@Service
public class CategoryLevel3CodeService extends ServiceImpl<CategoryLevel3CodeMapper, CategoryLevel3Code> {

    /**
     * 根据二级分类ID查询三级分类列表
     */
    public List<CategoryLevel3Code> getByLevel2Id(Integer categoryLevel2Id) {
        LambdaQueryWrapper<CategoryLevel3Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel3Code::getCategoryLevel2Id, categoryLevel2Id);
        wrapper.orderByAsc(CategoryLevel3Code::getCategoryLevel3Name);
        return list(wrapper);
    }

    /**
     * 根据一级分类ID查询三级分类列表
     */
    public List<CategoryLevel3Code> getByLevel1Id(Integer categoryLevel1Id) {
        LambdaQueryWrapper<CategoryLevel3Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel3Code::getCategoryLevel1Id, categoryLevel1Id);
        wrapper.orderByAsc(CategoryLevel3Code::getCategoryLevel3Name);
        return list(wrapper);
    }

    /**
     * 获取所有三级分类列表
     */
    public List<CategoryLevel3Code> getAllCategoryLevel3List() {
        LambdaQueryWrapper<CategoryLevel3Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(CategoryLevel3Code::getCategoryLevel1Id)
               .orderByAsc(CategoryLevel3Code::getCategoryLevel2Id)
               .orderByAsc(CategoryLevel3Code::getCategoryLevel3Name);
        return list(wrapper);
    }

    /**
     * 根据catalogId查询三级分类
     */
    public CategoryLevel3Code getByCatalogId(String catalogId) {
        LambdaQueryWrapper<CategoryLevel3Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel3Code::getCatalogId, catalogId);
        return getOne(wrapper);
    }

    /**
     * 检查catalogId是否存在
     */
    public boolean existsByCatalogId(String catalogId) {
        LambdaQueryWrapper<CategoryLevel3Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel3Code::getCatalogId, catalogId);
        return count(wrapper) > 0;
    }

    /**
     * 根据分类名称和二级分类ID查询三级分类
     */
    public CategoryLevel3Code getByNameAndLevel2Id(String categoryName, Integer categoryLevel2Id) {
        LambdaQueryWrapper<CategoryLevel3Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel3Code::getCategoryLevel3Name, categoryName);
        wrapper.eq(CategoryLevel3Code::getCategoryLevel2Id, categoryLevel2Id);
        return getOne(wrapper);
    }

    /**
     * 检查分类名称在指定二级分类下是否存在
     */
    public boolean existsByNameAndLevel2Id(String categoryName, Integer categoryLevel2Id) {
        LambdaQueryWrapper<CategoryLevel3Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel3Code::getCategoryLevel3Name, categoryName);
        wrapper.eq(CategoryLevel3Code::getCategoryLevel2Id, categoryLevel2Id);
        return count(wrapper) > 0;
    }

    /**
     * 根据二级分类ID统计三级分类数量
     */
    public long countByLevel2Id(Integer categoryLevel2Id) {
        LambdaQueryWrapper<CategoryLevel3Code> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryLevel3Code::getCategoryLevel2Id, categoryLevel2Id);
        return count(wrapper);
    }

    /**
     * 删除三级分类
     */
    public boolean deleteCategoryById(Integer id) {
        // TODO: 检查是否有关联的产品，如果有则不允许删除
        return removeById(id);
    }

    /**
     * 批量删除三级分类
     */
    public boolean deleteCategoryBatch(List<Integer> ids) {
        // TODO: 检查是否有关联的产品，如果有则不允许删除
        return removeByIds(ids);
    }
}
