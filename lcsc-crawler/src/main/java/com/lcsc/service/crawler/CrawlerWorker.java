package com.lcsc.service.crawler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 爬虫工作者
 * 负责从Redis队列获取任务并执行具体的爬取逻辑
 *
 * @author lcsc-crawler
 * @since 2025-09-03
 */
@Component
public class CrawlerWorker {

    @Autowired
    private RedisQueueService queueService;

    @Autowired
    private LcscApiService apiService;

    @Autowired
    private ProductDataProcessor dataProcessor;

    @Autowired
    private com.lcsc.service.TaskLogService taskLogService;

    @Autowired
    private DataPersistenceService dataPersistenceService;

    private ExecutorService workerExecutor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean shouldStop = new AtomicBoolean(false);

    /**
     * 启动爬虫工作者
     */
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            shouldStop.set(false);
            // 创建新的线程池
            workerExecutor = Executors.newFixedThreadPool(3);
            System.out.println("启动爬虫工作者，线程数: 3");
            
            // 启动多个工作线程
            for (int i = 0; i < 3; i++) {
                final int workerId = i + 1;
                workerExecutor.submit(() -> workerLoop(workerId));
            }
        }
    }

    /**
     * 停止爬虫工作者
     */
    public void stop() {
        shouldStop.set(true);
        isRunning.set(false);
        
        if (workerExecutor != null && !workerExecutor.isShutdown()) {
            try {
                workerExecutor.shutdown();
                if (!workerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    workerExecutor.shutdownNow();
                }
                System.out.println("爬虫工作者已停止");
            } catch (InterruptedException e) {
                workerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 工作循环
     */
    private void workerLoop(int workerId) {
        System.out.println("工作者 " + workerId + " 开始运行");
        
        while (!shouldStop.get()) {
            try {
                // 检查是否暂停
                if (queueService.isPaused()) {
                    Thread.sleep(5000); // 暂停时等待5秒
                    continue;
                }
                
                // 获取下一个任务
                RedisQueueService.CrawlerTask task = queueService.getNextTask();
                
                if (task == null) {
                    // 没有任务时等待
                    Thread.sleep(1000);
                    continue;
                }
                
                System.out.println("工作者 " + workerId + " 处理任务: " + task.getTaskId());
                
                // 记录任务开始执行日志
                taskLogService.logTask(task.getTaskId(), "INFO", "START_EXECUTION", 
                    "工作者 " + workerId + " 开始执行任务", 0);
                
                // 执行任务
                executeTask(task);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("工作者 " + workerId + " 异常: " + e.getMessage());
                try {
                    Thread.sleep(5000); // 异常后等待5秒
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        System.out.println("工作者 " + workerId + " 已停止");
    }

    /**
     * 执行任务
     */
    private void executeTask(RedisQueueService.CrawlerTask task) {
        try {
            switch (task.getTaskType()) {
                case CATALOG_FETCH:
                    executeCatalogFetchTask(task);
                    break;
                case PACKAGE_FETCH:
                    executePackageFetchTask(task);
                    break;
                case PRODUCT_FETCH:
                    executeProductFetchTask(task);
                    break;
                default:
                    queueService.failTask(task.getTaskId(), "未知任务类型: " + task.getTaskType());
            }
            
        } catch (Exception e) {
            String errorMessage = "执行任务失败: " + e.getMessage();
            System.err.println("任务 " + task.getTaskId() + " " + errorMessage);
            queueService.failTask(task.getTaskId(), errorMessage);
        }
    }

    /**
     * 执行目录获取任务
     */
    private void executeCatalogFetchTask(RedisQueueService.CrawlerTask task) {
        try {
            taskLogService.logTask(task.getTaskId(), "INFO", "CATALOG_FETCH", 
                "开始获取目录列表", 10);
            
            Map<String, Object> response = apiService.getCatalogList().get(30, TimeUnit.SECONDS);
            
            // 调试：打印完整响应结构
            System.out.println("=== DEBUG: 完整响应结构 ===");
            System.out.println("Response keys: " + response.keySet());
            for (String key : response.keySet()) {
                Object value = response.get(key);
                System.out.println("Key '" + key + "': " + (value != null ? value.getClass().getSimpleName() : "null") + 
                    " - " + (value instanceof List ? "List size: " + ((List<?>)value).size() : 
                            value instanceof Map ? "Map size: " + ((Map<?,?>)value).size() : 
                            String.valueOf(value)));
            }
            System.out.println("=== DEBUG END ===");
            
            // API service已经提取了result字段，直接从response中获取catalogList
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> catalogs = (List<Map<String, Object>>) response.get("catalogList");
            
            if (catalogs == null) {
                catalogs = new ArrayList<>();
            }
            
            Map<String, Object> result = Map.of(
                "catalogCount", catalogs.size(),
                "catalogs", catalogs,
                "timestamp", System.currentTimeMillis()
            );
            
            taskLogService.logTask(task.getTaskId(), "SUCCESS", "CATALOG_COMPLETED",
                "目录获取完成，共 " + catalogs.size() + " 个分类", 80);

            // 自动保存分类数据到数据库
            try {
                taskLogService.logTask(task.getTaskId(), "INFO", "CATALOG_PERSIST",
                    "开始保存分类数据到数据库...", 85);

                Map<String, Object> persistResult = dataPersistenceService.persistCatalogData(response);

                if (Boolean.TRUE.equals(persistResult.get("success"))) {
                    Integer level1Count = (Integer) persistResult.get("level1CategoriesCreated");
                    Integer level2Count = (Integer) persistResult.get("level2CategoriesCreated");

                    taskLogService.logTask(task.getTaskId(), "SUCCESS", "CATALOG_PERSIST_COMPLETED",
                        String.format("分类数据保存成功：一级分类 %d 个，二级分类 %d 个", level1Count, level2Count), 95);

                    System.out.println(String.format("分类数据已保存到数据库：一级分类 %d 个，二级分类 %d 个", level1Count, level2Count));
                } else {
                    String error = (String) persistResult.get("error");
                    taskLogService.logTask(task.getTaskId(), "WARN", "CATALOG_PERSIST_FAILED",
                        "分类数据保存失败：" + error, 90);
                    System.err.println("分类数据保存失败：" + error);
                }
            } catch (Exception e) {
                taskLogService.logTask(task.getTaskId(), "ERROR", "CATALOG_PERSIST_ERROR",
                    "分类数据保存异常：" + e.getMessage(), 90);
                System.err.println("分类数据保存异常：" + e.getMessage());
                e.printStackTrace();
            }

            queueService.completeTask(task.getTaskId(), result);

            // 为每个分类创建封装获取任务
            createPackageFetchTasks(catalogs);

            System.out.println("目录获取完成，共 " + catalogs.size() + " 个分类");
            
        } catch (Exception e) {
            throw new RuntimeException("获取目录失败", e);
        }
    }

    /**
     * 执行封装获取任务
     */
    private void executePackageFetchTask(RedisQueueService.CrawlerTask task) {
        try {
            Map<String, Object> params = task.getParams();
            Integer catalogId = (Integer) params.get("catalogId");
            
            taskLogService.logTask(task.getTaskId(), "INFO", "PACKAGE_FETCH", 
                "开始获取分类 " + catalogId + " 的封装列表", 20);
            
            Map<String, Object> filterParams = Map.of(
                "catalogIdList", List.of(catalogId)
            );
            
            Map<String, Object> response = apiService.getQueryParamGroup(filterParams)
                    .get(30, TimeUnit.SECONDS);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> packages = (List<Map<String, Object>>) response.get("Package");
            
            if (packages == null) {
                packages = new java.util.ArrayList<>();
            }
            
            Map<String, Object> result = Map.of(
                "catalogId", catalogId,
                "packageCount", packages.size(),
                "packages", packages,
                "timestamp", System.currentTimeMillis()
            );
            
            taskLogService.logTask(task.getTaskId(), "SUCCESS", "PACKAGE_COMPLETED", 
                "分类 " + catalogId + " 封装获取完成，共 " + packages.size() + " 种封装", 100);
            
            queueService.completeTask(task.getTaskId(), result);
            
            // 为每个封装创建产品获取任务
            createProductFetchTasks(catalogId, packages);
            
            System.out.println("分类 " + catalogId + " 封装获取完成，共 " + packages.size() + " 种封装");
            
        } catch (Exception e) {
            throw new RuntimeException("获取封装失败", e);
        }
    }

    /**
     * 执行产品获取任务
     */
    private void executeProductFetchTask(RedisQueueService.CrawlerTask task) {
        try {
            Map<String, Object> params = task.getParams();
            Integer catalogId = (Integer) params.get("catalogId");
            String packageName = (String) params.get("packageName");
            Integer currentPage = (Integer) params.get("currentPage");
            Integer pageSize = (Integer) params.get("pageSize");
            
            taskLogService.logTask(task.getTaskId(), "INFO", "PRODUCT_FETCH", 
                "正在爬取分类 " + catalogId + " 封装 " + packageName + " 第 " + currentPage + " 页", 50);
            
            Map<String, Object> filterParams = Map.of(
                "currentPage", currentPage,
                "pageSize", pageSize,
                "catalogIdList", List.of(catalogId),
                "encapValueList", List.of(packageName)
            );
            
            Map<String, Object> response = apiService.getQueryList(filterParams)
                    .get(30, TimeUnit.SECONDS);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> products = (List<Map<String, Object>>) response.get("dataList");
            Integer totalPages = (Integer) response.get("totalPages");
            
            // 处理产品数据
            int processedCount = dataProcessor.processProductList(products, catalogId, null);
            
            Map<String, Object> result = Map.of(
                "catalogId", catalogId,
                "packageName", packageName,
                "currentPage", currentPage,
                "totalPages", totalPages,
                "productCount", products.size(),
                "processedCount", processedCount,
                "failedCount", products.size() - processedCount,
                "timestamp", System.currentTimeMillis()
            );
            
            taskLogService.logTask(task.getTaskId(), "SUCCESS", "PRODUCT_COMPLETED", 
                "第 " + currentPage + "/" + totalPages + " 页爬取完成，处理 " + processedCount + " 个产品", 100);
            
            queueService.completeTask(task.getTaskId(), result);
            
            // 如果还有更多页面，创建下一页任务
            if (currentPage < totalPages) {
                createNextPageTask(catalogId, packageName, currentPage + 1, pageSize);
            }
            
            System.out.println("产品获取完成 - 分类: " + catalogId + 
                             ", 封装: " + packageName + 
                             ", 页面: " + currentPage + "/" + totalPages + 
                             ", 处理: " + processedCount + " 个产品");
            
        } catch (Exception e) {
            throw new RuntimeException("获取产品失败", e);
        }
    }

    /**
     * 为分类创建封装获取任务
     */
    private void createPackageFetchTasks(List<Map<String, Object>> catalogs) {
        for (Map<String, Object> catalog : catalogs) {
            Integer catalogId = (Integer) catalog.get("catalogId");
            
            Map<String, Object> params = Map.of("catalogId", catalogId);
            queueService.createTask(RedisQueueService.TaskType.PACKAGE_FETCH, params);
            
            // 处理子分类
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) catalog.get("childCatelogs");
            if (children != null && !children.isEmpty()) {
                for (Map<String, Object> child : children) {
                    Integer childCatalogId = (Integer) child.get("catalogId");
                    Map<String, Object> childParams = Map.of("catalogId", childCatalogId);
                    queueService.createTask(RedisQueueService.TaskType.PACKAGE_FETCH, childParams);
                }
            }
        }
    }

    /**
     * 为封装创建产品获取任务
     */
    private void createProductFetchTasks(Integer catalogId, List<Map<String, Object>> packages) {
        for (Map<String, Object> packageData : packages) {
            String packageName = (String) packageData.get("name");
            
            Map<String, Object> params = Map.of(
                "catalogId", catalogId,
                "packageName", packageName,
                "currentPage", 1,
                "pageSize", 25
            );
            
            queueService.createTask(RedisQueueService.TaskType.PRODUCT_FETCH, params);
        }
    }

    /**
     * 创建下一页任务
     */
    private void createNextPageTask(Integer catalogId, String packageName, Integer nextPage, Integer pageSize) {
        Map<String, Object> params = Map.of(
            "catalogId", catalogId,
            "packageName", packageName,
            "currentPage", nextPage,
            "pageSize", pageSize
        );
        
        queueService.createTask(RedisQueueService.TaskType.PRODUCT_FETCH, params);
    }

    /**
     * 获取工作状态
     */
    public Map<String, Object> getWorkerStatus() {
        return Map.of(
            "isRunning", isRunning.get(),
            "shouldStop", shouldStop.get(),
            "threadCount", 3,
            "queueStatus", queueService.getQueueStatus()
        );
    }

    /**
     * 手动创建完整爬取任务
     */
    public String startFullCrawl() {
        try {
            // 创建初始目录获取任务
            String taskId = queueService.createTask(
                RedisQueueService.TaskType.CATALOG_FETCH, 
                Map.of("fullCrawl", true)
            );
            
            System.out.println("开始完整爬取，初始任务: " + taskId);
            return taskId;
            
        } catch (Exception e) {
            throw new RuntimeException("启动完整爬取失败", e);
        }
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return isRunning.get();
    }
}