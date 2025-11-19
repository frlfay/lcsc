package com.lcsc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcsc.entity.TaskLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 基于Redis的任务日志服务
 * 使用Redis存储任务日志，自动过期清理
 * 
 * @author lcsc-crawler
 * @since 2025-09-07
 */
@Service
public class RedisTaskLogService {

    private static final Logger logger = LoggerFactory.getLogger(RedisTaskLogService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // Redis键前缀
    private static final String LOG_KEY_PREFIX = "task_logs:";
    private static final String ACTIVE_TASKS_KEY = "active_tasks";
    
    // 默认过期时间（1小时）
    private static final long DEFAULT_EXPIRE_HOURS = 1;
    
    // 任务序列号管理
    private final Map<String, AtomicInteger> taskSequenceCounters = new ConcurrentHashMap<>();

    /**
     * 记录任务日志到Redis
     */
    public void logTask(String taskId, String level, String step, String message, Integer progress) {
        logTaskWithDetails(taskId, null, null, level, step, message, progress, null, null);
    }

    /**
     * 详细的任务日志记录方法
     */
    public void logTaskWithDetails(String taskId, String parentTaskId, String taskType,
                                   String level, String step, String message, Integer progress,
                                   Object metadata, String errorCode) {
        try {
            // 获取序列号
            int sequenceOrder = getNextSequence(taskId);
            
            // 创建日志对象
            TaskLog taskLog = new TaskLog();
            taskLog.setTaskId(taskId);
            taskLog.setParentTaskId(parentTaskId);
            taskLog.setTaskType(taskType != null ? taskType : "UNKNOWN");
            taskLog.setLevel(level);
            taskLog.setStep(step);
            taskLog.setMessage(message);
            taskLog.setProgress(progress);
            taskLog.setSequenceOrder(sequenceOrder);
            taskLog.setCreateTime(LocalDateTime.now());
            taskLog.setErrorCode(errorCode);
            
            // 设置元数据
            if (metadata != null) {
                String metadataJson = objectMapper.writeValueAsString(metadata);
                taskLog.setMetadata(metadataJson);
            }
            
            // 生成唯一ID
            String logId = taskId + ":" + sequenceOrder + ":" + System.currentTimeMillis();
            taskLog.setId((long)(logId.hashCode() & 0x7fffffff)); // 转为正数Long类型
            
            // 存储到Redis
            String logKey = LOG_KEY_PREFIX + logId;
            redisTemplate.opsForValue().set(logKey, taskLog, DEFAULT_EXPIRE_HOURS, TimeUnit.HOURS);
            
            // 添加到有序集合中（按时间排序）
            String timeBasedKey = LOG_KEY_PREFIX + "timeline";
            double score = System.currentTimeMillis();
            redisTemplate.opsForZSet().add(timeBasedKey, logKey, score);
            // 设置时间线的过期时间
            redisTemplate.expire(timeBasedKey, DEFAULT_EXPIRE_HOURS, TimeUnit.HOURS);
            
            // 更新活跃任务状态
            updateActiveTask(taskId, taskLog);
            
            logger.debug("任务日志已存储到Redis: taskId={}, step={}, sequence={}, key={}", 
                        taskId, step, sequenceOrder, logKey);
            
        } catch (Exception e) {
            logger.error("存储任务日志到Redis失败: taskId={}, message={}", taskId, message, e);
        }
    }

    /**
     * 获取任务的下一个序列号
     */
    private int getNextSequence(String taskId) {
        return taskSequenceCounters.computeIfAbsent(taskId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * 更新活跃任务状态
     */
    private void updateActiveTask(String taskId, TaskLog taskLog) {
        try {
            String activeTaskKey = ACTIVE_TASKS_KEY + ":" + taskId;
            redisTemplate.opsForValue().set(activeTaskKey, taskLog, DEFAULT_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.error("更新活跃任务状态失败: taskId={}", taskId, e);
        }
    }

    /**
     * 分页获取任务日志
     */
    public Map<String, Object> getTaskLogsPage(int page, int size, String taskId, String level) {
        try {
            String timeBasedKey = LOG_KEY_PREFIX + "timeline";
            
            // 计算分页参数
            long start = (long) (page - 1) * size;
            long end = start + size - 1;
            
            // 从有序集合中获取最新的日志键（按时间倒序）
            Set<Object> logKeys = redisTemplate.opsForZSet().reverseRange(timeBasedKey, start, end);
            if (logKeys == null || logKeys.isEmpty()) {
                return createEmptyPageResult(page, size);
            }
            
            // 批量获取日志详情
            List<String> logKeysList = logKeys.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
            List<Object> logs = redisTemplate.opsForValue().multiGet(logKeysList);
            
            // 过滤和转换日志
            logger.debug("获取到 {} 个日志对象，其中非空的有 {} 个", 
                    logs.size(), logs.stream().mapToInt(obj -> obj != null ? 1 : 0).sum());
            
            List<TaskLog> filteredLogs = logs.stream()
                    .filter(Objects::nonNull)
                    .peek(obj -> logger.debug("日志对象类型: {}", obj.getClass().getName()))
                    .map(obj -> {
                        try {
                            if (obj instanceof LinkedHashMap) {
                                // 手动转换LinkedHashMap到TaskLog
                                return convertMapToTaskLog((LinkedHashMap<String, Object>) obj);
                            } else {
                                return (TaskLog) obj;
                            }
                        } catch (Exception e) {
                            logger.error("日志对象转换失败: {} -> {}", obj.getClass(), e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(log -> filterLog(log, taskId, level))
                    .collect(Collectors.toList());
            
            // 获取总数（估算）
            Long total = redisTemplate.opsForZSet().count(timeBasedKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            
            return createPageResult(filteredLogs, page, size, total != null ? total.intValue() : 0);
            
        } catch (Exception e) {
            logger.error("从Redis获取任务日志失败", e);
            return createEmptyPageResult(page, size);
        }
    }

    /**
     * 过滤日志条件
     */
    private boolean filterLog(TaskLog log, String taskId, String level) {
        if (taskId != null && !taskId.isEmpty() && !taskId.equals(log.getTaskId())) {
            return false;
        }
        if (level != null && !level.isEmpty() && !level.equals(log.getLevel())) {
            return false;
        }
        return true;
    }

    /**
     * 获取活跃任务状态
     */
    public Map<String, TaskLog> getActiveTasksStatus() {
        try {
            Map<String, TaskLog> activeTasks = new HashMap<>();
            
            // 扫描所有活跃任务键
            Set<String> keys = redisTemplate.keys(ACTIVE_TASKS_KEY + ":*");
            if (keys != null && !keys.isEmpty()) {
                List<Object> values = redisTemplate.opsForValue().multiGet(keys);
                
                for (int i = 0; i < keys.size() && i < values.size(); i++) {
                    Object value = values.get(i);
                    if (value instanceof TaskLog) {
                        TaskLog taskLog = (TaskLog) value;
                        String taskId = taskLog.getTaskId();
                        activeTasks.put(taskId, taskLog);
                    }
                }
            }
            
            return activeTasks;
            
        } catch (Exception e) {
            logger.error("获取活跃任务状态失败", e);
            return new HashMap<>();
        }
    }

    /**
     * 获取指定任务的最新日志
     */
    public List<TaskLog> getLatestTaskLogs(String taskId, int limit) {
        try {
            String timeBasedKey = LOG_KEY_PREFIX + "timeline";
            
            // 获取最新的日志键
            Set<Object> logKeys = redisTemplate.opsForZSet().reverseRange(timeBasedKey, 0, limit - 1);
            if (logKeys == null || logKeys.isEmpty()) {
                return new ArrayList<>();
            }
            
            // 批量获取日志详情并过滤
            List<Object> logKeysList = new ArrayList<>(logKeys);
            List<Object> logs = redisTemplate.opsForValue().multiGet(logKeysList.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList()));
            
            return logs.stream()
                    .filter(Objects::nonNull)
                    .map(obj -> (TaskLog) obj)
                    .filter(log -> taskId.equals(log.getTaskId()))
                    .limit(limit)
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            logger.error("获取任务最新日志失败: taskId={}", taskId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取任务进度
     */
    public Integer getTaskProgress(String taskId) {
        try {
            List<TaskLog> latestLogs = getLatestTaskLogs(taskId, 1);
            if (!latestLogs.isEmpty()) {
                TaskLog latestLog = latestLogs.get(0);
                return latestLog.getProgress();
            }
        } catch (Exception e) {
            logger.error("获取任务进度失败: taskId={}", taskId, e);
        }
        return 0;
    }

    /**
     * 清理过期日志（手动清理，Redis会自动过期）
     */
    public int cleanExpiredLogs(int daysToKeep) {
        try {
            String timeBasedKey = LOG_KEY_PREFIX + "timeline";
            
            // 计算过期时间点
            long expiredBefore = System.currentTimeMillis() - (daysToKeep * 24L * 60 * 60 * 1000);
            
            // 获取过期的日志键
            Set<Object> expiredKeys = redisTemplate.opsForZSet().rangeByScore(timeBasedKey, 
                    Double.NEGATIVE_INFINITY, expiredBefore);
            
            int deletedCount = 0;
            if (expiredKeys != null && !expiredKeys.isEmpty()) {
                // 删除过期的日志数据
                List<String> keysToDelete = expiredKeys.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
                redisTemplate.delete(keysToDelete);
                
                // 从时间线中删除过期的键
                redisTemplate.opsForZSet().removeRangeByScore(timeBasedKey, 
                        Double.NEGATIVE_INFINITY, expiredBefore);
                
                deletedCount = expiredKeys.size();
            }
            
            logger.info("清理过期日志完成，删除了 {} 条记录", deletedCount);
            return deletedCount;
            
        } catch (Exception e) {
            logger.error("清理过期日志失败", e);
            return 0;
        }
    }

    /**
     * 创建空的分页结果
     */
    private Map<String, Object> createEmptyPageResult(int page, int size) {
        Map<String, Object> result = new HashMap<>();
        result.put("records", new ArrayList<>());
        result.put("total", 0);
        result.put("size", size);
        result.put("current", page);
        result.put("pages", 0);
        return result;
    }

    /**
     * 创建分页结果
     */
    private Map<String, Object> createPageResult(List<TaskLog> records, int page, int size, int total) {
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("size", size);
        result.put("current", page);
        result.put("pages", (total + size - 1) / size);
        return result;
    }

    /**
     * 记录任务开始日志
     */
    public void logTaskStart(String taskId, String taskType, Map<String, Object> details) {
        logTaskWithDetails(taskId, null, taskType, "INFO", "INIT", 
            "任务开始执行 - 类型: " + taskType, 0, details, null);
    }

    /**
     * 记录任务完成日志
     */
    public void logTaskCompleted(String taskId, Object result) {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("result", result);
        logTaskWithDetails(taskId, null, null, "SUCCESS", "COMPLETED", 
            "任务执行完成", 100, resultData, null);
    }

    /**
     * 记录任务失败日志
     */
    public void logTaskFailed(String taskId, String errorMessage) {
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("error", errorMessage);
        logTaskWithDetails(taskId, null, null, "ERROR", "FAILED", 
            "任务执行失败: " + errorMessage, null, errorData, "TASK_FAILED");
    }

    /**
     * 将LinkedHashMap转换为TaskLog对象
     */
    @SuppressWarnings("unchecked")
    private TaskLog convertMapToTaskLog(LinkedHashMap<String, Object> map) {
        try {
            TaskLog taskLog = new TaskLog();
            
            // 基础字段
            if (map.get("id") != null) {
                taskLog.setId(((Number) map.get("id")).longValue());
            }
            taskLog.setTaskId((String) map.get("taskId"));
            taskLog.setParentTaskId((String) map.get("parentTaskId"));
            taskLog.setTaskType((String) map.get("taskType"));
            taskLog.setLevel((String) map.get("level"));
            taskLog.setStep((String) map.get("step"));
            taskLog.setMessage((String) map.get("message"));
            
            if (map.get("progress") != null) {
                taskLog.setProgress(((Number) map.get("progress")).intValue());
            }
            if (map.get("sequenceOrder") != null) {
                taskLog.setSequenceOrder(((Number) map.get("sequenceOrder")).intValue());
            }
            
            taskLog.setExtraData((String) map.get("extraData"));
            taskLog.setMetadata((String) map.get("metadata"));
            taskLog.setErrorCode((String) map.get("errorCode"));
            
            // 处理createTime - Redis中可能存储为数组格式
            Object createTimeObj = map.get("createTime");
            if (createTimeObj instanceof List) {
                List<Integer> timeArray = (List<Integer>) createTimeObj;
                if (timeArray.size() >= 6) {
                    LocalDateTime createTime = LocalDateTime.of(
                        timeArray.get(0), // year
                        timeArray.get(1), // month
                        timeArray.get(2), // day
                        timeArray.get(3), // hour
                        timeArray.get(4), // minute
                        timeArray.get(5), // second
                        timeArray.size() > 6 ? timeArray.get(6) : 0 // nano
                    );
                    taskLog.setCreateTime(createTime);
                }
            } else if (createTimeObj instanceof String) {
                taskLog.setCreateTime(LocalDateTime.parse((String) createTimeObj));
            }
            
            return taskLog;
        } catch (Exception e) {
            logger.error("转换LinkedHashMap到TaskLog失败", e);
            return null;
        }
    }
}