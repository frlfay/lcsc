package com.lcsc.service.crawler;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lcsc.entity.CrawlerTask;
import com.lcsc.mapper.CrawlerTaskMapper;
import com.lcsc.service.crawler.RedisQueueService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务持久化服务
 * 负责将Redis中的任务数据持久化到数据库
 * 
 * @author lcsc-crawler
 * @since 2025-09-08
 */
@Service
public class TaskPersistenceService extends ServiceImpl<CrawlerTaskMapper, CrawlerTask> {

    private static final Logger logger = LoggerFactory.getLogger(TaskPersistenceService.class);

    @Autowired
    private RedisQueueService queueService;

    @Autowired
    private ObjectMapper objectMapper;

    // 已持久化的任务ID缓存（避免重复持久化）
    private final Set<String> persistedTaskIds = ConcurrentHashMap.newKeySet();

    /**
     * 定时持久化已完成的任务
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    @Transactional(rollbackFor = Exception.class)
    public void persistCompletedTasks() {
        try {
            logger.info("开始执行任务持久化作业...");
            
            int persistedCount = 0;
            
            // 持久化已完成的任务
            persistedCount += persistTasksByStatus("COMPLETED");
            
            // 持久化失败的任务
            persistedCount += persistTasksByStatus("FAILED");
            
            logger.info("任务持久化完成，共持久化 {} 个任务", persistedCount);
            
        } catch (Exception e) {
            logger.error("任务持久化失败", e);
        }
    }

    /**
     * 根据状态持久化任务
     */
    private int persistTasksByStatus(String status) {
        try {
            // 从Redis获取指定状态的任务样本
            Map<String, Object> debugInfo = queueService.getDebugInfo();
            Set<String> taskIds = getTaskIdsByStatus(debugInfo, status);
            
            int persistedCount = 0;
            
            for (String taskId : taskIds) {
                // 检查是否已经持久化
                if (persistedTaskIds.contains(taskId)) {
                    continue;
                }
                
                // 检查数据库中是否已存在
                if (baseMapper.countByTaskId(taskId) > 0) {
                    persistedTaskIds.add(taskId);
                    continue;
                }
                
                // 从Redis获取任务详情并持久化
                RedisQueueService.CrawlerTask redisTask = queueService.getTask(taskId);
                if (redisTask != null) {
                    CrawlerTask dbTask = convertToDbTask(redisTask);
                    if (dbTask != null) {
                        this.save(dbTask);
                        persistedTaskIds.add(taskId);
                        persistedCount++;
                        
                        logger.debug("成功持久化任务: {}", taskId);
                    }
                }
            }
            
            return persistedCount;
            
        } catch (Exception e) {
            logger.error("按状态持久化任务失败: status={}", status, e);
            return 0;
        }
    }

    /**
     * 从调试信息中提取指定状态的任务ID
     */
    @SuppressWarnings("unchecked")
    private Set<String> getTaskIdsByStatus(Map<String, Object> debugInfo, String status) {
        Set<String> taskIds = new HashSet<>();
        
        try {
            String sampleKey = status.toLowerCase() + "Samples";
            Object samples = debugInfo.get(sampleKey);
            
            if (samples instanceof Set) {
                taskIds.addAll((Set<String>) samples);
            }
            
        } catch (Exception e) {
            logger.warn("提取任务ID失败: status={}", status, e);
        }
        
        return taskIds;
    }

    /**
     * 将Redis任务对象转换为数据库任务对象
     */
    private CrawlerTask convertToDbTask(RedisQueueService.CrawlerTask redisTask) {
        try {
            CrawlerTask dbTask = new CrawlerTask();
            
            dbTask.setTaskId(redisTask.getTaskId());
            dbTask.setTaskType(redisTask.getTaskType() != null ? redisTask.getTaskType().name() : "UNKNOWN");
            dbTask.setTaskStatus(redisTask.getStatus() != null ? redisTask.getStatus().name() : "UNKNOWN");
            dbTask.setPriority(redisTask.getPriority());
            dbTask.setRetryCount(redisTask.getRetryCount());
            dbTask.setCreatedAt(redisTask.getCreatedAt());
            dbTask.setStartedAt(redisTask.getStartedAt());
            dbTask.setCompletedAt(redisTask.getCompletedAt());
            dbTask.setErrorMessage(redisTask.getErrorMessage());
            
            // 设置任务参数和结果
            if (redisTask.getParams() != null) {
                dbTask.setTaskParams(redisTask.getParams());
            }
            if (redisTask.getResult() != null) {
                dbTask.setTaskResult(redisTask.getResult());
            }
            
            // 计算执行时长
            if (redisTask.getStartedAt() != null && redisTask.getCompletedAt() != null) {
                long durationMs = java.time.Duration.between(
                    redisTask.getStartedAt(), 
                    redisTask.getCompletedAt()
                ).toMillis();
                dbTask.setExecutionDurationMs(durationMs);
            }
            
            return dbTask;
            
        } catch (Exception e) {
            logger.error("转换任务对象失败: taskId={}", redisTask.getTaskId(), e);
            return null;
        }
    }

    /**
     * 手动触发持久化（提供给管理接口调用）
     */
    public Map<String, Object> triggerPersistence() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            
            int completedCount = persistTasksByStatus("COMPLETED");
            int failedCount = persistTasksByStatus("FAILED");
            
            long duration = System.currentTimeMillis() - startTime;
            
            result.put("success", true);
            result.put("completedTasksPersisted", completedCount);
            result.put("failedTasksPersisted", failedCount);
            result.put("totalPersisted", completedCount + failedCount);
            result.put("durationMs", duration);
            result.put("timestamp", LocalDateTime.now());
            
            logger.info("手动触发持久化完成: {}", result);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            logger.error("手动触发持久化失败", e);
        }
        
        return result;
    }

    /**
     * 清理过期任务（保留最近30天的数据）
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredTasks() {
        try {
            LocalDateTime expireTime = LocalDateTime.now().minusDays(30);
            int deletedCount = baseMapper.deleteExpiredTasks(expireTime);
            
            logger.info("清理过期任务完成，删除了 {} 个任务记录", deletedCount);
            
        } catch (Exception e) {
            logger.error("清理过期任务失败", e);
        }
    }

    /**
     * 检查长时间运行的任务
     */
    @Scheduled(fixedRate = 600000) // 10分钟检查一次
    public void checkLongRunningTasks() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusHours(2); // 超过2小时的任务
            List<CrawlerTask> longRunningTasks = baseMapper.selectLongRunningTasks(threshold);
            
            if (!longRunningTasks.isEmpty()) {
                logger.warn("发现 {} 个长时间运行的任务", longRunningTasks.size());
                
                for (CrawlerTask task : longRunningTasks) {
                    logger.warn("长时间运行任务: taskId={}, startTime={}, type={}", 
                        task.getTaskId(), task.getStartedAt(), task.getTaskType());
                }
            }
            
        } catch (Exception e) {
            logger.error("检查长时间运行任务失败", e);
        }
    }

    /**
     * 获取持久化统计信息
     */
    public Map<String, Object> getPersistenceStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 最近24小时的统计
            LocalDateTime since = LocalDateTime.now().minusHours(24);
            Map<String, Object> execStats = baseMapper.selectExecutionStatistics(since);
            stats.putAll(execStats);
            
            // 已持久化任务数量
            stats.put("persistedTasksInCache", persistedTaskIds.size());
            
            // 最近完成的任务
            List<CrawlerTask> recentTasks = baseMapper.selectRecentCompletedTasks(since, 10);
            stats.put("recentCompletedTasks", recentTasks);
            
            stats.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            stats.put("error", "获取统计信息失败: " + e.getMessage());
            logger.error("获取持久化统计信息失败", e);
        }
        
        return stats;
    }

    /**
     * 清理持久化缓存
     */
    public void clearPersistedCache() {
        persistedTaskIds.clear();
        logger.info("已清理持久化任务ID缓存");
    }
}