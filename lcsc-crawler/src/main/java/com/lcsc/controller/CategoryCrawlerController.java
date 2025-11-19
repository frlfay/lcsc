package com.lcsc.controller;

import com.lcsc.common.Result;
import com.lcsc.service.crawler.CategoryCrawlerService;
import com.lcsc.service.crawler.CategoryCrawlerService.CategoryCrawlResult;
import com.lcsc.service.crawler.CategoryCrawlerService.CategoryPair;
import com.lcsc.service.crawler.CategoryExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 分类爬取控制器
 * 提供分类爬取和补丁处理的API接口
 * 
 * @author lcsc-crawler
 * @since 2025-09-09
 */
@RestController
@RequestMapping("/api/category-crawler")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
public class CategoryCrawlerController {
    
    private static final Logger logger = LoggerFactory.getLogger(CategoryCrawlerController.class);
    
    @Autowired
    private CategoryCrawlerService categoryCrawlerService;
    
    @Autowired
    private CategoryExtractorService categoryExtractorService;
    
    /**
     * 爬取所有分类数据并入库
     * 这是主要的补丁接口，用于爬取所有一级和二级分类
     */
    @PostMapping("/crawl-all")
    public CompletableFuture<Result<CategoryCrawlResult>> crawlAllCategories() {
        logger.info("收到爬取所有分类的请求");
        
        return categoryCrawlerService.crawlAllCategories()
            .thenApply(result -> {
                if (result.isSuccess()) {
                    return Result.<CategoryCrawlResult>success("分类爬取成功", result);
                } else {
                    return Result.<CategoryCrawlResult>error("分类爬取失败: " + result.getErrorMessage());
                }
            })
            .exceptionally(throwable -> {
                logger.error("爬取所有分类时出现异常", throwable);
                return Result.<CategoryCrawlResult>error("爬取失败: " + throwable.getMessage());
            });
    }
    
    /**
     * 爬取指定一级分类的数据
     */
    @PostMapping("/crawl-level1")
    public CompletableFuture<Result<CategoryCrawlResult>> crawlLevel1Category(
            @RequestParam String categoryName) {
        logger.info("收到爬取指定一级分类的请求: {}", categoryName);
        
        return categoryCrawlerService.crawlLevel1Category(categoryName)
            .thenApply(result -> {
                if (result.isSuccess()) {
                    return Result.<CategoryCrawlResult>success("指定分类爬取成功", result);
                } else {
                    return Result.<CategoryCrawlResult>error("指定分类爬取失败: " + result.getErrorMessage());
                }
            })
            .exceptionally(throwable -> {
                logger.error("爬取指定分类时出现异常", throwable);
                return Result.<CategoryCrawlResult>error("爬取失败: " + throwable.getMessage());
            });
    }
    
    /**
     * 批量补丁处理分类名称
     * 用于处理已经从产品数据中提取出的分类名称列表
     */
    @PostMapping("/patch-process")
    public CompletableFuture<Result<CategoryCrawlResult>> patchProcessCategories(
            @RequestBody List<CategoryPair> categoryPairs) {
        logger.info("收到批量补丁处理请求，包含 {} 个分类组合", categoryPairs.size());
        
        return categoryCrawlerService.patchProcessCategories(categoryPairs)
            .thenApply(result -> {
                if (result.isSuccess()) {
                    return Result.<CategoryCrawlResult>success("批量补丁处理成功", result);
                } else {
                    return Result.<CategoryCrawlResult>error("批量补丁处理失败: " + result.getErrorMessage());
                }
            })
            .exceptionally(throwable -> {
                logger.error("批量补丁处理时出现异常", throwable);
                return Result.<CategoryCrawlResult>error("处理失败: " + throwable.getMessage());
            });
    }
    
    /**
     * 从现有产品数据中提取分类并补丁处理
     * 这个接口会从数据库中读取产品数据，提取分类名称，然后进行补丁处理
     */
    @PostMapping("/patch-from-products")
    public CompletableFuture<Result<CategoryCrawlResult>> patchFromProducts(
            @RequestParam(defaultValue = "1000") int batchSize,
            @RequestParam(defaultValue = "0") int offset) {
        
        logger.info("收到从产品数据补丁分类的请求，批次大小: {}, 偏移: {}", batchSize, offset);
        
        return categoryExtractorService.extractCategoriesFromProducts(batchSize, offset)
            .thenCompose(categoryPairs -> {
                if (categoryPairs.isEmpty()) {
                    logger.info("没有从产品数据中提取到分类信息");
                    CategoryCrawlResult result = new CategoryCrawlResult();
                    result.setSuccess(true);
                    result.setErrorMessage("没有找到可提取的分类信息");
                    return CompletableFuture.completedFuture(Result.success("没有可处理的分类", result));
                }
                
                logger.info("从产品数据中提取到 {} 个分类组合，开始补丁处理", categoryPairs.size());
                return categoryCrawlerService.patchProcessCategories(categoryPairs)
                    .thenApply(crawlResult -> {
                        if (crawlResult.isSuccess()) {
                            return Result.<CategoryCrawlResult>success("从产品数据补丁分类成功", crawlResult);
                        } else {
                            return Result.<CategoryCrawlResult>error("从产品数据补丁分类失败: " + crawlResult.getErrorMessage());
                        }
                    })
                    .exceptionally(ex -> {
                        logger.error("从产品数据补丁分类时出现异常", ex);
                        return Result.<CategoryCrawlResult>error("处理失败: " + ex.getMessage());
                    });
            });
    }
    
    /**
     * 提取所有产品的分类并批量处理
     * 这是一个完整的补丁接口，会处理所有产品数据
     */
    @PostMapping("/patch-all-from-products")
    public CompletableFuture<Result<CategoryCrawlResult>> patchAllFromProducts() {
        logger.info("收到提取所有产品分类的补丁请求");
        
        return categoryExtractorService.extractAllCategories()
            .thenCompose(categoryPairs -> {
                if (categoryPairs.isEmpty()) {
                    logger.info("没有从产品数据中提取到任何分类信息");
                    CategoryCrawlResult result = new CategoryCrawlResult();
                    result.setSuccess(true);
                    result.setErrorMessage("没有找到可提取的分类信息");
                    return CompletableFuture.completedFuture(Result.success("没有可处理的分类", result));
                }
                
                logger.info("从所有产品数据中提取到 {} 个唯一分类组合，开始批量补丁处理", categoryPairs.size());
                return categoryCrawlerService.patchProcessCategories(categoryPairs)
                    .thenApply(crawlResult -> {
                        if (crawlResult.isSuccess()) {
                            return Result.<CategoryCrawlResult>success("批量补丁处理完成", crawlResult);
                        } else {
                            return Result.<CategoryCrawlResult>error("批量补丁处理失败: " + crawlResult.getErrorMessage());
                        }
                    })
                    .exceptionally(ex -> {
                        logger.error("批量补丁处理时出现异常", ex);
                        return Result.<CategoryCrawlResult>error("处理失败: " + ex.getMessage());
                    });
            });
    }
    
    /**
     * 获取分类统计信息
     */
    @GetMapping("/statistics")
    public CompletableFuture<Result<Map<String, Object>>> getCategoryStatistics() {
        logger.debug("收到获取分类统计信息的请求");
        
        return categoryCrawlerService.getCategoryStatistics()
            .thenApply(stats -> Result.success("获取统计信息成功", stats))
            .exceptionally(throwable -> {
                logger.error("获取分类统计信息时出现异常", throwable);
                return Result.<Map<String, Object>>error("获取统计信息失败: " + throwable.getMessage());
            });
    }
    
    /**
     * 手动添加单个分类组合
     * 用于手动指定要处理的分类名称
     */
    @PostMapping("/add-single")
    public CompletableFuture<Result<String>> addSingleCategory(
            @RequestParam String level1Name,
            @RequestParam String level2Name) {
        
        logger.info("收到手动添加分类的请求: L1={}, L2={}", level1Name, level2Name);
        
        CategoryPair pair = new CategoryPair(level1Name, level2Name);
        List<CategoryPair> pairs = List.of(pair);
        
        return categoryCrawlerService.patchProcessCategories(pairs)
            .thenApply(result -> {
                if (result.isSuccess()) {
                    String message = String.format("L1创建: %d, L2创建: %d", 
                            result.getCreatedLevel1(), result.getCreatedLevel2());
                    return Result.<String>success("分类添加成功", message);
                } else {
                    return Result.<String>error("分类添加失败: " + result.getErrorMessage());
                }
            })
            .exceptionally(throwable -> {
                logger.error("手动添加分类时出现异常", throwable);
                return Result.<String>error("添加失败: " + throwable.getMessage());
            });
    }
    
    /**
     * 测试分类爬取功能
     * 仅获取分类数据但不入库，用于调试
     */
    @GetMapping("/test-fetch")
    public CompletableFuture<Result<Map<String, Object>>> testFetchCategories() {
        logger.info("收到测试分类获取的请求");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // TODO: 实现测试获取功能
                // 调用分类API但不进行入库操作
                logger.warn("测试获取分类功能还未实现");
                
                return Result.error("测试功能还在开发中");
                
            } catch (Exception e) {
                logger.error("测试获取分类时出现异常", e);
                return Result.error("测试失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 清理和重置分类缓存
     */
    @PostMapping("/reset-cache")
    public Result<String> resetCache() {
        logger.info("收到重置分类缓存的请求");
        
        try {
            // 通过CategoryPersistenceService清理缓存
            // 这个功能应该已经在CategoryPersistenceService中实现了
            return Result.success("缓存重置功能请使用 /api/category-persistence/clear-cache 接口");
            
        } catch (Exception e) {
            logger.error("重置缓存时出现异常", e);
            return Result.error("重置失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取爬虫状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getCrawlerStatus() {
        try {
            Map<String, Object> status = Map.of(
                "service", "CategoryCrawlerService",
                "version", "1.0.0",
                "timestamp", System.currentTimeMillis(),
                "availableEndpoints", List.of(
                    "POST /crawl-all - 爬取所有分类",
                    "POST /crawl-level1 - 爬取指定一级分类", 
                    "POST /patch-process - 批量补丁处理",
                    "POST /add-single - 手动添加单个分类",
                    "GET /statistics - 获取统计信息"
                )
            );
            
            return Result.success("获取状态成功", status);
            
        } catch (Exception e) {
            logger.error("获取爬虫状态时出现异常", e);
            return Result.error("获取状态失败: " + e.getMessage());
        }
    }
}