package com.lcsc.service.crawler;

import com.lcsc.entity.CategoryLevel1Code;
import com.lcsc.entity.CategoryLevel2Code;
import com.lcsc.service.CategoryLevel1CodeService;
import com.lcsc.service.CategoryLevel2CodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 分类持久化服务
 * 负责处理一级和二级分类的智能去重和入库
 * 提供分类ID缓存，避免重复查询数据库
 * 
 * @author lcsc-crawler
 * @since 2025-09-09
 */
@Service
public class CategoryPersistenceService {
    
    private static final Logger logger = LoggerFactory.getLogger(CategoryPersistenceService.class);
    
    @Autowired
    private CategoryLevel1CodeService categoryLevel1Service;
    
    @Autowired
    private CategoryLevel2CodeService categoryLevel2Service;
    
    // 内存缓存，避免重复数据库查询
    private final Map<String, Integer> level1NameToIdCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> level2NameToIdCache = new ConcurrentHashMap<>();
    
    /**
     * 获取或创建一级分类ID
     * 如果分类不存在则创建新的分类记录
     * 
     * @param categoryLevel1Name 一级分类名称
     * @return 分类ID
     */
    @Transactional
    public Integer getOrCreateLevel1CategoryId(String categoryLevel1Name) {
        return getOrCreateLevel1CategoryId(categoryLevel1Name, null);
    }
    
    /**
     * 获取或创建一级分类ID（带catalogId）
     * 如果分类不存在则创建新的分类记录
     * 
     * @param categoryLevel1Name 一级分类名称
     * @param catalogId 立创API的catalogId
     * @return 分类ID
     */
    @Transactional
    public Integer getOrCreateLevel1CategoryId(String categoryLevel1Name, String catalogId) {
        if (categoryLevel1Name == null || categoryLevel1Name.trim().isEmpty()) {
            logger.warn("一级分类名称为空，无法处理");
            return null;
        }
        
        String cleanName = categoryLevel1Name.trim();
        
        // 首先检查缓存
        Integer cachedId = level1NameToIdCache.get(cleanName);
        if (cachedId != null) {
            return cachedId;
        }
        
        // 检查数据库是否存在
        CategoryLevel1Code existingCategory = categoryLevel1Service.getByName(cleanName);
        if (existingCategory != null) {
            // 更新缓存
            level1NameToIdCache.put(cleanName, existingCategory.getId());
            logger.debug("找到已存在的一级分类: {} -> ID: {}", cleanName, existingCategory.getId());
            return existingCategory.getId();
        }
        
        // 创建新的一级分类
        CategoryLevel1Code newCategory = new CategoryLevel1Code();
        newCategory.setCategoryLevel1Name(cleanName);
        newCategory.setCatalogId(catalogId);
        newCategory.setCreatedAt(LocalDateTime.now());
        newCategory.setUpdatedAt(LocalDateTime.now());
        
        boolean saved = categoryLevel1Service.save(newCategory);
        if (saved && newCategory.getId() != null) {
            // 更新缓存
            level1NameToIdCache.put(cleanName, newCategory.getId());
            logger.info("创建新的一级分类: {} -> ID: {}", cleanName, newCategory.getId());
            return newCategory.getId();
        } else {
            logger.error("创建一级分类失败: {}", cleanName);
            return null;
        }
    }
    
    /**
     * 获取或创建二级分类ID
     * 如果分类不存在则创建新的分类记录
     * 
     * @param categoryLevel2Name 二级分类名称
     * @param categoryLevel1Id 所属一级分类ID
     * @return 分类ID
     */
    @Transactional
    public Integer getOrCreateLevel2CategoryId(String categoryLevel2Name, Integer categoryLevel1Id) {
        return getOrCreateLevel2CategoryId(categoryLevel2Name, categoryLevel1Id, null);
    }
    
    /**
     * 获取或创建二级分类ID（带catalogId）
     * 如果分类不存在则创建新的分类记录
     * 
     * @param categoryLevel2Name 二级分类名称
     * @param categoryLevel1Id 所属一级分类ID
     * @param catalogId 立创API的catalogId
     * @return 分类ID
     */
    @Transactional
    public Integer getOrCreateLevel2CategoryId(String categoryLevel2Name, Integer categoryLevel1Id, String catalogId) {
        if (categoryLevel2Name == null || categoryLevel2Name.trim().isEmpty()) {
            logger.warn("二级分类名称为空，无法处理");
            return null;
        }
        
        if (categoryLevel1Id == null) {
            logger.warn("一级分类ID为空，无法创建二级分类: {}", categoryLevel2Name);
            return null;
        }
        
        String cleanName = categoryLevel2Name.trim();
        String cacheKey = cleanName + "|" + categoryLevel1Id; // 组合键：名称+一级分类ID
        
        // 首先检查缓存
        Integer cachedId = level2NameToIdCache.get(cacheKey);
        if (cachedId != null) {
            return cachedId;
        }
        
        // 检查数据库是否存在（同一一级分类下的同名二级分类）
        CategoryLevel2Code existingCategory = categoryLevel2Service.getByNameAndLevel1Id(cleanName, categoryLevel1Id);
        if (existingCategory != null) {
            // 更新缓存
            level2NameToIdCache.put(cacheKey, existingCategory.getId());
            logger.debug("找到已存在的二级分类: {} (L1:{}) -> ID: {}", cleanName, categoryLevel1Id, existingCategory.getId());
            return existingCategory.getId();
        }
        
        // 创建新的二级分类
        CategoryLevel2Code newCategory = new CategoryLevel2Code();
        newCategory.setCategoryLevel2Name(cleanName);
        newCategory.setCatalogId(catalogId);
        newCategory.setCategoryLevel1Id(categoryLevel1Id);
        newCategory.setCreatedAt(LocalDateTime.now());
        newCategory.setUpdatedAt(LocalDateTime.now());
        
        boolean saved = categoryLevel2Service.save(newCategory);
        if (saved && newCategory.getId() != null) {
            // 更新缓存
            level2NameToIdCache.put(cacheKey, newCategory.getId());
            logger.info("创建新的二级分类: {} (L1:{}) -> ID: {}", cleanName, categoryLevel1Id, newCategory.getId());
            return newCategory.getId();
        } else {
            logger.error("创建二级分类失败: {} (L1:{})", cleanName, categoryLevel1Id);
            return null;
        }
    }
    
    /**
     * 批量处理分类信息
     * 返回分类ID对，用于产品入库
     * 
     * @param categoryLevel1Name 一级分类名称
     * @param categoryLevel2Name 二级分类名称
     * @return CategoryIds对象，包含一级和二级分类ID
     */
    @Transactional
    public CategoryIds processCategories(String categoryLevel1Name, String categoryLevel2Name) {
        // 先处理一级分类
        Integer level1Id = getOrCreateLevel1CategoryId(categoryLevel1Name);
        if (level1Id == null) {
            logger.error("无法获取或创建一级分类: {}", categoryLevel1Name);
            return null;
        }
        
        // 再处理二级分类
        Integer level2Id = getOrCreateLevel2CategoryId(categoryLevel2Name, level1Id);
        if (level2Id == null) {
            logger.error("无法获取或创建二级分类: {} (L1:{})", categoryLevel2Name, level1Id);
            return new CategoryIds(level1Id, null); // 至少返回一级分类ID
        }
        
        return new CategoryIds(level1Id, level2Id);
    }
    
    /**
     * 清空分类缓存
     * 在需要刷新缓存时调用
     */
    public void clearCache() {
        level1NameToIdCache.clear();
        level2NameToIdCache.clear();
        logger.info("分类缓存已清空");
    }
    
    /**
     * 获取缓存统计信息
     * 
     * @return 缓存统计信息
     */
    public CacheStats getCacheStats() {
        return new CacheStats(
            level1NameToIdCache.size(),
            level2NameToIdCache.size()
        );
    }
    
    /**
     * 分类ID结果类
     */
    public static class CategoryIds {
        private final Integer categoryLevel1Id;
        private final Integer categoryLevel2Id;
        
        public CategoryIds(Integer categoryLevel1Id, Integer categoryLevel2Id) {
            this.categoryLevel1Id = categoryLevel1Id;
            this.categoryLevel2Id = categoryLevel2Id;
        }
        
        public Integer getCategoryLevel1Id() {
            return categoryLevel1Id;
        }
        
        public Integer getCategoryLevel2Id() {
            return categoryLevel2Id;
        }
        
        public boolean isValid() {
            return categoryLevel1Id != null && categoryLevel2Id != null;
        }
        
        @Override
        public String toString() {
            return String.format("CategoryIds{L1:%d, L2:%d}", categoryLevel1Id, categoryLevel2Id);
        }
    }
    
    /**
     * 缓存统计信息类
     */
    public static class CacheStats {
        private final int level1CacheSize;
        private final int level2CacheSize;
        
        public CacheStats(int level1CacheSize, int level2CacheSize) {
            this.level1CacheSize = level1CacheSize;
            this.level2CacheSize = level2CacheSize;
        }
        
        public int getLevel1CacheSize() {
            return level1CacheSize;
        }
        
        public int getLevel2CacheSize() {
            return level2CacheSize;
        }
        
        @Override
        public String toString() {
            return String.format("CacheStats{L1:%d, L2:%d}", level1CacheSize, level2CacheSize);
        }
    }
}