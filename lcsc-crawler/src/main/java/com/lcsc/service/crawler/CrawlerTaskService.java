package com.lcsc.service.crawler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.lcsc.controller.CrawlerWebSocketController;
import com.lcsc.service.TaskLogService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 爬虫任务调度服务
 * 负责统一管理和调度爬虫任务
 *
 * @author lcsc-crawler
 * @since 2025-09-03
 */
@Service
public class CrawlerTaskService {

    @Autowired
    private RedisQueueService queueService;

    @Autowired
    private CrawlerWorker crawlerWorker;

    @Autowired
    private LcscApiService apiService;

    @Autowired
    private TaskLogService taskLogService;

    @Autowired
    private CrawlerWebSocketController webSocketController;

    // 任务统计信息
    private final Map<String, Object> taskStats = new HashMap<>();

    @PostConstruct
    public void init() {
        System.out.println("爬虫任务调度服务初始化完成");
        initializeStats();
    }

    @PreDestroy
    public void destroy() {
        stopCrawler();
        System.out.println("爬虫任务调度服务已关闭");
    }

    /**
     * 启动爬虫
     */
    public CompletableFuture<Map<String, Object>> startCrawler() {
        return CompletableFuture.supplyAsync(() -> {
            String systemTaskId = "SYSTEM_START_" + System.currentTimeMillis();
            
            try {
                // 记录系统启动日志
                taskLogService.logTaskStart(systemTaskId, "SYSTEM_START", Map.of(
                    "operation", "startCrawler",
                    "timestamp", LocalDateTime.now()
                ));

                if (crawlerWorker.isRunning()) {
                    taskLogService.logTask(systemTaskId, "WARN", "CHECK_STATUS", 
                        "爬虫已在运行中", 100);
                    return createResponse(false, "爬虫已在运行中", null);
                }

                // 推送启动状态
                if (webSocketController != null) {
                    webSocketController.broadcastSystemStatus("STARTING", Map.of(
                        "message", "正在启动爬虫系统",
                        "taskId", systemTaskId
                    ));
                }

                // 启动工作者
                taskLogService.logTaskProgress(systemTaskId, "START_WORKER", "启动爬虫工作者", 30);
                crawlerWorker.start();

                // 创建初始任务
                taskLogService.logTaskProgress(systemTaskId, "CREATE_INITIAL_TASK", "创建初始爬取任务", 60);
                String initialTaskId = crawlerWorker.startFullCrawl();

                updateStats("lastStartTime", LocalDateTime.now());
                updateStats("status", "RUNNING");
                updateStats("systemTaskId", systemTaskId);

                // 记录启动成功
                taskLogService.logTaskCompleted(systemTaskId, Map.of(
                    "initialTaskId", initialTaskId,
                    "workerStatus", crawlerWorker.getWorkerStatus()
                ));

                // 推送启动成功状态
                if (webSocketController != null) {
                    webSocketController.broadcastSystemStatus("RUNNING", Map.of(
                        "initialTaskId", initialTaskId,
                        "systemTaskId", systemTaskId,
                        "startTime", LocalDateTime.now()
                    ));
                }

                return createResponse(true, "爬虫启动成功", Map.of(
                    "initialTaskId", initialTaskId,
                    "systemTaskId", systemTaskId,
                    "workerStatus", crawlerWorker.getWorkerStatus(),
                    "startTime", LocalDateTime.now()
                ));

            } catch (Exception e) {
                System.err.println("启动爬虫失败: " + e.getMessage());
                
                // 记录启动失败
                taskLogService.logTaskFailed(systemTaskId, "启动失败: " + e.getMessage());
                
                // 推送启动失败状态
                if (webSocketController != null) {
                    webSocketController.broadcastSystemStatus("FAILED", Map.of(
                        "error", e.getMessage(),
                        "systemTaskId", systemTaskId
                    ));
                }
                
                return createResponse(false, "启动失败: " + e.getMessage(), null);
            }
        });
    }

    /**
     * 停止爬虫
     */
    public CompletableFuture<Map<String, Object>> stopCrawler() {
        return CompletableFuture.supplyAsync(() -> {
            String systemTaskId = "SYSTEM_STOP_" + System.currentTimeMillis();
            
            try {
                // 记录系统停止日志
                taskLogService.logTaskStart(systemTaskId, "SYSTEM_STOP", Map.of(
                    "operation", "stopCrawler",
                    "timestamp", LocalDateTime.now()
                ));

                if (!crawlerWorker.isRunning()) {
                    taskLogService.logTask(systemTaskId, "WARN", "CHECK_STATUS", 
                        "爬虫未在运行", 100);
                    return createResponse(false, "爬虫未在运行", null);
                }

                // 推送停止状态
                if (webSocketController != null) {
                    webSocketController.broadcastSystemStatus("STOPPING", Map.of(
                        "message", "正在停止爬虫系统",
                        "taskId", systemTaskId
                    ));
                }

                // 停止工作者
                taskLogService.logTaskProgress(systemTaskId, "STOP_WORKER", "停止爬虫工作者", 70);
                crawlerWorker.stop();

                updateStats("lastStopTime", LocalDateTime.now());
                updateStats("status", "STOPPED");

                // 记录停止成功
                taskLogService.logTaskCompleted(systemTaskId, Map.of(
                    "stopTime", LocalDateTime.now(),
                    "finalStats", new HashMap<>(taskStats)
                ));

                // 推送停止成功状态
                if (webSocketController != null) {
                    webSocketController.broadcastSystemStatus("STOPPED", Map.of(
                        "systemTaskId", systemTaskId,
                        "stopTime", LocalDateTime.now(),
                        "finalStats", taskStats
                    ));
                }

                return createResponse(true, "爬虫已停止", Map.of(
                    "systemTaskId", systemTaskId,
                    "stopTime", LocalDateTime.now(),
                    "finalStats", taskStats
                ));

            } catch (Exception e) {
                System.err.println("停止爬虫失败: " + e.getMessage());
                
                // 记录停止失败
                taskLogService.logTaskFailed(systemTaskId, "停止失败: " + e.getMessage());
                
                // 推送停止失败状态
                if (webSocketController != null) {
                    webSocketController.broadcastSystemStatus("STOP_FAILED", Map.of(
                        "error", e.getMessage(),
                        "systemTaskId", systemTaskId
                    ));
                }
                
                return createResponse(false, "停止失败: " + e.getMessage(), null);
            }
        });
    }

    /**
     * 暂停爬虫
     */
    public CompletableFuture<Map<String, Object>> pauseCrawler() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                queueService.pauseAllTasks();
                updateStats("lastPauseTime", LocalDateTime.now());
                updateStats("status", "PAUSED");

                return createResponse(true, "爬虫已暂停", null);

            } catch (Exception e) {
                return createResponse(false, "暂停失败: " + e.getMessage(), null);
            }
        });
    }

    /**
     * 恢复爬虫
     */
    public CompletableFuture<Map<String, Object>> resumeCrawler() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                queueService.resumeAllTasks();
                updateStats("lastResumeTime", LocalDateTime.now());
                updateStats("status", "RUNNING");

                return createResponse(true, "爬虫已恢复", null);

            } catch (Exception e) {
                return createResponse(false, "恢复失败: " + e.getMessage(), null);
            }
        });
    }

    /**
     * 获取爬虫状态
     */
    public Map<String, Object> getCrawlerStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 基本状态
        status.put("isRunning", crawlerWorker.isRunning());
        status.put("isPaused", queueService.isPaused());
        status.put("workerStatus", crawlerWorker.getWorkerStatus());
        
        // 队列状态
        status.put("queueStatus", queueService.getQueueStatus());
        
        // 统计信息
        status.put("taskStats", new HashMap<>(taskStats));
        
        // 时间戳
        status.put("timestamp", LocalDateTime.now());
        
        return status;
    }

    /**
     * 创建自定义爬取任务
     */
    @Async
    public CompletableFuture<Map<String, Object>> createCustomCrawlTask(
            List<Integer> catalogIds, 
            List<String> packages, 
            Integer priority) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (catalogIds == null || catalogIds.isEmpty()) {
                    return createResponse(false, "分类ID列表不能为空", null);
                }

                List<String> taskIds = queueService.createProductFetchTasks(catalogIds, packages != null ? packages : List.of());

                updateStats("customTaskCount", (Integer) taskStats.getOrDefault("customTaskCount", 0) + taskIds.size());

                return createResponse(true, "自定义任务创建成功", Map.of(
                    "taskIds", taskIds,
                    "taskCount", taskIds.size()
                ));

            } catch (Exception e) {
                return createResponse(false, "创建任务失败: " + e.getMessage(), null);
            }
        });
    }

    /**
     * 获取任务详情
     */
    public Map<String, Object> getTaskDetails(String taskId) {
        try {
            RedisQueueService.CrawlerTask task = queueService.getTask(taskId);
            
            if (task == null) {
                return createResponse(false, "任务不存在", null);
            }

            return createResponse(true, "获取成功", Map.of(
                "task", task,
                "queueStatus", queueService.getQueueStatus()
            ));

        } catch (Exception e) {
            return createResponse(false, "获取任务详情失败: " + e.getMessage(), null);
        }
    }

    /**
     * 获取爬取统计报告
     */
    public Map<String, Object> getCrawlerReport() {
        Map<String, Object> report = new HashMap<>();
        
        // 队列统计
        Map<String, Object> queueStatus = queueService.getQueueStatus();
        report.put("queueStats", queueStatus);
        
        // 任务统计
        report.put("taskStats", new HashMap<>(taskStats));
        
        // 运行状态
        report.put("crawlerStatus", Map.of(
            "isRunning", crawlerWorker.isRunning(),
            "isPaused", queueService.isPaused()
        ));
        
        // 生成报告时间
        report.put("reportTime", LocalDateTime.now());
        
        return report;
    }

    /**
     * 测试API连接
     */
    public CompletableFuture<Map<String, Object>> testApiConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 测试目录接口
                long startTime = System.currentTimeMillis();
                Map<String, Object> response = apiService.getCatalogList().get();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> catalogs = (List<Map<String, Object>>) response.get("catalogList");
                long duration = System.currentTimeMillis() - startTime;

                return createResponse(true, "API连接正常", Map.of(
                    "catalogCount", catalogs.size(),
                    "responseTime", duration + "ms",
                    "testTime", LocalDateTime.now()
                ));

            } catch (Exception e) {
                return createResponse(false, "API连接失败: " + e.getMessage(), null);
            }
        });
    }

    /**
     * 清理已完成的任务
     */
    @Scheduled(fixedRate = 3600000) // 每小时执行一次
    public void cleanupTasks() {
        try {
            queueService.cleanupCompletedTasks(24); // 清理24小时前的任务
            updateStats("lastCleanupTime", LocalDateTime.now());
            System.out.println("任务清理完成");
            
        } catch (Exception e) {
            System.err.println("清理任务失败: " + e.getMessage());
        }
    }

    /**
     * 定期更新统计信息
     */
    @Scheduled(fixedRate = 300000) // 每5分钟更新一次
    public void updateStatistics() {
        try {
            Map<String, Object> queueStatus = queueService.getQueueStatus();
            
            // 计算总任务数
            Long pending = (Long) queueStatus.get("pending");
            Long processing = (Long) queueStatus.get("processing");
            Long completed = (Long) queueStatus.get("completed");
            Long failed = (Long) queueStatus.get("failed");
            
            Long totalTasks = pending + processing + completed + failed;
            
            updateStats("totalTasks", totalTasks);
            updateStats("completionRate", totalTasks > 0 ? (double) completed / totalTasks : 0.0);
            updateStats("failureRate", totalTasks > 0 ? (double) failed / totalTasks : 0.0);
            updateStats("lastUpdateTime", LocalDateTime.now());
            
        } catch (Exception e) {
            System.err.println("更新统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 初始化统计信息
     */
    private void initializeStats() {
        taskStats.put("totalTasks", 0L);
        taskStats.put("completionRate", 0.0);
        taskStats.put("failureRate", 0.0);
        taskStats.put("customTaskCount", 0);
        taskStats.put("status", "INITIALIZED");
        taskStats.put("initTime", LocalDateTime.now());
    }

    /**
     * 更新统计信息
     */
    private void updateStats(String key, Object value) {
        synchronized (taskStats) {
            taskStats.put(key, value);
        }
    }

    /**
     * 创建统一响应格式
     */
    private Map<String, Object> createResponse(boolean success, String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now());
        
        if (data != null) {
            response.put("data", data);
        }
        
        return response;
    }

    /**
     * 获取健康状态
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // 检查Redis连接
            Map<String, Object> queueStatus = queueService.getQueueStatus();
            boolean redisHealthy = queueStatus != null && !queueStatus.isEmpty();
            
            // 检查工作者状态
            boolean workerHealthy = crawlerWorker != null;
            
            // 整体健康状态
            boolean overall = redisHealthy && workerHealthy;
            
            health.put("overall", overall ? "HEALTHY" : "UNHEALTHY");
            health.put("redis", redisHealthy ? "UP" : "DOWN");
            health.put("worker", workerHealthy ? "UP" : "DOWN");
            health.put("checkTime", LocalDateTime.now());
            
        } catch (Exception e) {
            health.put("overall", "ERROR");
            health.put("error", e.getMessage());
        }
        
        return health;
    }

    /**
     * 获取Redis队列服务实例
     */
    public RedisQueueService getQueueService() {
        return queueService;
    }

    /**
     * 获取API服务实例
     */
    public LcscApiService getApiService() {
        return apiService;
    }
}