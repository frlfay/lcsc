package com.lcsc.controller;

import com.lcsc.common.Result;
import com.lcsc.service.crawler.CategoryPersistenceService;
import com.lcsc.service.crawler.CategoryPersistenceService.CategoryIds;
import com.lcsc.service.crawler.CategoryPersistenceService.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 分类持久化管理控制器
 * 提供分类批量处理和管理接口
 * 
 * @author lcsc-crawler
 * @since 2025-09-09
 */
@RestController
@RequestMapping("/api/category-persistence")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
public class CategoryPersistenceController {
    
    private static final Logger logger = LoggerFactory.getLogger(CategoryPersistenceController.class);
    
    @Autowired
    private CategoryPersistenceService categoryPersistenceService;
    
    /**
     * 批量处理分类（单个分类组合）
     * 用于手动添加或测试分类持久化
     */
    @PostMapping("/process")
    public Result<CategoryIds> processCategory(
            @RequestParam String categoryLevel1Name,
            @RequestParam String categoryLevel2Name) {
        try {
            CategoryIds result = categoryPersistenceService.processCategories(
                categoryLevel1Name, categoryLevel2Name);
            
            if (result != null && result.isValid()) {
                return Result.success("分类处理成功", result);
            } else {
                return Result.error("分类处理失败");
            }
        } catch (Exception e) {
            logger.error("处理分类失败: L1={}, L2={}", categoryLevel1Name, categoryLevel2Name, e);
            return Result.error("分类处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量处理多个分类组合
     * 接收分类组合列表，返回处理结果
     */
    @PostMapping("/batch-process")
    public Result<BatchProcessResult> processCategoriesBatch(@RequestBody List<CategoryPair> categoryPairs) {
        try {
            BatchProcessResult result = new BatchProcessResult();
            
            for (CategoryPair pair : categoryPairs) {
                try {
                    CategoryIds categoryIds = categoryPersistenceService.processCategories(
                        pair.getCategoryLevel1Name(), pair.getCategoryLevel2Name());
                    
                    if (categoryIds != null && categoryIds.isValid()) {
                        result.addSuccess(pair, categoryIds);
                    } else {
                        result.addFailure(pair, "分类处理失败");
                    }
                } catch (Exception e) {
                    logger.warn("处理分类组合失败: {}", pair, e);
                    result.addFailure(pair, e.getMessage());
                }
            }
            
            String message = String.format("批量处理完成，成功: %d, 失败: %d", 
                    result.getSuccessCount(), result.getFailureCount());
            return Result.success(message, result);
            
        } catch (Exception e) {
            logger.error("批量处理分类失败", e);
            return Result.error("批量处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取或创建一级分类ID
     */
    @PostMapping("/level1")
    public Result<Integer> getOrCreateLevel1Category(@RequestParam String categoryName) {
        try {
            Integer categoryId = categoryPersistenceService.getOrCreateLevel1CategoryId(categoryName);
            if (categoryId != null) {
                return Result.success("一级分类处理成功", categoryId);
            } else {
                return Result.error("一级分类处理失败");
            }
        } catch (Exception e) {
            logger.error("处理一级分类失败: {}", categoryName, e);
            return Result.error("处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取或创建二级分类ID
     */
    @PostMapping("/level2")
    public Result<Integer> getOrCreateLevel2Category(
            @RequestParam String categoryName,
            @RequestParam Integer categoryLevel1Id) {
        try {
            Integer categoryId = categoryPersistenceService.getOrCreateLevel2CategoryId(
                categoryName, categoryLevel1Id);
            if (categoryId != null) {
                return Result.success("二级分类处理成功", categoryId);
            } else {
                return Result.error("二级分类处理失败");
            }
        } catch (Exception e) {
            logger.error("处理二级分类失败: {} (L1:{})", categoryName, categoryLevel1Id, e);
            return Result.error("处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    @GetMapping("/cache-stats")
    public Result<CacheStats> getCacheStats() {
        try {
            CacheStats stats = categoryPersistenceService.getCacheStats();
            return Result.success("获取缓存统计成功", stats);
        } catch (Exception e) {
            logger.error("获取缓存统计失败", e);
            return Result.error("获取统计失败: " + e.getMessage());
        }
    }
    
    /**
     * 清空分类缓存
     */
    @PostMapping("/clear-cache")
    public Result<String> clearCache() {
        try {
            categoryPersistenceService.clearCache();
            return Result.success("缓存清空成功");
        } catch (Exception e) {
            logger.error("清空缓存失败", e);
            return Result.error("清空缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 分类组合请求类
     */
    public static class CategoryPair {
        private String categoryLevel1Name;
        private String categoryLevel2Name;
        
        public CategoryPair() {}
        
        public CategoryPair(String categoryLevel1Name, String categoryLevel2Name) {
            this.categoryLevel1Name = categoryLevel1Name;
            this.categoryLevel2Name = categoryLevel2Name;
        }
        
        public String getCategoryLevel1Name() {
            return categoryLevel1Name;
        }
        
        public void setCategoryLevel1Name(String categoryLevel1Name) {
            this.categoryLevel1Name = categoryLevel1Name;
        }
        
        public String getCategoryLevel2Name() {
            return categoryLevel2Name;
        }
        
        public void setCategoryLevel2Name(String categoryLevel2Name) {
            this.categoryLevel2Name = categoryLevel2Name;
        }
        
        @Override
        public String toString() {
            return String.format("CategoryPair{L1:'%s', L2:'%s'}", categoryLevel1Name, categoryLevel2Name);
        }
    }
    
    /**
     * 批量处理结果类
     */
    public static class BatchProcessResult {
        private int successCount = 0;
        private int failureCount = 0;
        private java.util.List<SuccessItem> successList = new java.util.ArrayList<>();
        private java.util.List<FailureItem> failureList = new java.util.ArrayList<>();
        
        public void addSuccess(CategoryPair pair, CategoryIds categoryIds) {
            successCount++;
            successList.add(new SuccessItem(pair, categoryIds));
        }
        
        public void addFailure(CategoryPair pair, String reason) {
            failureCount++;
            failureList.add(new FailureItem(pair, reason));
        }
        
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public List<SuccessItem> getSuccessList() { return successList; }
        public List<FailureItem> getFailureList() { return failureList; }
        
        public static class SuccessItem {
            private CategoryPair categoryPair;
            private CategoryIds categoryIds;
            
            public SuccessItem(CategoryPair categoryPair, CategoryIds categoryIds) {
                this.categoryPair = categoryPair;
                this.categoryIds = categoryIds;
            }
            
            public CategoryPair getCategoryPair() { return categoryPair; }
            public CategoryIds getCategoryIds() { return categoryIds; }
        }
        
        public static class FailureItem {
            private CategoryPair categoryPair;
            private String reason;
            
            public FailureItem(CategoryPair categoryPair, String reason) {
                this.categoryPair = categoryPair;
                this.reason = reason;
            }
            
            public CategoryPair getCategoryPair() { return categoryPair; }
            public String getReason() { return reason; }
        }
    }
}