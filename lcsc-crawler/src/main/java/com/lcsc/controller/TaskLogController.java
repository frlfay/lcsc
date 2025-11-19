package com.lcsc.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lcsc.common.Result;
import com.lcsc.entity.TaskLog;
import com.lcsc.service.TaskLogService;
import com.lcsc.service.RedisTaskLogService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 任务日志控制器
 * 提供任务日志查询和管理功能
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api/task-logs")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
public class TaskLogController {

    private static final Logger logger = LoggerFactory.getLogger(TaskLogController.class);

    @Autowired
    private TaskLogService taskLogService;
    
    @Autowired
    private RedisTaskLogService redisTaskLogService;

    /**
     * 分页查询任务日志 - 从Redis获取
     */
    @GetMapping("/page")
    public Result<Map<String, Object>> getTaskLogsPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String level) {
        try {
            logger.debug("从Redis分页查询任务日志: page={}, size={}, taskId={}, level={}", page, size, taskId, level);
            
            Map<String, Object> result = redisTaskLogService.getTaskLogsPage(page, size, taskId, level);
            
            return Result.success(result);
        } catch (Exception e) {
            logger.error("从Redis查询任务日志失败", e);
            return Result.error("查询任务日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定任务的最新日志 - 从Redis获取
     */
    @GetMapping("/latest/{taskId}")
    public Result<List<TaskLog>> getLatestTaskLogs(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            logger.debug("从Redis查询任务最新日志: taskId={}, limit={}", taskId, limit);
            
            List<TaskLog> logs = redisTaskLogService.getLatestTaskLogs(taskId, limit);
            
            return Result.success(logs);
        } catch (Exception e) {
            logger.error("从Redis查询任务最新日志失败: taskId={}", taskId, e);
            return Result.error("查询任务日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务当前进度 - 从Redis获取
     */
    @GetMapping("/progress/{taskId}")
    public Result<Map<String, Object>> getTaskProgress(@PathVariable String taskId) {
        try {
            logger.debug("从Redis查询任务进度: taskId={}", taskId);
            
            Integer progress = redisTaskLogService.getTaskProgress(taskId);
            List<TaskLog> latestLogs = redisTaskLogService.getLatestTaskLogs(taskId, 1);
            TaskLog latestLog = latestLogs.stream().findFirst().orElse(null);
            
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("progress", progress);
            if (latestLog != null) {
                result.put("latestStep", latestLog.getStep());
                result.put("latestMessage", latestLog.getMessage());
                result.put("latestTime", latestLog.getCreateTime());
            }
            
            return Result.success(result);
        } catch (Exception e) {
            logger.error("从Redis查询任务进度失败: taskId={}", taskId, e);
            return Result.error("查询任务进度失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有活跃任务的状态 - 从Redis获取
     */
    @GetMapping("/active-tasks")
    public Result<Map<String, TaskLog>> getActiveTasksStatus() {
        try {
            logger.debug("从Redis查询所有活跃任务状态");
            
            Map<String, TaskLog> activeTasksStatus = redisTaskLogService.getActiveTasksStatus();
            
            return Result.success(activeTasksStatus);
        } catch (Exception e) {
            logger.error("从Redis查询活跃任务状态失败", e);
            return Result.error("查询活跃任务状态失败: " + e.getMessage());
        }
    }

    /**
     * 手动记录任务日志（用于测试） - 存储到Redis
     */
    @PostMapping("/log")
    public Result<String> logTask(@RequestBody Map<String, Object> logRequest) {
        try {
            String taskId = (String) logRequest.get("taskId");
            String level = (String) logRequest.get("level");
            String step = (String) logRequest.get("step");
            String message = (String) logRequest.get("message");
            Integer progress = logRequest.get("progress") != null ? 
                ((Number) logRequest.get("progress")).intValue() : null;

            if (taskId == null || taskId.trim().isEmpty()) {
                return Result.error("taskId 不能为空");
            }
            if (message == null || message.trim().isEmpty()) {
                return Result.error("message 不能为空");
            }

            redisTaskLogService.logTask(taskId, 
                level != null ? level : "INFO", 
                step != null ? step : "CUSTOM", 
                message, 
                progress);

            return Result.success("日志记录到Redis成功");
        } catch (Exception e) {
            logger.error("记录任务日志到Redis失败", e);
            return Result.error("记录任务日志失败: " + e.getMessage());
        }
    }

    /**
     * 清理过期日志 - 从Redis清理
     */
    @PostMapping("/cleanup")
    public Result<Map<String, Object>> cleanupExpiredLogs(
            @RequestParam(defaultValue = "7") int daysToKeep) {
        try {
            logger.info("开始清理Redis过期日志，保留天数: {}", daysToKeep);
            
            int deletedCount = redisTaskLogService.cleanExpiredLogs(daysToKeep);
            
            Map<String, Object> result = new HashMap<>();
            result.put("deletedCount", deletedCount);
            result.put("daysToKeep", daysToKeep);
            result.put("message", "从Redis清理完成，删除了 " + deletedCount + " 条过期日志");
            
            return Result.success(result);
        } catch (Exception e) {
            logger.error("清理Redis过期日志失败", e);
            return Result.error("清理过期日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务日志统计信息
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getTaskLogStats() {
        try {
            logger.debug("查询任务日志统计信息");
            
            // 这里可以添加更多统计逻辑
            Map<String, Object> stats = new HashMap<>();
            
            // 获取活跃任务数量
            Map<String, TaskLog> activeTasks = taskLogService.getActiveTasksStatus();
            stats.put("activeTasksCount", activeTasks.size());
            
            // 按级别统计（可以通过添加更多查询方法来实现）
            stats.put("totalLogs", "暂未实现");
            stats.put("errorCount", "暂未实现");
            stats.put("successCount", "暂未实现");
            
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("查询任务日志统计信息失败", e);
            return Result.error("查询统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 从数据库分页查询任务日志 (用于实时更新)
     */
    @GetMapping("/db/page")
    public Result<Map<String, Object>> getTaskLogsFromDB(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String level) {
        try {
            logger.debug("从数据库分页查询任务日志: page={}, size={}, taskId={}, level={}", page, size, taskId, level);
            
            IPage<TaskLog> pageRequest = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
            
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TaskLog> queryWrapper = 
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            
            if (taskId != null && !taskId.trim().isEmpty()) {
                queryWrapper.eq("task_id", taskId.trim());
            }
            if (level != null && !level.trim().isEmpty()) {
                queryWrapper.eq("level", level.trim());
            }
            
            // 按创建时间降序排列，获取最新的日志
            queryWrapper.orderByDesc("create_time");
            
            IPage<TaskLog> resultPage = taskLogService.page(pageRequest, queryWrapper);
            
            Map<String, Object> result = new HashMap<>();
            result.put("records", resultPage.getRecords());
            result.put("total", resultPage.getTotal());
            result.put("size", resultPage.getSize());
            result.put("current", resultPage.getCurrent());
            result.put("pages", resultPage.getPages());
            
            return Result.success(result);
        } catch (Exception e) {
            logger.error("从数据库查询任务日志失败: page={}, size={}, taskId={}, level={}", page, size, taskId, level, e);
            return Result.error("查询任务日志失败: " + e.getMessage());
        }
    }

    /**
     * 根据任务ID删除日志
     */
    @DeleteMapping("/{taskId}")
    public Result<String> deleteTaskLogs(@PathVariable String taskId) {
        try {
            logger.info("删除任务日志: taskId={}", taskId);
            
            boolean success = taskLogService.remove(
                taskLogService.query().eq("task_id", taskId).getWrapper()
            );
            
            if (success) {
                return Result.success("任务日志删除成功");
            } else {
                return Result.error("任务日志删除失败");
            }
        } catch (Exception e) {
            logger.error("删除任务日志失败: taskId={}", taskId, e);
            return Result.error("删除任务日志失败: " + e.getMessage());
        }
    }
}