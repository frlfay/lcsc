package com.lcsc.service.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis队列服务
 * 基于Redis实现任务队列管理
 *
 * @author lcsc-crawler
 * @since 2025-09-03
 */
@Service
public class RedisQueueService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // 队列名称
    private static final String TASK_QUEUE = "lcsc:crawler:tasks";
    private static final String PROCESSING_QUEUE = "lcsc:crawler:processing";
    private static final String COMPLETED_QUEUE = "lcsc:crawler:completed";
    private static final String FAILED_QUEUE = "lcsc:crawler:failed";
    private static final String TASK_DATA = "lcsc:crawler:task:data:";

    // 任务状态
    public enum TaskStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, RETRY
    }

    // 任务类型
    public enum TaskType {
        CATALOG_FETCH,      // 获取目录
        PACKAGE_FETCH,      // 获取封装
        PRODUCT_FETCH       // 获取产品
    }

    /**
     * 创建爬虫任务
     */
    public String createTask(TaskType taskType, Map<String, Object> taskParams) {
        try {
            String taskId = generateTaskId();
            System.out.println("开始创建任务: " + taskId + ", 类型: " + taskType);
            
            CrawlerTask task = new CrawlerTask();
            task.setTaskId(taskId);
            task.setTaskType(taskType);
            task.setParams(taskParams);
            task.setStatus(TaskStatus.PENDING);
            task.setCreatedAt(LocalDateTime.now());
            task.setRetryCount(0);
            task.setPriority(1); // 默认优先级
            System.out.println("任务对象创建完成: " + taskId);
            
            // 保存任务数据
            String taskDataKey = TASK_DATA + taskId;
            System.out.println("开始保存任务数据到Redis, key: " + taskDataKey);
            redisTemplate.opsForValue().set(taskDataKey, task, 24, TimeUnit.HOURS);
            System.out.println("任务数据保存完成: " + taskDataKey);
            
            // 添加到待处理队列（使用优先级队列）
            double score = System.currentTimeMillis() + (10 - task.getPriority()) * 1000;
            System.out.println("开始添加任务到队列: " + TASK_QUEUE + ", taskId: " + taskId + ", score: " + score);
            Boolean addResult = redisTemplate.opsForZSet().add(TASK_QUEUE, taskId, score);
            System.out.println("任务添加到队列结果: " + addResult + ", taskId: " + taskId);
            
            // 验证任务是否在队列中
            Long queueSize = redisTemplate.opsForZSet().count(TASK_QUEUE, 0, Double.MAX_VALUE);
            System.out.println("队列当前大小: " + queueSize);
            
            System.out.println("创建任务: " + taskId + ", 类型: " + taskType);
            return taskId;
            
        } catch (Exception e) {
            System.err.println("创建任务异常: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("创建任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取下一个待处理任务
     */
    public CrawlerTask getNextTask() {
        try {
            // 从优先级队列获取最高优先级的任务
            Set<Object> taskIds = redisTemplate.opsForZSet().range(TASK_QUEUE, 0, 0);
            
            if (taskIds == null || taskIds.isEmpty()) {
                return null;
            }
            
            String taskId = (String) taskIds.iterator().next();
            
            // 移除任务从待处理队列
            redisTemplate.opsForZSet().remove(TASK_QUEUE, taskId);
            
            // 获取任务数据
            String taskDataKey = TASK_DATA + taskId;
            Object taskData = redisTemplate.opsForValue().get(taskDataKey);
            CrawlerTask task = convertToTask(taskData);
            
            if (task != null) {
                // 移动到处理中队列
                task.setStatus(TaskStatus.PROCESSING);
                task.setStartedAt(LocalDateTime.now());
                
                redisTemplate.opsForValue().set(taskDataKey, task, 24, TimeUnit.HOURS);
                redisTemplate.opsForZSet().add(PROCESSING_QUEUE, taskId, System.currentTimeMillis());
                
                System.out.println("获取任务: " + taskId);
                return task;
            }
            
            return null;
            
        } catch (Exception e) {
            System.err.println("获取任务失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 完成任务
     */
    public void completeTask(String taskId, Map<String, Object> result) {
        try {
            String taskDataKey = TASK_DATA + taskId;
            CrawlerTask task = convertToTask(redisTemplate.opsForValue().get(taskDataKey));
            
            if (task != null) {
                task.setStatus(TaskStatus.COMPLETED);
                task.setCompletedAt(LocalDateTime.now());
                task.setResult(result);
                
                // 更新任务数据
                redisTemplate.opsForValue().set(taskDataKey, task, 24, TimeUnit.HOURS);
                
                // 移动到完成队列
                redisTemplate.opsForZSet().remove(PROCESSING_QUEUE, taskId);
                redisTemplate.opsForZSet().add(COMPLETED_QUEUE, taskId, System.currentTimeMillis());
                
                System.out.println("任务完成: " + taskId);
            }
            
        } catch (Exception e) {
            System.err.println("完成任务失败: " + e.getMessage());
        }
    }

    /**
     * 任务失败处理
     */
    public void failTask(String taskId, String errorMessage) {
        try {
            String taskDataKey = TASK_DATA + taskId;
            CrawlerTask task = convertToTask(redisTemplate.opsForValue().get(taskDataKey));
            
            if (task != null) {
                task.setRetryCount(task.getRetryCount() + 1);
                task.setErrorMessage(errorMessage);
                
                // 检查是否需要重试
                if (task.getRetryCount() < 3) {
                    // 重新放入队列，降低优先级
                    task.setStatus(TaskStatus.RETRY);
                    double score = System.currentTimeMillis() + task.getRetryCount() * 10000; // 延迟重试
                    redisTemplate.opsForZSet().add(TASK_QUEUE, taskId, score);
                    
                    System.out.println("任务重试: " + taskId + ", 重试次数: " + task.getRetryCount());
                } else {
                    // 移动到失败队列
                    task.setStatus(TaskStatus.FAILED);
                    task.setCompletedAt(LocalDateTime.now());
                    redisTemplate.opsForZSet().add(FAILED_QUEUE, taskId, System.currentTimeMillis());
                    
                    System.out.println("任务失败: " + taskId + ", 错误: " + errorMessage);
                }
                
                // 更新任务数据
                redisTemplate.opsForValue().set(taskDataKey, task, 24, TimeUnit.HOURS);
                
                // 从处理队列移除
                redisTemplate.opsForZSet().remove(PROCESSING_QUEUE, taskId);
            }
            
        } catch (Exception e) {
            System.err.println("处理任务失败失败: " + e.getMessage());
        }
    }

    /**
     * 获取队列状态
     */
    public Map<String, Object> getQueueStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            status.put("pending", redisTemplate.opsForZSet().count(TASK_QUEUE, 0, Double.MAX_VALUE));
            status.put("processing", redisTemplate.opsForZSet().count(PROCESSING_QUEUE, 0, Double.MAX_VALUE));
            status.put("completed", redisTemplate.opsForZSet().count(COMPLETED_QUEUE, 0, Double.MAX_VALUE));
            status.put("failed", redisTemplate.opsForZSet().count(FAILED_QUEUE, 0, Double.MAX_VALUE));
            status.put("timestamp", LocalDateTime.now());
            
            return status;
            
        } catch (Exception e) {
            System.err.println("获取队列状态失败: " + e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 获取任务详情
     */
    public CrawlerTask getTask(String taskId) {
        try {
            String taskDataKey = TASK_DATA + taskId;
            return convertToTask(redisTemplate.opsForValue().get(taskDataKey));
            
        } catch (Exception e) {
            System.err.println("获取任务详情失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 清理已完成的任务
     */
    public void cleanupCompletedTasks(int hoursOld) {
        try {
            long cutoffTime = System.currentTimeMillis() - (hoursOld * 60 * 60 * 1000);
            
            // 清理完成队列
            Set<Object> completedTasks = redisTemplate.opsForZSet().rangeByScore(COMPLETED_QUEUE, 0, cutoffTime);
            if (completedTasks != null && !completedTasks.isEmpty()) {
                for (Object taskId : completedTasks) {
                    redisTemplate.opsForZSet().remove(COMPLETED_QUEUE, taskId);
                    redisTemplate.delete(TASK_DATA + taskId);
                }
                System.out.println("清理已完成任务: " + completedTasks.size() + " 个");
            }
            
        } catch (Exception e) {
            System.err.println("清理任务失败: " + e.getMessage());
        }
    }

    /**
     * 批量创建产品获取任务
     */
    public List<String> createProductFetchTasks(List<Integer> catalogIds, List<String> packages) {
        List<String> taskIds = new ArrayList<>();
        
        for (Integer catalogId : catalogIds) {
            for (String packageName : packages) {
                Map<String, Object> params = new HashMap<>();
                params.put("catalogId", catalogId);
                params.put("packageName", packageName);
                params.put("currentPage", 1);
                params.put("pageSize", 25);
                
                String taskId = createTask(TaskType.PRODUCT_FETCH, params);
                taskIds.add(taskId);
            }
        }
        
        return taskIds;
    }

    /**
     * 暂停所有任务
     */
    public void pauseAllTasks() {
        try {
            // 标记暂停状态
            redisTemplate.opsForValue().set("lcsc:crawler:paused", true, 1, TimeUnit.HOURS);
            System.out.println("爬虫已暂停");
            
        } catch (Exception e) {
            System.err.println("暂停任务失败: " + e.getMessage());
        }
    }

    /**
     * 恢复所有任务
     */
    public void resumeAllTasks() {
        try {
            redisTemplate.delete("lcsc:crawler:paused");
            System.out.println("爬虫已恢复");
            
        } catch (Exception e) {
            System.err.println("恢复任务失败: " + e.getMessage());
        }
    }

    /**
     * 检查是否暂停
     */
    public boolean isPaused() {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().get("lcsc:crawler:paused"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 生成任务ID
     */
    private String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 将Redis获取的对象转换为CrawlerTask
     */
    private CrawlerTask convertToTask(Object taskData) {
        if (taskData == null) {
            System.out.println("convertToTask: taskData为空");
            return null;
        }
        
        System.out.println("convertToTask: taskData类型 = " + taskData.getClass().getName());
        
        if (taskData instanceof CrawlerTask) {
            System.out.println("convertToTask: 直接转换CrawlerTask");
            return (CrawlerTask) taskData;
        }
        
        // 如果是LinkedHashMap（由于禁用了类型信息），使用ObjectMapper转换
        try {
            System.out.println("convertToTask: 使用ObjectMapper转换，输入数据: " + taskData);
            CrawlerTask result = objectMapper.convertValue(taskData, CrawlerTask.class);
            System.out.println("convertToTask: 转换成功，任务ID = " + (result != null ? result.getTaskId() : "null"));
            return result;
        } catch (Exception e) {
            System.err.println("任务数据转换失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 爬虫任务数据结构
     */
    public static class CrawlerTask {
        private String taskId;
        private TaskType taskType;
        private TaskStatus status;
        private Map<String, Object> params;
        private Map<String, Object> result;
        private LocalDateTime createdAt;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private int retryCount;
        private int priority;
        private String errorMessage;

        // Getters and Setters
        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public TaskType getTaskType() {
            return taskType;
        }

        public void setTaskType(TaskType taskType) {
            this.taskType = taskType;
        }

        public TaskStatus getStatus() {
            return status;
        }

        public void setStatus(TaskStatus status) {
            this.status = status;
        }

        public Map<String, Object> getParams() {
            return params;
        }

        public void setParams(Map<String, Object> params) {
            this.params = params;
        }

        public Map<String, Object> getResult() {
            return result;
        }

        public void setResult(Map<String, Object> result) {
            this.result = result;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(LocalDateTime startedAt) {
            this.startedAt = startedAt;
        }

        public LocalDateTime getCompletedAt() {
            return completedAt;
        }

        public void setCompletedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 获取调试信息 - 检查Redis键是否存在以及样本数据
     */
    public Map<String, Object> getDebugInfo() {
        Map<String, Object> debug = new HashMap<>();
        
        try {
            // 检查Redis键是否存在
            debug.put("pendingQueueExists", redisTemplate.hasKey(TASK_QUEUE));
            debug.put("processingQueueExists", redisTemplate.hasKey(PROCESSING_QUEUE));
            debug.put("completedQueueExists", redisTemplate.hasKey(COMPLETED_QUEUE));
            debug.put("failedQueueExists", redisTemplate.hasKey(FAILED_QUEUE));
            
            // 获取样本任务ID
            Set<Object> pendingSamplesObj = redisTemplate.opsForZSet().range(TASK_QUEUE, 0, 2);
            Set<Object> processingSamplesObj = redisTemplate.opsForZSet().range(PROCESSING_QUEUE, 0, 2);
            Set<Object> completedSamplesObj = redisTemplate.opsForZSet().range(COMPLETED_QUEUE, 0, 2);
            Set<Object> failedSamplesObj = redisTemplate.opsForZSet().range(FAILED_QUEUE, 0, 2);
            
            // 转换为String集合
            Set<String> pendingSamples = convertToStringSet(pendingSamplesObj);
            Set<String> processingSamples = convertToStringSet(processingSamplesObj);
            Set<String> completedSamples = convertToStringSet(completedSamplesObj);
            Set<String> failedSamples = convertToStringSet(failedSamplesObj);
            
            debug.put("pendingSamples", pendingSamples);
            debug.put("processingSamples", processingSamples);
            debug.put("completedSamples", completedSamples);
            debug.put("failedSamples", failedSamples);
            
            // 尝试获取一个完整的任务详情
            if (!completedSamples.isEmpty()) {
                String sampleTaskId = completedSamples.iterator().next();
                CrawlerTask sampleTask = getTask(sampleTaskId);
                debug.put("sampleTaskDetail", sampleTask);
            }
            
            debug.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            debug.put("error", "获取调试信息失败: " + e.getMessage());
        }
        
        return debug;
    }
    
    /**
     * 将Object集合转换为String集合
     */
    private Set<String> convertToStringSet(Set<Object> objectSet) {
        Set<String> stringSet = new HashSet<>();
        if (objectSet != null) {
            for (Object obj : objectSet) {
                if (obj != null) {
                    stringSet.add(obj.toString());
                }
            }
        }
        return stringSet;
    }
}