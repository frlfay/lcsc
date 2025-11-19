package com.lcsc.service.crawler.scheduler;

import com.lcsc.service.crawler.monitoring.CrawlerMetricsCollector;
import com.lcsc.service.crawler.memory.MemoryOptimizer;
import com.lcsc.service.crawler.data.BatchDataProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 智能任务调度器
 * 支持任务优先级、负载均衡、故障恢复等高级功能
 * 
 * @author lcsc-crawler
 * @since 2025-09-06
 */
@Component
public class SmartTaskScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartTaskScheduler.class);
    
    @Value("${lcsc.crawler.scheduler.core-pool-size:5}")
    private int corePoolSize;
    
    @Value("${lcsc.crawler.scheduler.max-pool-size:20}")
    private int maxPoolSize;
    
    @Value("${lcsc.crawler.scheduler.queue-capacity:1000}")
    private int queueCapacity;
    
    @Value("${lcsc.crawler.scheduler.keep-alive-seconds:300}")
    private long keepAliveSeconds;
    
    @Value("${lcsc.crawler.scheduler.max-retry-attempts:3}")
    private int maxRetryAttempts;
    
    @Autowired
    private CrawlerMetricsCollector metricsCollector;
    
    @Autowired
    private MemoryOptimizer memoryOptimizer;
    
    @Autowired
    private BatchDataProcessor batchDataProcessor;
    
    // 线程池和队列
    private ThreadPoolTaskExecutor taskExecutor;
    private final PriorityBlockingQueue<CrawlerTask> taskQueue = 
        new PriorityBlockingQueue<>(1000, new TaskPriorityComparator());
    
    // 任务跟踪
    private final Map<String, CrawlerTask> activeTasks = new ConcurrentHashMap<>();
    private final Map<String, CrawlerTask> completedTasks = new ConcurrentHashMap<>();
    private final Map<String, CrawlerTask> failedTasks = new ConcurrentHashMap<>();
    
    // 统计信息
    private final AtomicLong totalTasksScheduled = new AtomicLong(0);
    private final AtomicLong totalTasksCompleted = new AtomicLong(0);
    private final AtomicLong totalTasksFailed = new AtomicLong(0);
    private final AtomicInteger currentlyRunningTasks = new AtomicInteger(0);
    
    // 调度器状态
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private ScheduledExecutorService scheduledExecutor;
    
    /**
     * 启动任务调度器
     */
    public synchronized void start() {
        if (isRunning) {
            logger.warn("任务调度器已在运行中");
            return;
        }
        
        logger.info("启动智能任务调度器");
        
        // 初始化线程池
        initializeThreadPool();
        
        // 启动定时调度
        scheduledExecutor = Executors.newScheduledThreadPool(2);
        
        // 任务分发器
        scheduledExecutor.scheduleWithFixedDelay(this::dispatchTasks, 1, 1, TimeUnit.SECONDS);
        
        // 任务清理器
        scheduledExecutor.scheduleWithFixedDelay(this::cleanupTasks, 60, 60, TimeUnit.SECONDS);
        
        // 健康检查
        scheduledExecutor.scheduleWithFixedDelay(this::performHealthCheck, 30, 30, TimeUnit.SECONDS);
        
        isRunning = true;
        logger.info("任务调度器启动成功");
    }
    
    /**
     * 停止任务调度器
     */
    public synchronized void stop() {
        if (!isRunning) {
            logger.warn("任务调度器未在运行");
            return;
        }
        
        logger.info("停止任务调度器");
        isRunning = false;
        
        // 停止接受新任务
        if (taskExecutor != null) {
            taskExecutor.shutdown();
            
            try {
                if (!taskExecutor.getThreadPoolExecutor().awaitTermination(30, TimeUnit.SECONDS)) {
                    taskExecutor.getThreadPoolExecutor().shutdownNow();
                    logger.warn("强制关闭任务执行器");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                taskExecutor.getThreadPoolExecutor().shutdownNow();
            }
        }
        
        // 停止定时调度
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }
        
        logger.info("任务调度器已停止");
    }
    
    /**
     * 暂停任务调度
     */
    public synchronized void pause() {
        isPaused = true;
        logger.info("任务调度器已暂停");
    }
    
    /**
     * 恢复任务调度
     */
    public synchronized void resume() {
        isPaused = false;
        logger.info("任务调度器已恢复");
    }
    
    /**
     * 提交任务
     */
    public String submitTask(String taskType, String description, Map<String, Object> parameters, TaskPriority priority) {
        if (!isRunning) {
            throw new IllegalStateException("任务调度器未运行");
        }
        
        String taskId = generateTaskId();
        CrawlerTask task = new CrawlerTask(
            taskId, taskType, description, parameters, priority,
            LocalDateTime.now(), TaskStatus.PENDING
        );
        
        taskQueue.offer(task);
        totalTasksScheduled.incrementAndGet();
        
        logger.info("任务已提交: {} [{}] - {}", taskId, priority, description);
        
        // 记录指标
        metricsCollector.recordCategoryProcessed("任务提交");
        
        return taskId;
    }
    
    /**
     * 批量提交任务
     */
    public List<String> submitTasks(List<TaskDefinition> taskDefinitions) {
        List<String> taskIds = new ArrayList<>();
        
        for (TaskDefinition definition : taskDefinitions) {
            String taskId = submitTask(
                definition.getTaskType(),
                definition.getDescription(),
                definition.getParameters(),
                definition.getPriority()
            );
            taskIds.add(taskId);
        }
        
        logger.info("批量提交任务完成，共 {} 个任务", taskIds.size());
        return taskIds;
    }
    
    /**
     * 获取任务状态
     */
    public CrawlerTask getTaskStatus(String taskId) {
        // 查找活动任务
        CrawlerTask task = activeTasks.get(taskId);
        if (task != null) {
            return task;
        }
        
        // 查找已完成任务
        task = completedTasks.get(taskId);
        if (task != null) {
            return task;
        }
        
        // 查找失败任务
        task = failedTasks.get(taskId);
        if (task != null) {
            return task;
        }
        
        // 查找队列中的任务
        return taskQueue.stream()
            .filter(t -> t.getTaskId().equals(taskId))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        // 尝试从队列中移除
        boolean removed = taskQueue.removeIf(task -> task.getTaskId().equals(taskId));
        if (removed) {
            logger.info("任务已从队列中取消: {}", taskId);
            return true;
        }
        
        // 尝试取消正在执行的任务
        CrawlerTask activeTask = activeTasks.get(taskId);
        if (activeTask != null) {
            activeTask.setStatus(TaskStatus.CANCELLED);
            activeTask.setEndTime(LocalDateTime.now());
            activeTasks.remove(taskId);
            logger.info("正在执行的任务已取消: {}", taskId);
            return true;
        }
        
        return false;
    }
    
    /**
     * 任务分发器
     */
    private void dispatchTasks() {
        if (isPaused || !isRunning) {
            return;
        }
        
        // 检查系统资源状态
        if (!canAcceptNewTask()) {
            return;
        }
        
        // 获取下一个任务
        CrawlerTask task = taskQueue.poll();
        if (task == null) {
            return;
        }
        
        // 执行任务
        executeTask(task);
    }
    
    /**
     * 检查是否可以接受新任务
     */
    private boolean canAcceptNewTask() {
        // 检查线程池状态
        if (taskExecutor.getActiveCount() >= maxPoolSize) {
            logger.debug("线程池已满，等待任务完成");
            return false;
        }
        
        // 检查内存状态
        MemoryOptimizer.MemoryStatus memoryStatus = memoryOptimizer.checkMemoryStatus();
        if (memoryStatus == MemoryOptimizer.MemoryStatus.CRITICAL) {
            logger.warn("内存使用率过高，暂停接受新任务");
            return false;
        }
        
        // 检查批处理队列状态
        BatchDataProcessor.BatchProcessorStats batchStats = batchDataProcessor.getStats();
        if (batchStats.getCurrentQueueSize() > 5000) {
            logger.warn("批处理队列积压过多，暂停接受新任务");
            return false;
        }
        
        return true;
    }
    
    /**
     * 执行任务
     */
    @Async
    public void executeTask(CrawlerTask task) {
        String taskId = task.getTaskId();
        
        // 更新任务状态
        task.setStatus(TaskStatus.RUNNING);
        task.setStartTime(LocalDateTime.now());
        activeTasks.put(taskId, task);
        currentlyRunningTasks.incrementAndGet();
        
        logger.info("开始执行任务: {} [{}] - {}", taskId, task.getPriority(), task.getDescription());
        
        try {
            // 根据任务类型执行相应逻辑
            TaskResult result = executeTaskLogic(task);
            
            // 任务执行成功
            task.setStatus(TaskStatus.COMPLETED);
            task.setResult(result);
            task.setEndTime(LocalDateTime.now());
            
            // 移动到完成队列
            activeTasks.remove(taskId);
            completedTasks.put(taskId, task);
            totalTasksCompleted.incrementAndGet();
            
            logger.info("任务执行成功: {} 耗时: {}ms", 
                taskId, task.getExecutionTimeMs());
                
            // 记录成功指标
            metricsCollector.recordProductsProcessed(
                result.getProcessedCount(), task.getTaskType());
                
        } catch (Exception e) {
            logger.error("任务执行失败: {} - {}", taskId, e.getMessage(), e);
            handleTaskFailure(task, e);
            
        } finally {
            currentlyRunningTasks.decrementAndGet();
        }
    }
    
    /**
     * 执行具体任务逻辑
     */
    private TaskResult executeTaskLogic(CrawlerTask task) throws Exception {
        String taskType = task.getTaskType();
        Map<String, Object> parameters = task.getParameters();
        
        switch (taskType) {
            case "CATALOG_FETCH":
                return executeCatalogFetchTask(parameters);
            case "PRODUCT_CRAWL":
                return executeProductCrawlTask(parameters);
            case "DATA_EXPORT":
                return executeDataExportTask(parameters);
            default:
                throw new UnsupportedOperationException("不支持的任务类型: " + taskType);
        }
    }
    
    private TaskResult executeCatalogFetchTask(Map<String, Object> parameters) throws Exception {
        // 模拟分类获取任务
        Thread.sleep(2000);
        return new TaskResult(true, "分类获取成功", 1);
    }
    
    private TaskResult executeProductCrawlTask(Map<String, Object> parameters) throws Exception {
        // 模拟产品爬取任务
        Integer catalogId = (Integer) parameters.get("catalogId");
        String categoryName = (String) parameters.get("categoryName");
        
        // 这里应该调用实际的爬取服务
        logger.info("执行产品爬取任务: catalogId={}, categoryName={}", catalogId, categoryName);
        
        // 模拟处理时间
        Thread.sleep(5000);
        
        // 模拟处理结果
        int processedCount = (int) (Math.random() * 100) + 50;
        return new TaskResult(true, "产品爬取完成", processedCount);
    }
    
    private TaskResult executeDataExportTask(Map<String, Object> parameters) throws Exception {
        // 模拟数据导出任务
        Thread.sleep(3000);
        return new TaskResult(true, "数据导出完成", 1);
    }
    
    /**
     * 处理任务失败
     */
    private void handleTaskFailure(CrawlerTask task, Exception error) {
        String taskId = task.getTaskId();
        
        task.incrementRetryCount();
        task.setLastError(error.getMessage());
        
        if (task.getRetryCount() < maxRetryAttempts) {
            // 重新调度任务
            task.setStatus(TaskStatus.PENDING);
            task.setPriority(TaskPriority.HIGH); // 提高重试任务的优先级
            taskQueue.offer(task);
            
            logger.info("任务将重试: {} (第 {} 次重试)", taskId, task.getRetryCount());
            
        } else {
            // 任务最终失败
            task.setStatus(TaskStatus.FAILED);
            task.setEndTime(LocalDateTime.now());
            
            activeTasks.remove(taskId);
            failedTasks.put(taskId, task);
            totalTasksFailed.incrementAndGet();
            
            logger.error("任务最终失败: {} 重试次数: {}", taskId, task.getRetryCount());
            
            // 记录错误指标
            metricsCollector.recordError("TASK_FAILURE", task.getTaskType());
        }
    }
    
    /**
     * 清理任务
     */
    private void cleanupTasks() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
            
            // 清理过期的完成任务
            int completedCleaned = cleanupTaskMap(completedTasks, cutoffTime);
            
            // 清理过期的失败任务
            int failedCleaned = cleanupTaskMap(failedTasks, cutoffTime);
            
            if (completedCleaned > 0 || failedCleaned > 0) {
                logger.info("任务清理完成: 已完成任务 {} 个, 失败任务 {} 个", 
                    completedCleaned, failedCleaned);
            }
            
        } catch (Exception e) {
            logger.error("任务清理时发生错误", e);
        }
    }
    
    private int cleanupTaskMap(Map<String, CrawlerTask> taskMap, LocalDateTime cutoffTime) {
        int cleaned = 0;
        Iterator<Map.Entry<String, CrawlerTask>> iterator = taskMap.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, CrawlerTask> entry = iterator.next();
            CrawlerTask task = entry.getValue();
            
            if (task.getEndTime() != null && task.getEndTime().isBefore(cutoffTime)) {
                iterator.remove();
                cleaned++;
            }
        }
        
        return cleaned;
    }
    
    /**
     * 健康检查
     */
    private void performHealthCheck() {
        try {
            // 检查线程池健康状态
            ThreadPoolExecutor executor = taskExecutor.getThreadPoolExecutor();
            int activeCount = executor.getActiveCount();
            int poolSize = executor.getPoolSize();
            long completedTaskCount = executor.getCompletedTaskCount();
            
            logger.debug("线程池状态: 活跃线程 {}/{}, 已完成任务 {}, 队列大小 {}", 
                activeCount, poolSize, completedTaskCount, taskQueue.size());
            
            // 检查是否有长时间运行的任务
            LocalDateTime now = LocalDateTime.now();
            activeTasks.values().stream()
                .filter(task -> task.getStartTime() != null)
                .filter(task -> task.getStartTime().plusMinutes(30).isBefore(now))
                .forEach(task -> {
                    logger.warn("发现长时间运行的任务: {} 已运行 {} 分钟", 
                        task.getTaskId(), 
                        java.time.Duration.between(task.getStartTime(), now).toMinutes());
                });
                
        } catch (Exception e) {
            logger.error("健康检查时发生错误", e);
        }
    }
    
    /**
     * 获取调度器统计信息
     */
    public SchedulerStats getStats() {
        return new SchedulerStats(
            totalTasksScheduled.get(),
            totalTasksCompleted.get(),
            totalTasksFailed.get(),
            currentlyRunningTasks.get(),
            taskQueue.size(),
            activeTasks.size(),
            completedTasks.size(),
            failedTasks.size(),
            isRunning,
            isPaused,
            taskExecutor != null ? taskExecutor.getActiveCount() : 0,
            taskExecutor != null ? taskExecutor.getThreadPoolExecutor().getPoolSize() : 0
        );
    }
    
    // 初始化和辅助方法
    
    private void initializeThreadPool() {
        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(corePoolSize);
        taskExecutor.setMaxPoolSize(maxPoolSize);
        taskExecutor.setQueueCapacity(queueCapacity);
        taskExecutor.setKeepAliveSeconds((int) keepAliveSeconds);
        taskExecutor.setThreadNamePrefix("CrawlerTask-");
        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        taskExecutor.initialize();
    }
    
    private String generateTaskId() {
        return "TASK_" + System.currentTimeMillis() + "_" + 
               Integer.toHexString(ThreadLocalRandom.current().nextInt());
    }
    
    // 内部类定义
    
    /**
     * 任务优先级比较器
     */
    private static class TaskPriorityComparator implements Comparator<CrawlerTask> {
        @Override
        public int compare(CrawlerTask t1, CrawlerTask t2) {
            // 优先级高的排在前面
            int priorityComparison = t2.getPriority().compareTo(t1.getPriority());
            if (priorityComparison != 0) {
                return priorityComparison;
            }
            
            // 优先级相同时，提交时间早的排在前面
            return t1.getCreateTime().compareTo(t2.getCreateTime());
        }
    }
    
    /**
     * 任务优先级枚举
     */
    public enum TaskPriority {
        LOW(1, "低"),
        MEDIUM(2, "中"),
        HIGH(3, "高"),
        CRITICAL(4, "紧急");
        
        private final int level;
        private final String description;
        
        TaskPriority(int level, String description) {
            this.level = level;
            this.description = description;
        }
        
        public int getLevel() { return level; }
        public String getDescription() { return description; }
    }
    
    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        PENDING("待执行"),
        RUNNING("执行中"),
        COMPLETED("已完成"),
        FAILED("失败"),
        CANCELLED("已取消");
        
        private final String description;
        
        TaskStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * 任务定义类
     */
    public static class TaskDefinition {
        private final String taskType;
        private final String description;
        private final Map<String, Object> parameters;
        private final TaskPriority priority;
        
        public TaskDefinition(String taskType, String description, 
                            Map<String, Object> parameters, TaskPriority priority) {
            this.taskType = taskType;
            this.description = description;
            this.parameters = parameters;
            this.priority = priority;
        }
        
        // Getters
        public String getTaskType() { return taskType; }
        public String getDescription() { return description; }
        public Map<String, Object> getParameters() { return parameters; }
        public TaskPriority getPriority() { return priority; }
    }
    
    /**
     * 任务结果类
     */
    public static class TaskResult {
        private final boolean success;
        private final String message;
        private final int processedCount;
        private final Map<String, Object> data;
        
        public TaskResult(boolean success, String message, int processedCount) {
            this(success, message, processedCount, new HashMap<>());
        }
        
        public TaskResult(boolean success, String message, int processedCount, Map<String, Object> data) {
            this.success = success;
            this.message = message;
            this.processedCount = processedCount;
            this.data = data;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getProcessedCount() { return processedCount; }
        public Map<String, Object> getData() { return data; }
    }
    
    /**
     * 爬虫任务类
     */
    public static class CrawlerTask {
        private final String taskId;
        private final String taskType;
        private final String description;
        private final Map<String, Object> parameters;
        private final LocalDateTime createTime;
        
        private TaskPriority priority;
        private TaskStatus status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private TaskResult result;
        private int retryCount = 0;
        private String lastError;
        
        public CrawlerTask(String taskId, String taskType, String description, 
                         Map<String, Object> parameters, TaskPriority priority,
                         LocalDateTime createTime, TaskStatus status) {
            this.taskId = taskId;
            this.taskType = taskType;
            this.description = description;
            this.parameters = parameters;
            this.priority = priority;
            this.createTime = createTime;
            this.status = status;
        }
        
        // Getters and Setters
        public String getTaskId() { return taskId; }
        public String getTaskType() { return taskType; }
        public String getDescription() { return description; }
        public Map<String, Object> getParameters() { return parameters; }
        public TaskPriority getPriority() { return priority; }
        public void setPriority(TaskPriority priority) { this.priority = priority; }
        public TaskStatus getStatus() { return status; }
        public void setStatus(TaskStatus status) { this.status = status; }
        public LocalDateTime getCreateTime() { return createTime; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public TaskResult getResult() { return result; }
        public void setResult(TaskResult result) { this.result = result; }
        public int getRetryCount() { return retryCount; }
        public void incrementRetryCount() { this.retryCount++; }
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
        
        /**
         * 获取任务执行时间（毫秒）
         */
        public long getExecutionTimeMs() {
            if (startTime == null || endTime == null) {
                return 0;
            }
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
    }
    
    /**
     * 调度器统计信息
     */
    public static class SchedulerStats {
        private final long totalScheduled;
        private final long totalCompleted;
        private final long totalFailed;
        private final int currentlyRunning;
        private final int queueSize;
        private final int activeTasksCount;
        private final int completedTasksCount;
        private final int failedTasksCount;
        private final boolean isRunning;
        private final boolean isPaused;
        private final int threadPoolActiveCount;
        private final int threadPoolSize;
        
        public SchedulerStats(long totalScheduled, long totalCompleted, long totalFailed,
                            int currentlyRunning, int queueSize, int activeTasksCount,
                            int completedTasksCount, int failedTasksCount,
                            boolean isRunning, boolean isPaused,
                            int threadPoolActiveCount, int threadPoolSize) {
            this.totalScheduled = totalScheduled;
            this.totalCompleted = totalCompleted;
            this.totalFailed = totalFailed;
            this.currentlyRunning = currentlyRunning;
            this.queueSize = queueSize;
            this.activeTasksCount = activeTasksCount;
            this.completedTasksCount = completedTasksCount;
            this.failedTasksCount = failedTasksCount;
            this.isRunning = isRunning;
            this.isPaused = isPaused;
            this.threadPoolActiveCount = threadPoolActiveCount;
            this.threadPoolSize = threadPoolSize;
        }
        
        // Getters
        public long getTotalScheduled() { return totalScheduled; }
        public long getTotalCompleted() { return totalCompleted; }
        public long getTotalFailed() { return totalFailed; }
        public int getCurrentlyRunning() { return currentlyRunning; }
        public int getQueueSize() { return queueSize; }
        public int getActiveTasksCount() { return activeTasksCount; }
        public int getCompletedTasksCount() { return completedTasksCount; }
        public int getFailedTasksCount() { return failedTasksCount; }
        public boolean isRunning() { return isRunning; }
        public boolean isPaused() { return isPaused; }
        public int getThreadPoolActiveCount() { return threadPoolActiveCount; }
        public int getThreadPoolSize() { return threadPoolSize; }
        
        public double getSuccessRate() {
            return totalScheduled > 0 ? (double) totalCompleted / totalScheduled * 100 : 0;
        }
        
        public double getFailureRate() {
            return totalScheduled > 0 ? (double) totalFailed / totalScheduled * 100 : 0;
        }
    }
}