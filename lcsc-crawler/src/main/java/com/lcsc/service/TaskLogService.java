package com.lcsc.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lcsc.entity.TaskLog;
import com.lcsc.mapper.TaskLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.lcsc.controller.CrawlerWebSocketController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务日志服务类
 * 提供任务日志记录、查询和实时推送功能
 * 支持层级任务、性能监控和可视化功能
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Service
public class TaskLogService extends ServiceImpl<TaskLogMapper, TaskLog> {

    private static final Logger logger = LoggerFactory.getLogger(TaskLogService.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private CrawlerWebSocketController webSocketController;

    @Autowired
    private ObjectMapper objectMapper;

    // 任务序列号管理
    private final Map<String, AtomicInteger> taskSequenceCounters = new ConcurrentHashMap<>();
    
    // 任务步骤开始时间记录
    private final Map<String, Map<String, LocalDateTime>> taskStepStartTimes = new ConcurrentHashMap<>();

    /**
     * 记录任务日志并实时推送
     */
    public void logTask(String taskId, String level, String step, String message, Integer progress) {
        logTaskWithSequence(taskId, null, null, level, step, message, progress, null, null);
    }

    /**
     * 增强的任务日志记录方法
     */
    public void logTaskWithSequence(String taskId, String parentTaskId, String taskType,
                                   String level, String step, String message, Integer progress,
                                   Object metadata, String errorCode) {
        try {
            // 获取序列号
            int sequenceOrder = getNextSequence(taskId);
            
            // 创建增强的日志记录
            TaskLog taskLog = new TaskLog(taskId, parentTaskId, taskType, level, step, message, progress, sequenceOrder);
            
            // 设置错误代码
            if (errorCode != null) {
                taskLog.setErrorCode(errorCode);
            }
            
            // 设置元数据
            if (metadata != null) {
                String metadataJson = objectMapper.writeValueAsString(metadata);
                taskLog.setMetadata(metadataJson);
            }
            
            // 保存到数据库
            this.save(taskLog);
            
            // 实时推送给前端
            pushLogToClient(taskLog);
            
            logger.debug("任务日志已记录: taskId={}, step={}, sequence={}, message={}", 
                        taskId, step, sequenceOrder, message);
            
        } catch (Exception e) {
            logger.error("记录任务日志失败: taskId={}, message={}", taskId, message, e);
        }
    }

    /**
     * 开始一个新的任务步骤
     */
    public TaskLog startStep(String taskId, String parentTaskId, String taskType, String step, String message) {
        return startStep(taskId, parentTaskId, taskType, step, message, null);
    }

    /**
     * 开始一个新的任务步骤（带进度）
     */
    public TaskLog startStep(String taskId, String parentTaskId, String taskType, 
                           String step, String message, Integer progress) {
        try {
            int sequenceOrder = getNextSequence(taskId);
            TaskLog taskLog = new TaskLog(taskId, parentTaskId, taskType, 
                                        TaskLog.LogLevel.INFO.getCode(), step, message, progress, sequenceOrder);
            
            // 记录步骤开始时间
            recordStepStartTime(taskId, step);
            
            this.save(taskLog);
            pushLogToClient(taskLog);
            
            logger.debug("开始任务步骤: taskId={}, step={}, sequence={}", taskId, step, sequenceOrder);
            return taskLog;
            
        } catch (Exception e) {
            logger.error("开始任务步骤失败: taskId={}, step={}", taskId, step, e);
            return null;
        }
    }

    /**
     * 完成一个任务步骤
     */
    public void completeStep(String taskId, String step, String message, Object result) {
        try {
            LocalDateTime startTime = getStepStartTime(taskId, step);
            LocalDateTime endTime = LocalDateTime.now();
            
            int sequenceOrder = getNextSequence(taskId);
            TaskLog taskLog = new TaskLog();
            taskLog.setTaskId(taskId);
            taskLog.setLevel(TaskLog.LogLevel.SUCCESS.getCode());
            taskLog.setStep(step);
            taskLog.setMessage(message);
            taskLog.setStartTime(startTime);
            taskLog.setEndTime(endTime);
            taskLog.setSequenceOrder(sequenceOrder);
            
            if (result != null) {
                taskLog.setMetadata(objectMapper.writeValueAsString(result));
            }
            
            this.save(taskLog);
            pushLogToClient(taskLog);
            
            logger.debug("完成任务步骤: taskId={}, step={}, duration={}ms", 
                        taskId, step, taskLog.getDurationMs());
                        
        } catch (Exception e) {
            logger.error("完成任务步骤失败: taskId={}, step={}", taskId, step, e);
        }
    }

    /**
     * 步骤失败记录
     */
    public void failStep(String taskId, String step, String errorCode, String errorMessage, Exception exception) {
        try {
            LocalDateTime startTime = getStepStartTime(taskId, step);
            LocalDateTime endTime = LocalDateTime.now();
            
            int sequenceOrder = getNextSequence(taskId);
            TaskLog taskLog = new TaskLog();
            taskLog.setTaskId(taskId);
            taskLog.setLevel(TaskLog.LogLevel.ERROR.getCode());
            taskLog.setStep(step);
            taskLog.setMessage(errorMessage);
            taskLog.setErrorCode(errorCode);
            taskLog.setStartTime(startTime);
            taskLog.setEndTime(endTime);
            taskLog.setSequenceOrder(sequenceOrder);
            
            // 添加异常详情到元数据
            if (exception != null) {
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("exceptionType", exception.getClass().getSimpleName());
                errorDetails.put("stackTrace", Arrays.toString(exception.getStackTrace()).substring(0, 
                    Math.min(1000, Arrays.toString(exception.getStackTrace()).length()))); // 限制长度
                taskLog.setMetadata(objectMapper.writeValueAsString(errorDetails));
            }
            
            this.save(taskLog);
            pushLogToClient(taskLog);
            
            logger.error("任务步骤失败: taskId={}, step={}, error={}, duration={}ms", 
                        taskId, step, errorCode, taskLog.getDurationMs());
                        
        } catch (Exception e) {
            logger.error("记录任务步骤失败失败: taskId={}, step={}", taskId, step, e);
        }
    }

    /**
     * 记录任务日志并附加额外数据
     * 
     * @param taskId 任务ID
     * @param level 日志级别
     * @param step 任务步骤
     * @param message 日志消息
     * @param progress 进度百分比
     * @param extraData 额外数据对象
     */
    public void logTask(String taskId, String level, String step, String message, Integer progress, Object extraData) {
        try {
            TaskLog taskLog = new TaskLog(taskId, level, step, message, progress);
            
            // 序列化额外数据
            if (extraData != null) {
                String extraDataJson = objectMapper.writeValueAsString(extraData);
                taskLog.setExtraData(extraDataJson);
            }
            
            this.save(taskLog);
            pushLogToClient(taskLog);
            
            logger.debug("任务日志已记录(含额外数据): taskId={}, level={}, message={}", taskId, level, message);
            
        } catch (JsonProcessingException e) {
            logger.error("序列化额外数据失败: taskId={}, message={}", taskId, message, e);
        } catch (Exception e) {
            logger.error("记录任务日志失败: taskId={}, message={}", taskId, message, e);
        }
    }

    /**
     * 推送日志到WebSocket客户端
     */
    private void pushLogToClient(TaskLog taskLog) {
        try {
            // 创建推送数据
            Map<String, Object> logData = new HashMap<>();
            logData.put("id", taskLog.getId());
            logData.put("taskId", taskLog.getTaskId());
            logData.put("parentTaskId", taskLog.getParentTaskId());
            logData.put("taskType", taskLog.getTaskType());
            logData.put("level", taskLog.getLevel());
            logData.put("step", taskLog.getStep());
            logData.put("message", taskLog.getMessage());
            logData.put("progress", taskLog.getProgress());
            logData.put("durationMs", taskLog.getDurationMs());
            logData.put("startTime", taskLog.getStartTime());
            logData.put("endTime", taskLog.getEndTime());
            logData.put("metadata", taskLog.getMetadata());
            logData.put("errorCode", taskLog.getErrorCode());
            logData.put("retryCount", taskLog.getRetryCount());
            logData.put("sequenceOrder", taskLog.getSequenceOrder());
            logData.put("createTime", taskLog.getCreateTime());
            
            // 添加执行状态
            String executionStatus = determineExecutionStatus(taskLog);
            logData.put("executionStatus", executionStatus);

            // 使用WebSocket控制器进行推送
            if (webSocketController != null) {
                // 推送实时日志
                webSocketController.broadcastLog(taskLog.getTaskId(), taskLog.getLevel(), 
                    taskLog.getMessage(), logData);
                
                // 推送进度更新
                if (taskLog.getProgress() != null) {
                    webSocketController.broadcastProgress(taskLog.getTaskId(), taskLog.getProgress(), 
                        taskLog.getStep(), logData);
                }
                
                // 推送任务状态更新
                webSocketController.broadcastTaskStatus(taskLog.getTaskId(), executionStatus, 
                    taskLog.getMessage(), logData);
                
                // 如果是错误日志，单独推送错误信息
                if ("ERROR".equals(taskLog.getLevel())) {
                    webSocketController.broadcastError(taskLog.getTaskId(), taskLog.getErrorCode(), 
                        taskLog.getMessage(), logData);
                }
            }
            
            // 直接推送到消息代理（备用方案）
            messagingTemplate.convertAndSend("/topic/crawler/task-logs", logData);
            messagingTemplate.convertAndSend("/topic/crawler/task/" + taskLog.getTaskId() + "/logs", logData);
            
            // 如果是步骤完成或失败，推送任务流程更新
            if ("SUCCESS".equals(taskLog.getLevel()) || "ERROR".equals(taskLog.getLevel())) {
                pushTaskFlowUpdate(taskLog.getTaskId());
            }
            
            logger.debug("TaskLog推送到WebSocket: taskId={}, step={}, sequence={}, status={}", 
                taskLog.getTaskId(), taskLog.getStep(), taskLog.getSequenceOrder(), executionStatus);
            
        } catch (Exception e) {
            logger.error("推送任务日志失败: taskId={}", taskLog.getTaskId(), e);
        }
    }

    /**
     * 推送任务流程更新
     */
    private void pushTaskFlowUpdate(String taskId) {
        try {
            List<TaskLog> taskFlow = baseMapper.selectTaskFlow(taskId);
            Map<String, Object> flowData = new HashMap<>();
            flowData.put("taskId", taskId);
            flowData.put("flow", taskFlow);
            flowData.put("timestamp", System.currentTimeMillis());
            
            // 推送任务流程更新
            messagingTemplate.convertAndSend("/topic/crawler/task/" + taskId + "/flow", flowData);
            messagingTemplate.convertAndSend("/topic/crawler/task-flow", flowData);
            
        } catch (Exception e) {
            logger.error("推送任务流程更新失败: taskId={}", taskId, e);
        }
    }

    /**
     * 确定执行状态
     */
    private String determineExecutionStatus(TaskLog taskLog) {
        if ("ERROR".equals(taskLog.getLevel())) {
            return "FAILED";
        } else if ("SUCCESS".equals(taskLog.getLevel()) && "COMPLETED".equals(taskLog.getStep())) {
            return "COMPLETED";
        } else if (taskLog.getEndTime() == null) {
            return "RUNNING";
        } else {
            return "IN_PROGRESS";
        }
    }

    /**
     * 获取下一个序列号
     */
    private int getNextSequence(String taskId) {
        return taskSequenceCounters.computeIfAbsent(taskId, k -> new AtomicInteger(0))
                                  .incrementAndGet();
    }

    /**
     * 记录步骤开始时间
     */
    private void recordStepStartTime(String taskId, String step) {
        taskStepStartTimes.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>())
                         .put(step, LocalDateTime.now());
    }

    /**
     * 获取步骤开始时间
     */
    private LocalDateTime getStepStartTime(String taskId, String step) {
        Map<String, LocalDateTime> stepTimes = taskStepStartTimes.get(taskId);
        return stepTimes != null ? stepTimes.get(step) : null;
    }

    /**
     * 清理任务相关的内存数据
     */
    public void cleanupTaskMemoryData(String taskId) {
        taskSequenceCounters.remove(taskId);
        taskStepStartTimes.remove(taskId);
        logger.debug("清理任务内存数据: taskId={}", taskId);
    }

    /**
     * 分页查询任务日志
     * 
     * @param page 页码
     * @param size 页大小
     * @param taskId 任务ID（可选）
     * @param level 日志级别（可选）
     * @return 分页结果
     */
    public IPage<TaskLog> getTaskLogsPage(int page, int size, String taskId, String level) {
        Page<TaskLog> pageObj = new Page<>(page, size);
        
        QueryWrapper<TaskLog> queryWrapper = new QueryWrapper<>();
        
        if (taskId != null && !taskId.trim().isEmpty()) {
            queryWrapper.eq("task_id", taskId);
        }
        
        if (level != null && !level.trim().isEmpty()) {
            queryWrapper.eq("level", level);
        }
        
        // 按创建时间倒序
        queryWrapper.orderByDesc("create_time");
        
        return this.page(pageObj, queryWrapper);
    }

    /**
     * 获取任务的最新日志列表
     * 
     * @param taskId 任务ID
     * @param limit 限制数量
     * @return 日志列表
     */
    public List<TaskLog> getLatestTaskLogs(String taskId, int limit) {
        return baseMapper.selectLogsByTaskId(taskId, limit);
    }

    /**
     * 获取任务的当前进度
     * 
     * @param taskId 任务ID
     * @return 进度百分比
     */
    public Integer getTaskProgress(String taskId) {
        TaskLog latestLog = baseMapper.selectLatestLogByTaskId(taskId);
        return latestLog != null ? latestLog.getProgress() : 0;
    }

    /**
     * 获取所有正在进行的任务及其最新状态
     * 
     * @return 任务状态映射
     */
    public Map<String, TaskLog> getActiveTasksStatus() {
        Map<String, TaskLog> taskStatusMap = new HashMap<>();
        
        // 查询最近1小时的日志
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<TaskLog> recentLogs = baseMapper.selectLogsByTimeRange(oneHourAgo, LocalDateTime.now(), 1000);
        
        // 按任务ID分组，保留最新的日志
        for (TaskLog log : recentLogs) {
            String taskId = log.getTaskId();
            if (!taskStatusMap.containsKey(taskId) || 
                log.getCreateTime().isAfter(taskStatusMap.get(taskId).getCreateTime())) {
                taskStatusMap.put(taskId, log);
            }
        }
        
        return taskStatusMap;
    }

    /**
     * 清理过期的日志记录
     * 
     * @param daysToKeep 保留的天数
     * @return 清理的记录数
     */
    public int cleanExpiredLogs(int daysToKeep) {
        LocalDateTime expireTime = LocalDateTime.now().minusDays(daysToKeep);
        int deletedCount = baseMapper.deleteLogsBefore(expireTime);
        
        if (deletedCount > 0) {
            logger.info("清理了 {} 条过期日志记录", deletedCount);
        }
        
        return deletedCount;
    }

    /**
     * 记录任务开始日志
     * 
     * @param taskId 任务ID
     * @param taskType 任务类型
     * @param details 任务详情
     */
    public void logTaskStart(String taskId, String taskType, Object details) {
        String message = String.format("任务开始执行 - 类型: %s", taskType);
        logTask(taskId, TaskLog.LogLevel.INFO.getCode(), TaskLog.TaskStep.INIT.getCode(), 
                message, 0, details);
    }

    /**
     * 记录任务完成日志
     * 
     * @param taskId 任务ID
     * @param result 任务结果
     */
    public void logTaskCompleted(String taskId, Object result) {
        String message = "任务执行完成";
        logTask(taskId, TaskLog.LogLevel.SUCCESS.getCode(), TaskLog.TaskStep.COMPLETED.getCode(), 
                message, 100, result);
    }

    /**
     * 记录任务失败日志
     * 
     * @param taskId 任务ID
     * @param error 错误信息
     */
    public void logTaskFailed(String taskId, String error) {
        String message = String.format("任务执行失败: %s", error);
        logTask(taskId, TaskLog.LogLevel.ERROR.getCode(), TaskLog.TaskStep.FAILED.getCode(), 
                message, null);
    }

    /**
     * 记录任务进度日志
     */
    public void logTaskProgress(String taskId, String step, String message, Integer progress) {
        logTask(taskId, TaskLog.LogLevel.INFO.getCode(), step, message, progress);
    }

    // ========== 增强查询方法 ==========

    /**
     * 获取任务的完整执行流程
     */
    public List<TaskLog> getTaskFlow(String taskId) {
        return baseMapper.selectTaskFlow(taskId);
    }

    /**
     * 获取任务的子任务列表
     */
    public List<TaskLog> getChildTasks(String parentTaskId) {
        return baseMapper.selectChildTasks(parentTaskId);
    }

    /**
     * 获取任务执行性能统计
     */
    public Map<String, Object> getTaskPerformance(String taskId) {
        return baseMapper.selectTaskPerformance(taskId);
    }

    /**
     * 获取活跃任务列表
     */
    public List<Map<String, Object>> getActiveTasks() {
        return baseMapper.selectActiveTasks();
    }

    /**
     * 获取错误热点分析
     */
    public List<Map<String, Object>> getErrorHotspots() {
        return baseMapper.selectErrorHotspots();
    }

    /**
     * 获取步骤性能统计
     */
    public List<Map<String, Object>> getStepPerformance() {
        return baseMapper.selectStepPerformance();
    }

    /**
     * 获取任务进度趋势
     */
    public List<Map<String, Object>> getTaskTrends() {
        return baseMapper.selectTaskTrends();
    }

    /**
     * 创建任务执行上下文
     */
    public TaskExecutionContext createExecutionContext(String taskId, String taskType) {
        return new TaskExecutionContext(this, taskId, taskType);
    }

    // ========== 任务执行上下文类 ==========

    /**
     * 任务执行上下文类，简化任务日志记录
     */
    public static class TaskExecutionContext {
        private final TaskLogService taskLogService;
        private final String taskId;
        private final String taskType;
        private String currentStep;

        public TaskExecutionContext(TaskLogService taskLogService, String taskId, String taskType) {
            this.taskLogService = taskLogService;
            this.taskId = taskId;
            this.taskType = taskType;
        }

        public TaskExecutionContext startStep(String step, String message) {
            this.currentStep = step;
            taskLogService.startStep(taskId, null, taskType, step, message);
            return this;
        }

        public TaskExecutionContext startStep(String step, String message, Integer progress) {
            this.currentStep = step;
            taskLogService.startStep(taskId, null, taskType, step, message, progress);
            return this;
        }

        public TaskExecutionContext updateProgress(Integer progress, String message) {
            if (currentStep != null) {
                taskLogService.logTaskWithSequence(taskId, null, taskType, 
                    TaskLog.LogLevel.INFO.getCode(), currentStep, message, progress, null, null);
            }
            return this;
        }

        public TaskExecutionContext completeStep(String message, Object result) {
            if (currentStep != null) {
                taskLogService.completeStep(taskId, currentStep, message, result);
                currentStep = null;
            }
            return this;
        }

        public TaskExecutionContext failStep(String errorCode, String errorMessage, Exception exception) {
            if (currentStep != null) {
                taskLogService.failStep(taskId, currentStep, errorCode, errorMessage, exception);
                currentStep = null;
            }
            return this;
        }

        public TaskExecutionContext log(String level, String step, String message, Integer progress) {
            taskLogService.logTaskWithSequence(taskId, null, taskType, level, step, message, progress, null, null);
            return this;
        }

        public void cleanup() {
            taskLogService.cleanupTaskMemoryData(taskId);
        }

        public String getTaskId() {
            return taskId;
        }

        public String getCurrentStep() {
            return currentStep;
        }
    }
}