package com.lcsc.service.crawler.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 智能重试处理器
 * 根据不同的错误类型采用不同的重试策略
 * 
 * @author lcsc-crawler
 * @since 2025-09-06
 */
@Component
public class SmartRetryHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartRetryHandler.class);
    
    /**
     * 执行带智能重试的操作
     * 
     * @param operation 要执行的操作
     * @param operationName 操作名称（用于日志）
     * @param context 重试上下文
     * @return CompletableFuture包装的结果
     */
    public <T> CompletableFuture<T> executeWithSmartRetry(
            Supplier<T> operation,
            String operationName,
            RetryContext context) {
        
        return CompletableFuture.supplyAsync(() -> {
            return executeWithRetryInternal(operation, operationName, context, 0);
        });
    }
    
    /**
     * 内部重试执行逻辑
     */
    private <T> T executeWithRetryInternal(
            Supplier<T> operation,
            String operationName,
            RetryContext context,
            int attemptNumber) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            logger.debug("执行操作 [{}]，第 {} 次尝试", operationName, attemptNumber + 1);
            
            T result = operation.get();
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("操作 [{}] 执行成功，耗时 {}ms，尝试次数: {}", 
                operationName, duration, attemptNumber + 1);
                
            // 重置重试上下文（成功后）
            context.reset();
            
            return result;
            
        } catch (Exception e) {
            return handleRetryableError(e, operation, operationName, context, attemptNumber, startTime);
        }
    }
    
    /**
     * 处理可重试错误
     */
    private <T> T handleRetryableError(
            Exception error,
            Supplier<T> operation,
            String operationName,
            RetryContext context,
            int attemptNumber,
            long startTime) {
        
        long duration = System.currentTimeMillis() - startTime;
        
        // 分类错误
        HttpStatus statusCode = extractHttpStatus(error);
        CrawlerErrorType errorType = CrawlerErrorType.classifyError(error, 
            statusCode != null ? statusCode.value() : null);
        
        // 更新重试上下文
        context.recordAttempt(errorType, duration);
        
        logger.warn("操作 [{}] 第 {} 次尝试失败: {} - {}, 耗时: {}ms", 
            operationName, attemptNumber + 1, errorType.getDescription(), 
            error.getMessage(), duration);
        
        // 判断是否应该重试
        if (!shouldRetry(errorType, attemptNumber, context)) {
            logger.error("操作 [{}] 重试次数耗尽或不可重试，最终失败: {}", 
                operationName, errorType.getDescription());
            throw new CrawlerRetryExhaustedException(operationName, errorType, attemptNumber + 1, error);
        }
        
        // 计算延迟时间
        long delay = calculateRetryDelay(errorType, attemptNumber, context);
        
        logger.info("操作 [{}] 将在 {}ms 后进行第 {} 次重试", 
            operationName, delay, attemptNumber + 2);
        
        // 等待后重试
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("重试被中断", ie);
        }
        
        return executeWithRetryInternal(operation, operationName, context, attemptNumber + 1);
    }
    
    /**
     * 判断是否应该重试
     */
    private boolean shouldRetry(CrawlerErrorType errorType, int attemptNumber, RetryContext context) {
        // 检查错误类型是否支持重试
        if (!errorType.isRetryable()) {
            logger.debug("错误类型 {} 不支持重试", errorType.getDescription());
            return false;
        }
        
        // 检查是否超过最大重试次数
        if (attemptNumber >= errorType.getMaxRetries()) {
            logger.debug("已达到错误类型 {} 的最大重试次数 {}", 
                errorType.getDescription(), errorType.getMaxRetries());
            return false;
        }
        
        // 检查重试上下文中的全局限制
        if (context.getTotalAttempts() >= context.getMaxTotalAttempts()) {
            logger.debug("已达到全局最大重试次数 {}", context.getMaxTotalAttempts());
            return false;
        }
        
        // 检查重试时间窗口
        if (context.getTotalRetryTime() >= context.getMaxRetryTimeWindow()) {
            logger.debug("已达到最大重试时间窗口 {}ms", context.getMaxRetryTimeWindow());
            return false;
        }
        
        // 特殊情况检查
        if (errorType == CrawlerErrorType.API_RATE_LIMIT && 
            context.getConsecutiveRateLimits() > 3) {
            logger.debug("连续触发频率限制超过3次，停止重试");
            return false;
        }
        
        return true;
    }
    
    /**
     * 计算重试延迟时间
     */
    private long calculateRetryDelay(CrawlerErrorType errorType, int attemptNumber, RetryContext context) {
        long baseDelay = errorType.getRetryDelayMs();
        
        // 不同错误类型采用不同的延迟策略
        switch (errorType) {
            case API_RATE_LIMIT:
                // 频率限制：使用指数退避 + 随机抖动
                long rateLimitDelay = baseDelay * (1L << Math.min(attemptNumber, 6)); // 最多64倍
                rateLimitDelay += ThreadLocalRandom.current().nextLong(1000, 5000); // 随机1-5秒
                return Math.min(rateLimitDelay, 300000); // 最长5分钟
                
            case NETWORK_TIMEOUT:
            case CONNECTION_TIMEOUT:
                // 网络超时：线性增长 + 随机抖动
                long networkDelay = baseDelay + (attemptNumber * 2000);
                networkDelay += ThreadLocalRandom.current().nextLong(500, 2000);
                return Math.min(networkDelay, 60000); // 最长1分钟
                
            case API_SERVER_ERROR:
            case API_SERVICE_UNAVAILABLE:
                // 服务器错误：指数退避
                long serverErrorDelay = baseDelay * (1L << attemptNumber);
                return Math.min(serverErrorDelay, 120000); // 最长2分钟
                
            case DATABASE_CONNECTION_ERROR:
            case DATABASE_TIMEOUT_ERROR:
                // 数据库错误：固定延迟 + 小随机抖动
                return baseDelay + ThreadLocalRandom.current().nextLong(500, 1500);
                
            case MEMORY_ERROR:
                // 内存错误：较长延迟让系统恢复
                return baseDelay + ThreadLocalRandom.current().nextLong(5000, 15000);
                
            default:
                // 默认策略：简单指数退避
                long defaultDelay = baseDelay * (1L << Math.min(attemptNumber, 4));
                defaultDelay += ThreadLocalRandom.current().nextLong(100, 1000);
                return Math.min(defaultDelay, 30000); // 最长30秒
        }
    }
    
    /**
     * 从异常中提取HTTP状态码
     */
    private HttpStatus extractHttpStatus(Exception error) {
        if (error instanceof HttpClientErrorException) {
            HttpClientErrorException clientError = (HttpClientErrorException) error;
            return HttpStatus.valueOf(clientError.getStatusCode().value());
        } else if (error instanceof HttpServerErrorException) {
            HttpServerErrorException serverError = (HttpServerErrorException) error;
            return HttpStatus.valueOf(serverError.getStatusCode().value());
        }
        return null;
    }
    
    /**
     * 重试上下文类
     */
    public static class RetryContext {
        private final long maxRetryTimeWindow; // 最大重试时间窗口（毫秒）
        private final int maxTotalAttempts;    // 最大总重试次数
        
        private long startTime;
        private long totalRetryTime;
        private int totalAttempts;
        private int consecutiveRateLimits;
        private CrawlerErrorType lastErrorType;
        
        public RetryContext(long maxRetryTimeWindow, int maxTotalAttempts) {
            this.maxRetryTimeWindow = maxRetryTimeWindow;
            this.maxTotalAttempts = maxTotalAttempts;
            this.startTime = System.currentTimeMillis();
            this.totalRetryTime = 0;
            this.totalAttempts = 0;
            this.consecutiveRateLimits = 0;
        }
        
        public void recordAttempt(CrawlerErrorType errorType, long attemptDuration) {
            this.totalAttempts++;
            this.totalRetryTime = System.currentTimeMillis() - this.startTime;
            
            if (errorType == CrawlerErrorType.API_RATE_LIMIT) {
                if (this.lastErrorType == CrawlerErrorType.API_RATE_LIMIT) {
                    this.consecutiveRateLimits++;
                } else {
                    this.consecutiveRateLimits = 1;
                }
            } else {
                this.consecutiveRateLimits = 0;
            }
            
            this.lastErrorType = errorType;
        }
        
        public void reset() {
            this.startTime = System.currentTimeMillis();
            this.totalRetryTime = 0;
            this.totalAttempts = 0;
            this.consecutiveRateLimits = 0;
            this.lastErrorType = null;
        }
        
        // Getters
        public long getMaxRetryTimeWindow() { return maxRetryTimeWindow; }
        public int getMaxTotalAttempts() { return maxTotalAttempts; }
        public long getTotalRetryTime() { return totalRetryTime; }
        public int getTotalAttempts() { return totalAttempts; }
        public int getConsecutiveRateLimits() { return consecutiveRateLimits; }
    }
    
    /**
     * 默认重试上下文
     */
    public static RetryContext createDefaultContext() {
        return new RetryContext(300000, 10); // 5分钟，最多10次重试
    }
    
    /**
     * API调用专用重试上下文
     */
    public static RetryContext createApiContext() {
        return new RetryContext(600000, 15); // 10分钟，最多15次重试
    }
    
    /**
     * 数据库操作专用重试上下文
     */
    public static RetryContext createDatabaseContext() {
        return new RetryContext(120000, 5); // 2分钟，最多5次重试
    }
}