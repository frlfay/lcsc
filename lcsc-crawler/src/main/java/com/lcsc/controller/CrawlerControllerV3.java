package com.lcsc.controller;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lcsc.common.Result;
import com.lcsc.config.CrawlerConfig;
import com.lcsc.entity.CategoryLevel2Code;
import com.lcsc.entity.Product;
import com.lcsc.service.crawler.v3.CategoryCrawlerWorkerPool;
import com.lcsc.service.crawler.v3.CategorySyncService;
import com.lcsc.service.crawler.v3.CrawlerTaskQueueService;

/**
 * 爬虫控制器V3
 * 提供全新的爬虫控制REST API
 *
 * @author lcsc-crawler
 * @since 2025-10-08
 */
@RestController
@RequestMapping("/api/v3/crawler")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
public class CrawlerControllerV3 {

    private static final Logger log = LoggerFactory.getLogger(CrawlerControllerV3.class);

    @Autowired
    private CategorySyncService syncService;

    @Autowired
    private CrawlerTaskQueueService queueService;

    @Autowired
    private CategoryCrawlerWorkerPool workerPool;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private com.lcsc.mapper.ProductMapper productMapper;

    @Autowired
    private CrawlerConfig crawlerConfig;

    @Value("${crawler.storage.base-path:#{systemProperties['user.dir']}/data}")
    private String storageBasePath;

    /**
     * 检查系统状态
     * 包括：分类是否已同步、爬虫是否运行中、队列状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> checkStatus() {
        try {
            log.debug("检查系统状态");

            boolean categoriesSynced = syncService.isCategoriesSynced();
            boolean isRunning = workerPool.isRunning();
            Map<String, Object> queueStatus = queueService.getQueueStatus();
            Map<String, Object> categoryStats = syncService.getCategoryStatistics();
            Long totalProductsInDb = productMapper.selectCount(null);

            Map<String, Object> status = Map.of(
                "categoriesSynced", categoriesSynced,
                "isRunning", isRunning,
                "queueStatus", queueStatus,
                "categoryStats", categoryStats,
                "workerThreadCount", workerPool.getWorkerThreadCount(),
                "totalProductsInDb", totalProductsInDb != null ? totalProductsInDb : 0
            );

            return Result.success(status);

        } catch (Exception e) {
            log.error("检查系统状态失败", e);
            return Result.error("检查系统状态失败: " + e.getMessage());
        }
    }

    /**
     * 第一步：爬取分类信息
     * 从API获取所有分类并同步到数据库（覆盖模式）
     */
    @PostMapping("/sync-categories")
    public CompletableFuture<? extends Result<?>> syncCategories() {
        log.info("========== 收到同步分类请求 ==========");

        return syncService.crawlAndSyncCategories()
            .thenApply(result -> {
                Boolean success = (Boolean) result.get("success");
                if (success) {
                    log.info("分类同步成功: 一级={}, 二级={}",
                        result.get("level1Count"), result.get("level2Count"));
                    return Result.success("分类同步成功", result);
                } else {
                    log.error("分类同步失败: {}", result.get("error"));
                    return Result.error((String) result.get("message"));
                }
            })
            .exceptionally(ex -> {
                log.error("分类同步异常", ex);
                return Result.error("分类同步异常: " + ex.getMessage());
            });
    }

    /**
     * 第二步：开始全量爬取
     * 为所有二级分类创建爬取任务并启动Worker池
     */
    @PostMapping("/start-full")
    public Result<Map<String, Object>> startFullCrawl() {
        try {
            log.info("========== 收到全量爬取请求 ==========");

            // 1. 检查分类是否已同步
            if (!syncService.isCategoriesSynced()) {
                return Result.error("请先同步分类信息");
            }

            // 2. 清理旧状态，确保是全新的开始
            log.info("开始全量爬取前，重置所有队列和统计数据...");
            queueService.clearAllQueues();
            queueService.initializeState();

            // 3. 获取所有二级分类
            List<CategoryLevel2Code> categories = syncService.getAllLevel2Categories();
            if (categories.isEmpty()) {
                return Result.error("没有可爬取的分类");
            }

            List<Integer> catalogIds = categories.stream()
                .map(CategoryLevel2Code::getId)
                .collect(Collectors.toList());

            log.info("准备创建任务: 分类总数={}", catalogIds.size());

            // 4. 批量创建任务（优先级=1，自动）
            List<String> taskIds = queueService.createBatchTasks(catalogIds,
                CrawlerTaskQueueService.PRIORITY_AUTO);

            log.info("任务创建完成: 成功创建={} 个", taskIds.size());

            // 5. 启动Worker池
            workerPool.start();

            Map<String, Object> result = Map.of(
                "success", true,
                "totalCategories", categories.size(),
                "createdTasks", taskIds.size(),
                "message", "全量爬取已启动"
            );

            log.info("========== 全量爬取启动完成 ==========");
            return Result.success(result);

        } catch (Exception e) {
            log.error("启动全量爬取失败", e);
            return Result.error("启动失败: " + e.getMessage());
        }
    }

    /**
     * 开始指定分类爬取（手动触发，高优先级）
     */
    @PostMapping("/start-category/{catalogId}")
    public Result<Map<String, Object>> startCategoryCrawl(@PathVariable Integer catalogId) {
        try {
            log.info("========== 收到单分类爬取请求: catalogId={} ==========", catalogId);

            // 创建任务（优先级=10，手动）
            String taskId = queueService.createCategoryTask(catalogId,
                CrawlerTaskQueueService.PRIORITY_MANUAL);

            log.info("高优先级任务已创建: taskId={}", taskId);

            // 如果Worker未运行，启动
            if (!workerPool.isRunning()) {
                log.info("Worker池未运行，正在启动...");
                workerPool.start();
            }

            Map<String, Object> result = Map.of(
                "success", true,
                "taskId", taskId,
                "catalogId", catalogId,
                "priority", "HIGH",
                "message", "高优先级爬取任务已创建"
            );

            return Result.success(result);

        } catch (Exception e) {
            log.error("创建分类爬取任务失败: catalogId={}", catalogId, e);
            return Result.error("创建任务失败: " + e.getMessage());
        }
    }

    /**
     * 停止爬虫（当前任务执行完后停止）
     * 
     * 改进：使用幂等设计，即使没有运行中的任务也返回成功
     * 返回包含 success 字段的 Map，便于前端统一处理
     */
    @PostMapping("/stop")
    public Result<Map<String, Object>> stopCrawler() {
        try {
            log.info("========== 收到停止爬虫请求 ==========");

            // 检查爬虫状态
            boolean isRunning = workerPool.isRunning();
            Map<String, Object> queueStatus = queueService.getQueueStatus();
            int pendingTasks = (int) queueStatus.getOrDefault("pending", 0);
            int processingTasks = (int) queueStatus.getOrDefault("processing", 0);

            log.info("当前状态 - Worker运行中: {}, 待处理任务: {}, 处理中任务: {}", 
                    isRunning, pendingTasks, processingTasks);

            // 幂等设计：无论当前是否运行中，都尝试停止
            if (isRunning || pendingTasks > 0 || processingTasks > 0) {
                workerPool.stop();
                log.info("停止信号已发送，Worker将在当前任务完成后停止");
                
                Map<String, Object> result = Map.of(
                    "success", true,
                    "message", "爬虫将在当前任务完成后停止",
                    "isRunning", isRunning,
                    "pendingTasks", pendingTasks,
                    "processingTasks", processingTasks
                );
                return Result.success(result);
            } else {
                // 已经停止或无任务，也返回成功（幂等性）
                log.info("爬虫已停止或无待处理任务");
                
                Map<String, Object> result = Map.of(
                    "success", true,
                    "message", "爬虫已停止（无运行中的任务）",
                    "isRunning", false,
                    "pendingTasks", 0,
                    "processingTasks", 0
                );
                return Result.success(result);
            }

        } catch (Exception e) {
            log.error("停止爬虫失败", e);
            return Result.error("停止失败: " + e.getMessage());
        }
    }

    /**
     * 继续爬虫（从队列继续弹出任务）
     */
    @PostMapping("/resume")
    public Result<Map<String, Object>> resumeCrawler() {
        try {
            log.info("========== 收到继续爬虫请求 ==========");

            // 检查队列是否有待处理任务
            Map<String, Object> queueStatus = queueService.getQueueStatus();
            int pending = (int) queueStatus.get("pending");

            if (pending == 0) {
                return Result.error("队列中没有待处理任务");
            }

            if (workerPool.isRunning()) {
                return Result.error("爬虫已在运行中");
            }

            // 启动Worker
            workerPool.start();

            Map<String, Object> result = Map.of(
                "success", true,
                "pendingTasks", pending,
                "message", "爬虫已继续运行"
            );

            log.info("爬虫已继续，待处理任务数: {}", pending);
            return Result.success(result);

        } catch (Exception e) {
            log.error("继续爬虫失败", e);
            return Result.error("继续失败: " + e.getMessage());
        }
    }

    /**
     * 获取队列状态
     */
    @GetMapping("/queue-status")
    public Result<Map<String, Object>> getQueueStatus() {
        try {
            Map<String, Object> status = queueService.getQueueStatus();
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取队列状态失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取分类爬取进度
     */
    @GetMapping("/progress/category/{catalogId}")
    public Result<Map<Object, Object>> getCategoryProgress(@PathVariable Integer catalogId) {
        try {
            Map<Object, Object> progress = redisTemplate.opsForHash()
                .entries("crawler:progress:" + catalogId);

            if (progress.isEmpty()) {
                return Result.error("该分类暂无进度信息");
            }

            return Result.success(progress);

        } catch (Exception e) {
            log.error("获取分类进度失败: catalogId={}", catalogId, e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/task/{taskId}")
    public Result<Map<Object, Object>> getTaskDetails(@PathVariable String taskId) {
        try {
            Map<Object, Object> taskDetails = queueService.getTaskDetails(taskId);

            if (taskDetails.isEmpty()) {
                return Result.error("任务不存在");
            }

            return Result.success(taskDetails);

        } catch (Exception e) {
            log.error("获取任务详情失败: taskId={}", taskId, e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取分类统计信息
     */
    @GetMapping("/category-stats")
    public Result<Map<String, Object>> getCategoryStatistics() {
        try {
            Map<String, Object> stats = syncService.getCategoryStatistics();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取分类统计失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 清空所有队列（调试用，慎用）
     */
    @PostMapping("/debug/clear-queues")
    public Result<String> clearAllQueues() {
        try {
            log.warn("========== 清空所有队列（调试操作） ==========");

            if (workerPool.isRunning()) {
                return Result.error("请先停止爬虫再清空队列");
            }

            queueService.clearAllQueues();
            queueService.initializeState();

            log.info("所有队列已清空");
            return Result.success("所有队列已清空");

        } catch (Exception e) {
            log.error("清空队列失败", e);
            return Result.error("清空失败: " + e.getMessage());
        }
    }

    /**
     * 批量爬取指定分类
     * @param catalogIds 分类ID列表
     */
    @PostMapping("/start-batch")
    public Result<Map<String, Object>> startBatchCrawl(@RequestBody List<Integer> catalogIds) {
        try {
            log.info("========== 收到批量爬取请求: {} 个分类 ==========", catalogIds.size());

            if (catalogIds == null || catalogIds.isEmpty()) {
                return Result.error("分类ID列表不能为空");
            }

            // 1. 检查分类是否已同步
            if (!syncService.isCategoriesSynced()) {
                return Result.error("请先同步分类信息");
            }

            // 2. 批量创建任务（优先级=5，手动批量）
            List<String> taskIds = queueService.createBatchTasks(catalogIds, 5);

            log.info("批量任务创建完成: 成功创建={} 个", taskIds.size());

            // 3. 如果Worker未运行，启动
            if (!workerPool.isRunning()) {
                log.info("Worker池未运行，正在启动...");
                workerPool.start();
            }

            Map<String, Object> result = Map.of(
                "success", true,
                "totalCategories", catalogIds.size(),
                "createdTasks", taskIds.size(),
                "message", "批量爬取任务已创建"
            );

            return Result.success(result);

        } catch (Exception e) {
            log.error("批量爬取失败", e);
            return Result.error("批量爬取失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有分类及其爬取状态（仅返回已提交过爬取任务的分类）
     */
    @GetMapping("/categories-with-status")
    public Result<List<Map<String, Object>>> getCategoriesWithStatus() {
        try {
            log.debug("获取所有分类及爬取状态");

            List<CategoryLevel2Code> categories = syncService.getAllLevel2Categories();

            // 获取每个分类下的产品数量，并过滤掉未爬取过的分类
            List<Map<String, Object>> result = categories.stream()
                .map(cat -> {
                    Long productCount = productMapper.selectCount(
                        new QueryWrapper<Product>()
                            .eq("category_level2_id", cat.getId())
                    );

                    Map<String, Object> item = new HashMap<>();
                    item.put("id", cat.getId());
                    item.put("categoryLevel1Id", cat.getCategoryLevel1Id());
                    item.put("categoryLevel2Name", cat.getCategoryLevel2Name());
                    // 转换为小写状态，以匹配前端期望的格式
                    String crawlStatus = cat.getCrawlStatus() != null ? cat.getCrawlStatus().toLowerCase() : "pending";
                    item.put("crawlStatus", crawlStatus);
                    item.put("crawlProgress", cat.getCrawlProgress() != null ? cat.getCrawlProgress() : 0);
                    item.put("lastCrawlTime", cat.getLastCrawlTime());
                    item.put("totalProducts", productCount != null ? productCount : 0);
                    item.put("errorMessage", cat.getErrorMessage());

                    return item;
                })
                // 只返回已经爬取过的分类（有爬取状态、有产品或有最后爬取时间）
                .filter(item -> {
                    String status = (String) item.get("crawlStatus");
                    Long productCount = (Long) item.get("totalProducts");
                    Object lastCrawlTime = item.get("lastCrawlTime");

                    // 排除未开始或待处理状态的分类
                    if ("pending".equals(status) || "not_started".equals(status)) {
                        // 如果状态是未开始，但有产品数据或有最后爬取时间，仍然显示
                        return (productCount != null && productCount > 0) || lastCrawlTime != null;
                    }

                    // 其他状态（processing, completed, failed, in_queue）都显示
                    return true;
                })
                .collect(Collectors.toList());

            return Result.success(result);

        } catch (Exception e) {
            log.error("获取分类状态失败", e);
            return Result.error("获取分类状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有分类列表（包括未爬取的，用于分类选择器）
     */
    @GetMapping("/all-categories")
    public Result<List<Map<String, Object>>> getAllCategories() {
        try {
            log.debug("获取所有分类列表");

            List<CategoryLevel2Code> categories = syncService.getAllLevel2Categories();

            // 返回所有分类，包括未爬取的
            List<Map<String, Object>> result = categories.stream()
                .map(cat -> {
                    Long productCount = productMapper.selectCount(
                        new QueryWrapper<Product>()
                            .eq("category_level2_id", cat.getId())
                    );

                    Map<String, Object> item = new HashMap<>();
                    item.put("id", cat.getId());
                    item.put("categoryLevel1Id", cat.getCategoryLevel1Id());
                    item.put("categoryLevel2Name", cat.getCategoryLevel2Name());
                    // 转换为小写状态，以匹配前端期望的格式
                    String crawlStatus = cat.getCrawlStatus() != null ? cat.getCrawlStatus().toLowerCase() : "pending";
                    item.put("crawlStatus", crawlStatus);
                    item.put("crawlProgress", cat.getCrawlProgress() != null ? cat.getCrawlProgress() : 0);
                    item.put("lastCrawlTime", cat.getLastCrawlTime());
                    item.put("totalProducts", productCount != null ? productCount : 0);
                    item.put("errorMessage", cat.getErrorMessage());

                    return item;
                })
                .collect(Collectors.toList());

            return Result.success(result);

        } catch (Exception e) {
            log.error("获取所有分类失败", e);
            return Result.error("获取所有分类失败: " + e.getMessage());
        }
    }

    /**
     * 获取存储路径信息
     * 返回图片、PDF等文件的存储路径及统计信息
     */
    @GetMapping("/storage-paths")
    public Result<Map<String, Object>> getStoragePaths() {
        try {
            // 构建完整路径
            String basePath = Paths.get(storageBasePath).toAbsolutePath().toString();
            String imagePath = Paths.get(basePath, crawlerConfig.getStorage().getImageDir()).toString();
            String pdfPath = Paths.get(basePath, crawlerConfig.getStorage().getPdfDir()).toString();
            String dataPath = Paths.get(basePath, crawlerConfig.getStorage().getDataDir()).toString();
            String exportPath = Paths.get(basePath, crawlerConfig.getStorage().getExportDir()).toString();

            // 统计文件数量
            File imageDir = new File(imagePath);
            File pdfDir = new File(pdfPath);
            File dataDir = new File(dataPath);
            File exportDir = new File(exportPath);

            int imageCount = imageDir.exists() && imageDir.isDirectory() ?
                (imageDir.listFiles() != null ? imageDir.listFiles().length : 0) : 0;
            int pdfCount = pdfDir.exists() && pdfDir.isDirectory() ?
                (pdfDir.listFiles() != null ? pdfDir.listFiles().length : 0) : 0;

            // 计算目录大小（简化版本）
            long imageSize = getDirectorySize(imageDir);
            long pdfSize = getDirectorySize(pdfDir);

            Map<String, Object> storageInfo = new HashMap<>();
            storageInfo.put("basePath", basePath);
            storageInfo.put("paths", Map.of(
                "images", Map.of(
                    "path", imagePath,
                    "relativePath", crawlerConfig.getStorage().getImageDir(),
                    "exists", imageDir.exists(),
                    "fileCount", imageCount,
                    "sizeBytes", imageSize,
                    "sizeMB", round(imageSize / 1024.0 / 1024.0, 2)
                ),
                "pdfs", Map.of(
                    "path", pdfPath,
                    "relativePath", crawlerConfig.getStorage().getPdfDir(),
                    "exists", pdfDir.exists(),
                    "fileCount", pdfCount,
                    "sizeBytes", pdfSize,
                    "sizeMB", round(pdfSize / 1024.0 / 1024.0, 2)
                ),
                "data", Map.of(
                    "path", dataPath,
                    "relativePath", crawlerConfig.getStorage().getDataDir(),
                    "exists", dataDir.exists()
                ),
                "exports", Map.of(
                    "path", exportPath,
                    "relativePath", crawlerConfig.getStorage().getExportDir(),
                    "exists", exportDir.exists()
                )
            ));
            storageInfo.put("saveImages", crawlerConfig.getSaveImages());
            storageInfo.put("config", Map.of(
                "storageBasePath", storageBasePath,
                "imageDir", crawlerConfig.getStorage().getImageDir(),
                "pdfDir", crawlerConfig.getStorage().getPdfDir(),
                "dataDir", crawlerConfig.getStorage().getDataDir(),
                "exportDir", crawlerConfig.getStorage().getExportDir()
            ));

            return Result.success(storageInfo);

        } catch (Exception e) {
            log.error("获取存储路径信息失败", e);
            return Result.error("获取存储路径信息失败: " + e.getMessage());
        }
    }

    /**
     * 递归计算目录大小
     */
    private long getDirectorySize(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return 0;
        }

        long size = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += getDirectorySize(file);
                }
            }
        }
        return size;
    }

    /**
     * 四舍五入保留指定小数位
     */
    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
            "status", "UP",
            "version", "3.0",
            "timestamp", System.currentTimeMillis()
        );
        return Result.success(health);
    }
}
