package com.lcsc.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 爬虫WebSocket实时推送控制器
 * 处理实时消息推送和客户端连接管理
 * 
 * @author lcsc-crawler
 * @since 2025-09-07
 */
@Controller
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
public class CrawlerWebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(CrawlerWebSocketController.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 处理客户端连接消息
     */
    @MessageMapping("/crawler/connect")
    @SendTo("/topic/crawler/status")
    public CrawlerMessage handleConnect(@Payload Map<String, Object> message) {
        logger.info("客户端连接WebSocket: {}", message);
        
        return CrawlerMessage.builder()
            .type("connection")
            .message("WebSocket连接成功")
            .timestamp(LocalDateTime.now())
            .data(Map.of("status", "connected"))
            .build();
    }

    /**
     * 处理客户端订阅特定任务
     */
    @MessageMapping("/crawler/subscribe")
    public void handleTaskSubscription(@Payload Map<String, Object> message) {
        String taskId = (String) message.get("taskId");
        String sessionId = (String) message.get("sessionId");
        
        logger.info("客户端订阅任务: taskId={}, sessionId={}", taskId, sessionId);
        
        // 发送任务订阅成功消息
        CrawlerMessage response = CrawlerMessage.builder()
            .type("subscription")
            .taskId(taskId)
            .message("任务订阅成功")
            .timestamp(LocalDateTime.now())
            .data(Map.of(
                "taskId", taskId,
                "subscribed", true
            ))
            .build();

        messagingTemplate.convertAndSend("/topic/crawler/task/" + taskId, response);
    }

    /**
     * 广播爬虫任务状态更新
     */
    public void broadcastTaskStatus(String taskId, String status, String message, Object data) {
        CrawlerMessage crawlerMessage = CrawlerMessage.builder()
            .type("task_status")
            .taskId(taskId)
            .status(status)
            .message(message)
            .timestamp(LocalDateTime.now())
            .data(data)
            .build();

        // 发送到任务特定频道
        messagingTemplate.convertAndSend("/topic/crawler/task/" + taskId, crawlerMessage);
        
        // 发送到全局状态频道
        messagingTemplate.convertAndSend("/topic/crawler/status", crawlerMessage);
        
        logger.debug("广播任务状态: taskId={}, status={}", taskId, status);
    }

    /**
     * 广播爬虫进度更新
     */
    public void broadcastProgress(String taskId, int progress, String currentStep, Object stepData) {
        CrawlerMessage message = CrawlerMessage.builder()
            .type("progress")
            .taskId(taskId)
            .progress(progress)
            .currentStep(currentStep)
            .message(String.format("进度更新: %d%% - %s", progress, currentStep))
            .timestamp(LocalDateTime.now())
            .data(stepData)
            .build();

        messagingTemplate.convertAndSend("/topic/crawler/task/" + taskId + "/progress", message);
        logger.debug("广播进度更新: taskId={}, progress={}%", taskId, progress);
    }

    /**
     * 广播实时日志
     */
    public void broadcastLog(String taskId, String level, String logMessage, Object logData) {
        CrawlerMessage message = CrawlerMessage.builder()
            .type("log")
            .taskId(taskId)
            .level(level)
            .message(logMessage)
            .timestamp(LocalDateTime.now())
            .data(logData)
            .build();

        messagingTemplate.convertAndSend("/topic/crawler/task/" + taskId + "/logs", message);
        logger.debug("广播日志: taskId={}, level={}, message={}", taskId, level, logMessage);
    }

    /**
     * 广播错误信息
     */
    public void broadcastError(String taskId, String errorCode, String errorMessage, Object errorData) {
        CrawlerMessage message = CrawlerMessage.builder()
            .type("error")
            .taskId(taskId)
            .errorCode(errorCode)
            .message(errorMessage)
            .timestamp(LocalDateTime.now())
            .data(errorData)
            .build();

        messagingTemplate.convertAndSend("/topic/crawler/task/" + taskId + "/errors", message);
        messagingTemplate.convertAndSend("/topic/crawler/errors", message);
        
        logger.warn("广播错误: taskId={}, errorCode={}, message={}", taskId, errorCode, errorMessage);
    }

    /**
     * 广播系统状态
     */
    public void broadcastSystemStatus(String systemStatus, Object statusData) {
        CrawlerMessage message = CrawlerMessage.builder()
            .type("system_status")
            .status(systemStatus)
            .message("系统状态更新")
            .timestamp(LocalDateTime.now())
            .data(statusData)
            .build();

        messagingTemplate.convertAndSend("/topic/crawler/system", message);
        logger.debug("广播系统状态: status={}", systemStatus);
    }

    /**
     * 广播数据统计信息
     */
    public void broadcastStatistics(Map<String, Object> statistics) {
        CrawlerMessage message = CrawlerMessage.builder()
            .type("statistics")
            .message("统计信息更新")
            .timestamp(LocalDateTime.now())
            .data(statistics)
            .build();

        messagingTemplate.convertAndSend("/topic/crawler/statistics", message);
        logger.debug("广播统计信息: {}", statistics);
    }

    /**
     * 通用广播方法
     * 用于V3爬虫系统
     */
    public void broadcast(String messageType, Map<String, Object> data) {
        CrawlerMessage message = CrawlerMessage.builder()
            .type(messageType)
            .message(messageType)
            .timestamp(LocalDateTime.now())
            .data(data)
            .build();

        messagingTemplate.convertAndSend("/topic/crawler/v3", message);
        logger.debug("广播消息: type={}, data={}", messageType, data);
    }

    /**
     * 广播分类进度更新（V3专用）
     */
    public void broadcastCategoryProgress(Integer catalogId, int progress,
                                          int crawledProducts, int totalProducts) {
        Map<String, Object> data = Map.of(
            "catalogId", catalogId,
            "progress", progress,
            "crawledProducts", crawledProducts,
            "totalProducts", totalProducts
        );
        broadcast("CATEGORY_PROGRESS", data);
    }

    /**
     * WebSocket消息封装类
     */
    public static class CrawlerMessage {
        private String type;
        private String taskId;
        private String status;
        private String level;
        private String errorCode;
        private Integer progress;
        private String currentStep;
        private String message;
        private LocalDateTime timestamp;
        private Object data;

        public static Builder builder() {
            return new Builder();
        }

        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        
        public Integer getProgress() { return progress; }
        public void setProgress(Integer progress) { this.progress = progress; }
        
        public String getCurrentStep() { return currentStep; }
        public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }

        public static class Builder {
            private CrawlerMessage message = new CrawlerMessage();

            public Builder type(String type) { message.type = type; return this; }
            public Builder taskId(String taskId) { message.taskId = taskId; return this; }
            public Builder status(String status) { message.status = status; return this; }
            public Builder level(String level) { message.level = level; return this; }
            public Builder errorCode(String errorCode) { message.errorCode = errorCode; return this; }
            public Builder progress(Integer progress) { message.progress = progress; return this; }
            public Builder currentStep(String currentStep) { message.currentStep = currentStep; return this; }
            public Builder message(String msg) { message.message = msg; return this; }
            public Builder timestamp(LocalDateTime timestamp) { message.timestamp = timestamp; return this; }
            public Builder data(Object data) { message.data = data; return this; }

            public CrawlerMessage build() { return message; }
        }
    }
}