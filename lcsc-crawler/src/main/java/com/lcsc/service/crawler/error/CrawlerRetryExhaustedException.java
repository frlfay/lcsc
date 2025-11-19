package com.lcsc.service.crawler.error;

/**
 * 爬虫重试耗尽异常
 * 当重试次数用完或不可重试时抛出
 * 
 * @author lcsc-crawler
 * @since 2025-09-06
 */
public class CrawlerRetryExhaustedException extends RuntimeException {
    
    private final String operationName;
    private final CrawlerErrorType errorType;
    private final int attemptCount;
    private final Throwable originalCause;
    
    public CrawlerRetryExhaustedException(String operationName, CrawlerErrorType errorType, 
                                        int attemptCount, Throwable originalCause) {
        super(String.format("操作 [%s] 重试失败，错误类型: %s，尝试次数: %d，原始错误: %s", 
            operationName, errorType.getDescription(), attemptCount, 
            originalCause != null ? originalCause.getMessage() : "未知"));
            
        this.operationName = operationName;
        this.errorType = errorType;
        this.attemptCount = attemptCount;
        this.originalCause = originalCause;
    }
    
    // Getters
    public String getOperationName() { return operationName; }
    public CrawlerErrorType getErrorType() { return errorType; }
    public int getAttemptCount() { return attemptCount; }
    public Throwable getOriginalCause() { return originalCause; }
    
    @Override
    public Throwable getCause() {
        return originalCause;
    }
}