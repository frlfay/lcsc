package com.lcsc.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 任务日志实体类
 * 记录爬虫任务执行过程中的详细状态和进度
 * 支持层级任务、性能监控和可视化功能
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@TableName("task_logs")
public class TaskLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID，关联到TaskManager中的任务
     */
    @TableField("task_id")
    private String taskId;

    /**
     * 父任务ID，支持任务层级关系
     */
    @TableField("parent_task_id")
    private String parentTaskId;

    /**
     * 任务类型：CATALOG_CRAWL, PRODUCT_CRAWL, EXPORT等
     */
    @TableField("task_type")
    private String taskType;

    /**
     * 日志级别：INFO, WARN, ERROR, DEBUG, SUCCESS
     */
    @TableField("level")
    private String level;

    /**
     * 任务步骤：INIT, CRAWLING, PARSING, SAVING, COMPLETED, FAILED
     */
    @TableField("step")
    private String step;

    /**
     * 日志消息内容
     */
    @TableField("message")
    private String message;

    /**
     * 任务进度百分比 (0-100)
     */
    @TableField("progress")
    private Integer progress;

    /**
     * 步骤执行时长(毫秒)
     */
    @TableField("duration_ms")
    private Long durationMs;

    /**
     * 步骤开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 步骤结束时间
     */
    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 扩展数据，JSON格式存储
     */
    @TableField("extra_data")
    private String extraData;

    /**
     * 结构化元数据(产品数量、API响应时间等)
     */
    @TableField("metadata")
    private String metadata;

    /**
     * 错误代码
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 重试次数
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 同一任务内的步骤顺序
     */
    @TableField("sequence_order")
    private Integer sequenceOrder;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // 构造函数
    public TaskLog() {}

    public TaskLog(String taskId, String level, String step, String message, Integer progress) {
        this.taskId = taskId;
        this.level = level;
        this.step = step;
        this.message = message;
        this.progress = progress;
        this.createTime = LocalDateTime.now();
        this.retryCount = 0;
        this.sequenceOrder = 0;
    }

    /**
     * 增强构造函数，支持更多参数
     */
    public TaskLog(String taskId, String parentTaskId, String taskType, String level, String step, 
                   String message, Integer progress, Integer sequenceOrder) {
        this.taskId = taskId;
        this.parentTaskId = parentTaskId;
        this.taskType = taskType;
        this.level = level;
        this.step = step;
        this.message = message;
        this.progress = progress;
        this.sequenceOrder = sequenceOrder;
        this.createTime = LocalDateTime.now();
        this.retryCount = 0;
        this.startTime = LocalDateTime.now();
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

    public String getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getStep() {
        return step;
    }

    public void setStep(String step) {
        this.step = step;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
        // 自动计算执行时长
        if (this.startTime != null && endTime != null) {
            this.durationMs = java.time.Duration.between(this.startTime, endTime).toMillis();
        }
    }

    public String getExtraData() {
        return extraData;
    }

    public void setExtraData(String extraData) {
        this.extraData = extraData;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getSequenceOrder() {
        return sequenceOrder;
    }

    public void setSequenceOrder(Integer sequenceOrder) {
        this.sequenceOrder = sequenceOrder;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 便捷方法：完成当前步骤
     */
    public void completeStep() {
        this.endTime = LocalDateTime.now();
        this.level = LogLevel.SUCCESS.getCode();
        if (this.startTime != null) {
            this.durationMs = java.time.Duration.between(this.startTime, this.endTime).toMillis();
        }
    }

    /**
     * 便捷方法：标记步骤失败
     */
    public void failStep(String errorCode, String errorMessage) {
        this.endTime = LocalDateTime.now();
        this.level = LogLevel.ERROR.getCode();
        this.errorCode = errorCode;
        this.message = errorMessage;
        if (this.startTime != null) {
            this.durationMs = java.time.Duration.between(this.startTime, this.endTime).toMillis();
        }
    }

    /**
     * 便捷方法：增加重试次数
     */
    public void incrementRetry() {
        this.retryCount = (this.retryCount == null) ? 1 : this.retryCount + 1;
    }

    @Override
    public String toString() {
        return "TaskLog{" +
                "id=" + id +
                ", taskId='" + taskId + '\'' +
                ", parentTaskId='" + parentTaskId + '\'' +
                ", taskType='" + taskType + '\'' +
                ", level='" + level + '\'' +
                ", step='" + step + '\'' +
                ", message='" + message + '\'' +
                ", progress=" + progress +
                ", durationMs=" + durationMs +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", errorCode='" + errorCode + '\'' +
                ", retryCount=" + retryCount +
                ", sequenceOrder=" + sequenceOrder +
                ", createTime=" + createTime +
                '}';
    }

    /**
     * 日志级别枚举
     */
    public enum LogLevel {
        INFO("INFO", "信息"),
        WARN("WARN", "警告"),
        ERROR("ERROR", "错误"),
        DEBUG("DEBUG", "调试"),
        SUCCESS("SUCCESS", "成功");

        private final String code;
        private final String description;

        LogLevel(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 任务步骤枚举
     */
    public enum TaskStep {
        INIT("INIT", "初始化"),
        FETCHING_CATALOGS("FETCHING_CATALOGS", "获取目录列表"),
        PROCESSING_CATALOG("PROCESSING_CATALOG", "处理目录"),
        FETCHING_MANUFACTURERS("FETCHING_MANUFACTURERS", "获取制造商列表"),
        PROCESSING_MANUFACTURER("PROCESSING_MANUFACTURER", "处理制造商"),
        FETCHING_PACKAGES("FETCHING_PACKAGES", "获取封装列表"),
        PROCESSING_PACKAGE("PROCESSING_PACKAGE", "处理封装"),
        FETCHING_PRODUCTS("FETCHING_PRODUCTS", "获取产品列表"),
        PARSING_PRODUCTS("PARSING_PRODUCTS", "解析产品数据"),
        SAVING_PRODUCTS("SAVING_PRODUCTS", "保存产品数据"),
        CRAWLING("CRAWLING", "爬取中"),
        PARSING("PARSING", "解析数据"),
        SAVING("SAVING", "保存数据"),
        EXPORTING("EXPORTING", "导出数据"),
        COMPLETED("COMPLETED", "已完成"),
        FAILED("FAILED", "失败"),
        CANCELLED("CANCELLED", "已取消"),
        RETRYING("RETRYING", "重试中");

        private final String code;
        private final String description;

        TaskStep(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 任务类型枚举
     */
    public enum TaskType {
        CATALOG_CRAWL("CATALOG_CRAWL", "目录爬取"),
        PRODUCT_CRAWL("PRODUCT_CRAWL", "产品爬取"),
        SINGLE_PRODUCT_CRAWL("SINGLE_PRODUCT_CRAWL", "单个产品爬取"),
        BATCH_PRODUCT_CRAWL("BATCH_PRODUCT_CRAWL", "批量产品爬取"),
        DATA_EXPORT("DATA_EXPORT", "数据导出"),
        SYSTEM_MAINTENANCE("SYSTEM_MAINTENANCE", "系统维护"),
        TEST_TASK("TEST_TASK", "测试任务");

        private final String code;
        private final String description;

        TaskType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 错误代码枚举
     */
    public enum ErrorCode {
        NETWORK_ERROR("NETWORK_ERROR", "网络错误"),
        TIMEOUT_ERROR("TIMEOUT_ERROR", "超时错误"),
        PARSE_ERROR("PARSE_ERROR", "解析错误"),
        DATABASE_ERROR("DATABASE_ERROR", "数据库错误"),
        API_ERROR("API_ERROR", "API错误"),
        VALIDATION_ERROR("VALIDATION_ERROR", "数据验证错误"),
        UNKNOWN_ERROR("UNKNOWN_ERROR", "未知错误");

        private final String code;
        private final String description;

        ErrorCode(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }
}