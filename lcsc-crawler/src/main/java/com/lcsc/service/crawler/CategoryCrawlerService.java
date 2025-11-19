package com.lcsc.service.crawler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcsc.service.crawler.network.HttpClientService;
import com.lcsc.service.crawler.parser.CatalogParser;
import com.lcsc.service.crawler.LcscApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分类爬取服务
 * 专门用于爬取立创商城的一级和二级分类数据
 * 支持补丁式爬取和入库
 * 
 * @author lcsc-crawler
 * @since 2025-09-09
 */
@Service
public class CategoryCrawlerService {
    
    private static final Logger logger = LoggerFactory.getLogger(CategoryCrawlerService.class);
    
    @Autowired
    private HttpClientService httpClientService;
    
    @Autowired
    private CatalogParser catalogParser;
    
    @Autowired
    private CategoryPersistenceService categoryPersistenceService;
    
    @Autowired
    private LcscApiService lcscApiService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 爬取所有分类数据并入库
     * 
     * @return 爬取结果
     */
    public CompletableFuture<CategoryCrawlResult> crawlAllCategories() {
        logger.info("开始爬取所有分类数据");
        
        return CompletableFuture.supplyAsync(() -> {
            CategoryCrawlResult result = new CategoryCrawlResult();
            
            try {
                // 1. 调用分类API获取数据
                Map<String, Object> responseData = fetchCategoryData();
                if (responseData == null) {
                    result.setSuccess(false);
                    result.setErrorMessage("获取分类数据失败");
                    return result;
                }
                
                // 2. 直接获取分类列表（LcscApiService已经解析过了）
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> catalogMapping = (List<Map<String, Object>>) responseData.get("catalogList");
                if (catalogMapping == null || catalogMapping.isEmpty()) {
                    result.setSuccess(false);
                    result.setErrorMessage("解析分类数据为空");
                    return result;
                }
                
                logger.info("解析到 {} 个一级分类", catalogMapping.size());
                
                // 3. 处理每个一级分类及其子分类
                for (Map<String, Object> level1Category : catalogMapping) {
                    processSingleLevel1Category(level1Category, result);
                }
                
                result.setSuccess(true);
                result.setTotalLevel1Categories(catalogMapping.size());
                
                logger.info("分类爬取完成: {}", result);
                
            } catch (Exception e) {
                logger.error("爬取分类数据时出错", e);
                result.setSuccess(false);
                result.setErrorMessage("爬取失败: " + e.getMessage());
            }
            
            return result;
        });
    }
    
    /**
     * 爬取指定一级分类的数据
     * 
     * @param level1CategoryName 一级分类名称
     * @return 爬取结果
     */
    public CompletableFuture<CategoryCrawlResult> crawlLevel1Category(String level1CategoryName) {
        logger.info("开始爬取指定一级分类: {}", level1CategoryName);
        
        return CompletableFuture.supplyAsync(() -> {
            CategoryCrawlResult result = new CategoryCrawlResult();
            
            try {
                // 获取分类数据
                Map<String, Object> responseData = fetchCategoryData();
                if (responseData == null) {
                    result.setSuccess(false);
                    result.setErrorMessage("获取分类数据失败");
                    return result;
                }
                
                // 2. 直接获取分类列表（LcscApiService已经解析过了）
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> catalogMapping = (List<Map<String, Object>>) responseData.get("catalogList");
                if (catalogMapping == null || catalogMapping.isEmpty()) {
                    result.setSuccess(false);
                    result.setErrorMessage("解析分类数据为空");
                    return result;
                }
                
                logger.info("解析到 {} 个一级分类", catalogMapping.size());
                
                // 查找指定的一级分类
                Map<String, Object> targetCategory = findLevel1CategoryByName(catalogMapping, level1CategoryName);
                if (targetCategory == null) {
                    result.setSuccess(false);
                    result.setErrorMessage("未找到指定的一级分类: " + level1CategoryName);
                    return result;
                }
                
                // 处理该一级分类
                processSingleLevel1Category(targetCategory, result);
                
                result.setSuccess(true);
                result.setTotalLevel1Categories(1);
                
                logger.info("指定分类爬取完成: {}", result);
                
            } catch (Exception e) {
                logger.error("爬取指定分类数据时出错", e);
                result.setSuccess(false);
                result.setErrorMessage("爬取失败: " + e.getMessage());
            }
            
            return result;
        });
    }
    
    /**
     * 批量补丁式处理已有分类名称
     * 用于处理已经从产品数据中提取出的分类名称
     * 
     * @param categoryPairs 分类名称对列表
     * @return 处理结果
     */
    public CompletableFuture<CategoryCrawlResult> patchProcessCategories(List<CategoryPair> categoryPairs) {
        logger.info("开始批量补丁处理 {} 个分类组合", categoryPairs.size());
        
        return CompletableFuture.supplyAsync(() -> {
            CategoryCrawlResult result = new CategoryCrawlResult();
            
            Map<String, Integer> processedLevel1 = new ConcurrentHashMap<>();
            Map<String, Integer> processedLevel2 = new ConcurrentHashMap<>();
            
            for (CategoryPair pair : categoryPairs) {
                try {
                    String level1Name = pair.getLevel1Name();
                    String level2Name = pair.getLevel2Name();
                    
                    if (level1Name == null || level2Name == null) {
                        result.incrementSkipped();
                        continue;
                    }
                    
                    // 处理一级分类
                    Integer level1Id = processedLevel1.get(level1Name);
                    if (level1Id == null) {
                        level1Id = categoryPersistenceService.getOrCreateLevel1CategoryId(level1Name);
                        if (level1Id != null) {
                            processedLevel1.put(level1Name, level1Id);
                            result.incrementCreatedLevel1();
                        } else {
                            result.incrementFailed();
                            continue;
                        }
                    }
                    
                    // 处理二级分类
                    String level2Key = level1Name + "|" + level2Name;
                    Integer level2Id = processedLevel2.get(level2Key);
                    if (level2Id == null) {
                        level2Id = categoryPersistenceService.getOrCreateLevel2CategoryId(level2Name, level1Id);
                        if (level2Id != null) {
                            processedLevel2.put(level2Key, level2Id);
                            result.incrementCreatedLevel2();
                        } else {
                            result.incrementFailed();
                            continue;
                        }
                    }
                    
                    result.incrementProcessed();
                    
                } catch (Exception e) {
                    logger.warn("处理分类组合失败: {}", pair, e);
                    result.incrementFailed();
                }
            }
            
            result.setSuccess(true);
            result.setTotalLevel1Categories(processedLevel1.size());
            result.setTotalLevel2Categories(processedLevel2.size());
            
            logger.info("批量补丁处理完成: {}", result);
            return result;
        });
    }
    
    /**
     * 获取分类统计信息
     * 
     * @return 统计信息
     */
    public CompletableFuture<Map<String, Object>> getCategoryStatistics() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> stats = new HashMap<>();
            
            try {
                // 获取缓存统计
                var cacheStats = categoryPersistenceService.getCacheStats();
                stats.put("level1CacheSize", cacheStats.getLevel1CacheSize());
                stats.put("level2CacheSize", cacheStats.getLevel2CacheSize());
                
                // 这里可以添加更多统计信息，比如数据库中的分类数量等
                stats.put("timestamp", System.currentTimeMillis());
                
            } catch (Exception e) {
                logger.error("获取分类统计信息失败", e);
                stats.put("error", e.getMessage());
            }
            
            return stats;
        });
    }
    
    /**
     * 调用API获取分类数据
     */
    private Map<String, Object> fetchCategoryData() {
        try {
            logger.info("调用分类API: POST /catalog/list");
            
            CompletableFuture<Map<String, Object>> future = lcscApiService.getCatalogList();
            Map<String, Object> response = future.get();
            
            if (response == null || response.isEmpty()) {
                logger.error("获取到空的分类数据响应");
                return null;
            }
            
            logger.info("成功获取分类数据，包含字段: {}", response.keySet());
            return response;
            
        } catch (Exception e) {
            logger.error("获取分类数据时出错", e);
            return null;
        }
    }
    
    /**
     * 处理单个一级分类及其子分类
     */
    @SuppressWarnings("unchecked")
    private void processSingleLevel1Category(Map<String, Object> level1Category, CategoryCrawlResult result) {
        try {
            // 调试：输出一级分类的实际结构
            logger.info("一级分类结构: {}", level1Category.keySet());
            logger.info("一级分类内容: {}", level1Category);
            
            // 获取一级分类名称（优先使用中文名，如果为null则使用英文名）
            String level1Name = (String) level1Category.get("catalogName");
            if (level1Name == null || level1Name.trim().isEmpty()) {
                level1Name = (String) level1Category.get("catalogNameEn");
                logger.info("使用英文分类名称: {}", level1Name);
            }
            
            // 获取catalogId（直接从API响应格式）
            String catalogId = String.valueOf(level1Category.get("catalogId"));
            
            if (level1Name == null || level1Name.trim().isEmpty()) {
                logger.warn("一级分类名称为空，跳过处理");
                result.incrementSkipped();
                return;
            }
            
            // 创建或获取一级分类ID，传递catalogId
            Integer level1Id = categoryPersistenceService.getOrCreateLevel1CategoryId(level1Name, catalogId);
            if (level1Id == null) {
                logger.error("创建一级分类失败: {} (catalogId: {})", level1Name, catalogId);
                result.incrementFailed();
                return;
            }
            
            result.incrementCreatedLevel1();
            logger.debug("处理一级分类: {} -> ID: {}", level1Name, level1Id);
            
            // 处理子分类（直接从API响应格式）
            List<Map<String, Object>> subCategories = (List<Map<String, Object>>) level1Category.get("childCatelogs");
            if (subCategories != null) {
                for (Map<String, Object> subCategory : subCategories) {
                    processSingleLevel2Category(subCategory, level1Id, level1Name, result);
                }
            }
            
        } catch (Exception e) {
            logger.error("处理一级分类时出错", e);
            result.incrementFailed();
        }
    }
    
    /**
     * 处理单个二级分类
     */
    private void processSingleLevel2Category(Map<String, Object> level2Category, Integer level1Id, String level1Name, CategoryCrawlResult result) {
        try {
            // 获取二级分类名称（优先使用中文名，如果为null则使用英文名）
            String level2Name = (String) level2Category.get("catalogName");
            if (level2Name == null || level2Name.trim().isEmpty()) {
                level2Name = (String) level2Category.get("catalogNameEn");
            }
            
            // 获取catalogId
            String catalogId = null;
            Object catalogIdObj = level2Category.get("catalogId");
            if (catalogIdObj != null) {
                catalogId = String.valueOf(catalogIdObj);
            }
            
            if (level2Name == null || level2Name.trim().isEmpty()) {
                logger.warn("二级分类名称为空，跳过处理");
                result.incrementSkipped();
                return;
            }
            
            // 创建或获取二级分类ID，传递catalogId
            Integer level2Id = categoryPersistenceService.getOrCreateLevel2CategoryId(level2Name, level1Id, catalogId);
            if (level2Id == null) {
                logger.error("创建二级分类失败: {} (L1:{}, catalogId: {})", level2Name, level1Name, catalogId);
                result.incrementFailed();
                return;
            }
            
            result.incrementCreatedLevel2();
            logger.debug("处理二级分类: {} -> ID: {} (L1: {})", level2Name, level2Id, level1Name);
            
        } catch (Exception e) {
            logger.error("处理二级分类时出错", e);
            result.incrementFailed();
        }
    }
    
    /**
     * 根据名称查找一级分类
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findLevel1CategoryByName(List<Map<String, Object>> catalogMapping, String categoryName) {
        for (Map<String, Object> category : catalogMapping) {
            Map<String, String> nameInfo = (Map<String, String>) category.get("name");
            String cnName = nameInfo.get("cn");
            String enName = nameInfo.get("en");
            
            if (categoryName.equals(cnName) || categoryName.equals(enName)) {
                return category;
            }
        }
        return null;
    }
    
    /**
     * 解析JSON响应
     */
    private Map<String, Object> parseJsonResponse(String jsonResponse) {
        try {
            TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
            return objectMapper.readValue(jsonResponse, typeRef);
        } catch (Exception e) {
            logger.error("解析JSON响应失败: {}", e.getMessage());
            logger.debug("响应内容: {}", jsonResponse);
            return new HashMap<>();
        }
    }
    
    /**
     * 分类组合类
     */
    public static class CategoryPair {
        private String level1Name;
        private String level2Name;
        
        public CategoryPair() {}
        
        public CategoryPair(String level1Name, String level2Name) {
            this.level1Name = level1Name;
            this.level2Name = level2Name;
        }
        
        public String getLevel1Name() { return level1Name; }
        public void setLevel1Name(String level1Name) { this.level1Name = level1Name; }
        
        public String getLevel2Name() { return level2Name; }
        public void setLevel2Name(String level2Name) { this.level2Name = level2Name; }
        
        @Override
        public String toString() {
            return String.format("CategoryPair{L1:'%s', L2:'%s'}", level1Name, level2Name);
        }
    }
    
    /**
     * 分类爬取结果类
     */
    public static class CategoryCrawlResult {
        private boolean success = false;
        private String errorMessage;
        private int totalLevel1Categories = 0;
        private int totalLevel2Categories = 0;
        private final AtomicInteger createdLevel1 = new AtomicInteger(0);
        private final AtomicInteger createdLevel2 = new AtomicInteger(0);
        private final AtomicInteger processed = new AtomicInteger(0);
        private final AtomicInteger failed = new AtomicInteger(0);
        private final AtomicInteger skipped = new AtomicInteger(0);
        private long startTime = System.currentTimeMillis();
        private long endTime;
        
        public void incrementCreatedLevel1() { createdLevel1.incrementAndGet(); }
        public void incrementCreatedLevel2() { createdLevel2.incrementAndGet(); }
        public void incrementProcessed() { processed.incrementAndGet(); }
        public void incrementFailed() { failed.incrementAndGet(); }
        public void incrementSkipped() { skipped.incrementAndGet(); }
        
        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { 
            this.success = success; 
            this.endTime = System.currentTimeMillis();
        }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public int getTotalLevel1Categories() { return totalLevel1Categories; }
        public void setTotalLevel1Categories(int totalLevel1Categories) { this.totalLevel1Categories = totalLevel1Categories; }
        
        public int getTotalLevel2Categories() { return totalLevel2Categories; }
        public void setTotalLevel2Categories(int totalLevel2Categories) { this.totalLevel2Categories = totalLevel2Categories; }
        
        public int getCreatedLevel1() { return createdLevel1.get(); }
        public int getCreatedLevel2() { return createdLevel2.get(); }
        public int getProcessed() { return processed.get(); }
        public int getFailed() { return failed.get(); }
        public int getSkipped() { return skipped.get(); }
        
        public long getDurationMs() { 
            return endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime;
        }
        
        @Override
        public String toString() {
            return String.format("CategoryCrawlResult{成功: %s, L1总数: %d, L2总数: %d, 创建L1: %d, 创建L2: %d, 处理: %d, 失败: %d, 跳过: %d, 耗时: %dms}",
                success, totalLevel1Categories, totalLevel2Categories,
                createdLevel1.get(), createdLevel2.get(), processed.get(), failed.get(), skipped.get(),
                getDurationMs());
        }
    }
}