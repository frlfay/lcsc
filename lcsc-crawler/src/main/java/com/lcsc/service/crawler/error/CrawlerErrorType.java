package com.lcsc.service.crawler.error;

/**
 * 爬虫错误类型枚举
 * 定义了不同类型的错误及其处理策略
 * 
 * @author lcsc-crawler
 * @since 2025-09-06
 */
public enum CrawlerErrorType {
    
    // ==================== 网络相关错误 ====================
    NETWORK_TIMEOUT("网络超时", ErrorSeverity.MEDIUM, true, 3, 2000),
    CONNECTION_REFUSED("连接被拒绝", ErrorSeverity.MEDIUM, true, 2, 5000),
    CONNECTION_TIMEOUT("连接超时", ErrorSeverity.MEDIUM, true, 3, 3000),
    DNS_RESOLUTION_ERROR("DNS解析失败", ErrorSeverity.HIGH, true, 2, 10000),
    NETWORK_UNREACHABLE("网络不可达", ErrorSeverity.HIGH, true, 1, 30000),
    
    // ==================== API相关错误 ====================
    API_RATE_LIMIT("API频率限制", ErrorSeverity.MEDIUM, true, 5, 5000),
    API_SERVER_ERROR("API服务器错误", ErrorSeverity.MEDIUM, true, 3, 3000),
    API_CLIENT_ERROR("API客户端错误", ErrorSeverity.LOW, false, 0, 0),
    API_UNAUTHORIZED("API认证失败", ErrorSeverity.HIGH, false, 0, 0),
    API_FORBIDDEN("API访问被禁止", ErrorSeverity.CRITICAL, false, 0, 0),
    API_NOT_FOUND("API端点不存在", ErrorSeverity.HIGH, false, 0, 0),
    API_BAD_REQUEST("API请求参数错误", ErrorSeverity.MEDIUM, false, 0, 0),
    API_SERVICE_UNAVAILABLE("API服务不可用", ErrorSeverity.HIGH, true, 2, 30000),
    
    // ==================== 数据相关错误 ====================
    DATA_VALIDATION_ERROR("数据验证失败", ErrorSeverity.LOW, false, 0, 0),
    DATA_PARSING_ERROR("数据解析失败", ErrorSeverity.LOW, false, 0, 0),
    DATA_FORMAT_ERROR("数据格式错误", ErrorSeverity.LOW, false, 0, 0),
    DATA_INCOMPLETE_ERROR("数据不完整", ErrorSeverity.MEDIUM, true, 1, 1000),
    DATA_DUPLICATE_ERROR("数据重复", ErrorSeverity.LOW, false, 0, 0),
    DATA_CORRUPTION_ERROR("数据损坏", ErrorSeverity.HIGH, true, 2, 2000),
    
    // ==================== 数据库相关错误 ====================
    DATABASE_CONNECTION_ERROR("数据库连接失败", ErrorSeverity.CRITICAL, true, 3, 5000),
    DATABASE_TIMEOUT_ERROR("数据库操作超时", ErrorSeverity.MEDIUM, true, 2, 3000),
    DATABASE_DEADLOCK_ERROR("数据库死锁", ErrorSeverity.MEDIUM, true, 3, 1000),
    DATABASE_CONSTRAINT_ERROR("数据库约束违反", ErrorSeverity.LOW, false, 0, 0),
    DATABASE_QUERY_ERROR("数据库查询错误", ErrorSeverity.MEDIUM, true, 1, 1000),
    
    // ==================== 系统资源相关错误 ====================
    MEMORY_ERROR("内存不足", ErrorSeverity.CRITICAL, true, 1, 10000),
    DISK_SPACE_ERROR("磁盘空间不足", ErrorSeverity.CRITICAL, false, 0, 0),
    THREAD_POOL_EXHAUSTED("线程池耗尽", ErrorSeverity.HIGH, true, 2, 5000),
    FILE_IO_ERROR("文件IO错误", ErrorSeverity.MEDIUM, true, 2, 1000),
    PERMISSION_ERROR("权限不足", ErrorSeverity.HIGH, false, 0, 0),
    
    // ==================== 业务逻辑相关错误 ====================
    TASK_EXECUTION_ERROR("任务执行失败", ErrorSeverity.MEDIUM, true, 2, 2000),
    TASK_TIMEOUT_ERROR("任务执行超时", ErrorSeverity.MEDIUM, true, 2, 5000),
    TASK_CANCELLED_ERROR("任务被取消", ErrorSeverity.LOW, false, 0, 0),
    CONFIGURATION_ERROR("配置错误", ErrorSeverity.HIGH, false, 0, 0),
    DEPENDENCY_ERROR("依赖服务错误", ErrorSeverity.HIGH, true, 2, 10000),
    
    // ==================== 未知错误 ====================
    UNKNOWN_ERROR("未知错误", ErrorSeverity.MEDIUM, true, 1, 5000),
    UNEXPECTED_ERROR("意外错误", ErrorSeverity.MEDIUM, true, 1, 3000);
    
    private final String description;
    private final ErrorSeverity severity;
    private final boolean retryable;
    private final int maxRetries;
    private final long retryDelayMs;
    
    CrawlerErrorType(String description, ErrorSeverity severity, boolean retryable, 
                    int maxRetries, long retryDelayMs) {
        this.description = description;
        this.severity = severity;
        this.retryable = retryable;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
    }
    
    // Getters
    public String getDescription() { return description; }
    public ErrorSeverity getSeverity() { return severity; }
    public boolean isRetryable() { return retryable; }
    public int getMaxRetries() { return maxRetries; }
    public long getRetryDelayMs() { return retryDelayMs; }
    
    /**
     * 错误严重级别
     */
    public enum ErrorSeverity {
        LOW(1, "轻微"),
        MEDIUM(2, "中等"), 
        HIGH(3, "严重"),
        CRITICAL(4, "致命");
        
        private final int level;
        private final String description;
        
        ErrorSeverity(int level, String description) {
            this.level = level;
            this.description = description;
        }
        
        public int getLevel() { return level; }
        public String getDescription() { return description; }
        
        public boolean isMoreSevereThan(ErrorSeverity other) {
            return this.level > other.level;
        }
    }
    
    /**
     * 根据异常类型和HTTP状态码自动分类错误
     * 
     * @param throwable 异常对象
     * @param statusCode HTTP状态码（可为null）
     * @return 错误类型
     */
    public static CrawlerErrorType classifyError(Throwable throwable, Integer statusCode) {
        if (throwable == null) {
            return UNKNOWN_ERROR;
        }
        
        String errorMessage = throwable.getMessage() != null ? 
            throwable.getMessage().toLowerCase() : "";
        String className = throwable.getClass().getSimpleName().toLowerCase();
        
        // HTTP状态码优先判断
        if (statusCode != null) {
            switch (statusCode) {
                case 400: return API_BAD_REQUEST;
                case 401: return API_UNAUTHORIZED;
                case 403: return API_FORBIDDEN;
                case 404: return API_NOT_FOUND;
                case 429: return API_RATE_LIMIT;
                case 500:
                case 502:
                case 503: return API_SERVICE_UNAVAILABLE;
                case 504: return API_SERVER_ERROR;
                default:
                    if (statusCode >= 400 && statusCode < 500) {
                        return API_CLIENT_ERROR;
                    } else if (statusCode >= 500) {
                        return API_SERVER_ERROR;
                    }
            }
        }
        
        // 根据异常类型判断
        if (className.contains("connecttimeout") || className.contains("sockettimeout")) {
            return CONNECTION_TIMEOUT;
        }
        
        if (className.contains("connectexception")) {
            return CONNECTION_REFUSED;
        }
        
        if (className.contains("unknownhost") || className.contains("noresolvehost")) {
            return DNS_RESOLUTION_ERROR;
        }
        
        if (className.contains("timeout")) {
            return NETWORK_TIMEOUT;
        }
        
        if (className.contains("outofmemory")) {
            return MEMORY_ERROR;
        }
        
        if (className.contains("sql") || className.contains("dataaccess")) {
            if (errorMessage.contains("timeout")) {
                return DATABASE_TIMEOUT_ERROR;
            } else if (errorMessage.contains("deadlock")) {
                return DATABASE_DEADLOCK_ERROR;
            } else if (errorMessage.contains("connection")) {
                return DATABASE_CONNECTION_ERROR;
            } else {
                return DATABASE_QUERY_ERROR;
            }
        }
        
        if (className.contains("json") || className.contains("parse")) {
            return DATA_PARSING_ERROR;
        }
        
        if (className.contains("validation")) {
            return DATA_VALIDATION_ERROR;
        }
        
        if (className.contains("io") || className.contains("file")) {
            return FILE_IO_ERROR;
        }
        
        if (className.contains("security") || className.contains("permission")) {
            return PERMISSION_ERROR;
        }
        
        if (className.contains("interrupted") || className.contains("cancelled")) {
            return TASK_CANCELLED_ERROR;
        }
        
        // 根据错误消息判断
        if (errorMessage.contains("rate limit") || errorMessage.contains("too many requests")) {
            return API_RATE_LIMIT;
        }
        
        if (errorMessage.contains("forbidden") || errorMessage.contains("access denied")) {
            return API_FORBIDDEN;
        }
        
        if (errorMessage.contains("unauthorized")) {
            return API_UNAUTHORIZED;
        }
        
        if (errorMessage.contains("not found")) {
            return API_NOT_FOUND;
        }
        
        if (errorMessage.contains("service unavailable") || errorMessage.contains("bad gateway")) {
            return API_SERVICE_UNAVAILABLE;
        }
        
        if (errorMessage.contains("timeout")) {
            return NETWORK_TIMEOUT;
        }
        
        if (errorMessage.contains("connection")) {
            return CONNECTION_REFUSED;
        }
        
        // 默认分类
        if (className.contains("runtime") || className.contains("illegal")) {
            return UNEXPECTED_ERROR;
        }
        
        return UNKNOWN_ERROR;
    }
    
    /**
     * 获取错误的推荐处理建议
     * 
     * @return 处理建议
     */
    public String getHandlingSuggestion() {
        switch (this) {
            case API_RATE_LIMIT:
                return "降低请求频率，增加请求间隔";
            case NETWORK_TIMEOUT:
            case CONNECTION_TIMEOUT:
                return "检查网络连接，增加超时时间";
            case API_SERVER_ERROR:
            case API_SERVICE_UNAVAILABLE:
                return "等待服务恢复，采用指数退避重试";
            case MEMORY_ERROR:
                return "检查内存使用，优化数据处理逻辑";
            case DATABASE_CONNECTION_ERROR:
                return "检查数据库连接配置，重建连接池";
            case DATA_PARSING_ERROR:
                return "检查数据格式，完善解析逻辑";
            case API_UNAUTHORIZED:
            case API_FORBIDDEN:
                return "检查API认证信息和权限设置";
            default:
                return "查看详细日志，分析具体原因";
        }
    }
}