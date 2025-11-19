package com.lcsc.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 爬虫任务实体类
 * 用于持久化Redis中的任务数据
 * 
 * @author lcsc-crawler
 * @since 2025-09-08
 */
@TableName(value = "crawler_tasks", autoResultMap = true)
public class CrawlerTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    @TableField(value = "task_id")
    private String taskId;
    
    @TableField(value = "task_type")
    private String taskType;
    
    @TableField(value = "task_status")
    private String taskStatus;
    
    @TableField(value = "task_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> taskParams;
    
    @TableField(value = "task_result", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> taskResult;
    
    @TableField(value = "priority")
    private Integer priority;
    
    @TableField(value = "retry_count")
    private Integer retryCount;
    
    @TableField(value = "max_retry_count")
    private Integer maxRetryCount;
    
    @TableField(value = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @TableField(value = "started_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;
    
    @TableField(value = "completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;
    
    @TableField(value = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @TableField(value = "error_message")
    private String errorMessage;
    
    @TableField(value = "error_code")
    private String errorCode;
    
    @TableField(value = "execution_duration_ms")
    private Long executionDurationMs;
    
    @TableField(value = "worker_thread")
    private String workerThread;
    
    @TableField(value = "catalog_id")
    private Integer catalogId;
    
    @TableField(value = "product_codes", typeHandler = JacksonTypeHandler.class)
    private List<String> productCodes;
    
    @TableField(value = "batch_id")
    private String batchId;
    
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    // 任务状态枚举
    public static class TaskStatus {
        public static final String PENDING = "PENDING";
        public static final String PROCESSING = "PROCESSING";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
    }

    // 任务类型枚举
    public static class TaskType {
        public static final String CATALOG_CRAWL = "CATALOG_CRAWL";
        public static final String PRODUCT_CRAWL = "PRODUCT_CRAWL";
        public static final String BATCH_CRAWL = "BATCH_CRAWL";
        public static final String CUSTOM_CRAWL = "CUSTOM_CRAWL";
        public static final String DATA_EXPORT = "DATA_EXPORT";
    }

    // 构造函数
    public CrawlerTask() {}
    
    public CrawlerTask(String taskId, String taskType, String taskStatus) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.taskStatus = taskStatus;
        this.createdAt = LocalDateTime.now();
        this.priority = 0;
        this.retryCount = 0;
        this.maxRetryCount = 3;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public Map<String, Object> getTaskParams() {
        return taskParams;
    }

    public void setTaskParams(Map<String, Object> taskParams) {
        this.taskParams = taskParams;
    }

    public Map<String, Object> getTaskResult() {
        return taskResult;
    }

    public void setTaskResult(Map<String, Object> taskResult) {
        this.taskResult = taskResult;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Long getExecutionDurationMs() {
        return executionDurationMs;
    }

    public void setExecutionDurationMs(Long executionDurationMs) {
        this.executionDurationMs = executionDurationMs;
    }

    public String getWorkerThread() {
        return workerThread;
    }

    public void setWorkerThread(String workerThread) {
        this.workerThread = workerThread;
    }

    public Integer getCatalogId() {
        return catalogId;
    }

    public void setCatalogId(Integer catalogId) {
        this.catalogId = catalogId;
    }

    public List<String> getProductCodes() {
        return productCodes;
    }

    public void setProductCodes(List<String> productCodes) {
        this.productCodes = productCodes;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return "CrawlerTask{" +
                "id=" + id +
                ", taskId='" + taskId + '\'' +
                ", taskType='" + taskType + '\'' +
                ", taskStatus='" + taskStatus + '\'' +
                ", priority=" + priority +
                ", retryCount=" + retryCount +
                ", createdAt=" + createdAt +
                ", completedAt=" + completedAt +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}