package com.lcsc.service.crawler.network;

import com.lcsc.config.CrawlerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 重试服务
 * 提供智能重试机制，包含指数退避和随机抖动
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Service
public class RetryService {

    private static final Logger logger = LoggerFactory.getLogger(RetryService.class);

    @Autowired
    private CrawlerConfig crawlerConfig;

    /**
     * 执行带重试的操作
     * 
     * @param operation 要执行的操作
     * @param operationName 操作名称（用于日志）
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> T executeWithRetry(Supplier<T> operation, String operationName) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= crawlerConfig.getMaxRetry()) {
            try {
                if (attempt > 0) {
                    logger.info("重试操作: {} (第{}次尝试)", operationName, attempt + 1);
                }
                
                return operation.get();
                
            } catch (Exception e) {
                lastException = e;
                attempt++;
                
                if (attempt > crawlerConfig.getMaxRetry()) {
                    logger.error("操作失败，已达到最大重试次数: {} ({}次)", operationName, attempt);
                    break;
                }
                
                if (!shouldRetry(e)) {
                    logger.error("操作失败，不可重试的错误: {}", operationName, e);
                    break;
                }
                
                long backoffTime = calculateBackoffTime(attempt, e);
                logger.warn("操作失败，{}ms后进行第{}次重试: {}, 错误: {}", 
                    backoffTime, attempt + 1, operationName, e.getMessage());
                
                try {
                    Thread.sleep(backoffTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("重试等待被中断: {}", operationName);
                    throw new RuntimeException("重试等待被中断", ie);
                }
            }
        }
        
        throw new RuntimeException("操作最终失败: " + operationName, lastException);
    }

    /**
     * 判断是否应该重试
     * 
     * @param exception 异常
     * @return 是否应该重试
     */
    private boolean shouldRetry(Exception exception) {
        // 网络异常应该重试
        if (exception instanceof HttpClientService.NetworkException) {
            return true;
        }
        
        // HTTP状态码异常
        if (exception instanceof HttpStatusCodeException) {
            HttpStatusCodeException httpException = (HttpStatusCodeException) exception;
            int statusCode = httpException.getStatusCode().value();
            
            // 重试的状态码: 429(限流), 5xx(服务器错误), 503(服务不可用)
            return statusCode == 429 || statusCode == 403 || 
                   (statusCode >= 500 && statusCode < 600);
        }
        
        // 其他异常暂时不重试
        return false;
    }

    /**
     * 计算退避时间
     * 使用指数退避 + 随机抖动算法
     * 
     * @param attempt 重试次数
     * @param exception 异常
     * @return 退避时间(毫秒)
     */
    private long calculateBackoffTime(int attempt, Exception exception) {
        // 基础延迟时间
        long baseDelay = crawlerConfig.getDelay();
        
        // 处理429限流响应
        if (exception instanceof HttpStatusCodeException) {
            HttpStatusCodeException httpException = (HttpStatusCodeException) exception;
            if (httpException.getStatusCode().value() == 429) {
                // 对于429错误，使用更长的等待时间
                baseDelay = Math.max(baseDelay * 2, 5000);
            }
        }
        
        // 指数退避: baseDelay * 2^attempt
        long exponentialDelay = baseDelay * (1L << Math.min(attempt, 10)); // 最多2^10倍
        
        // 添加随机抖动 (±25%)
        double jitterFactor = 0.75 + (ThreadLocalRandom.current().nextDouble() * 0.5);
        long backoffTime = (long) (exponentialDelay * jitterFactor);
        
        // 设置最大退避时间 (5分钟)
        long maxBackoff = 5 * 60 * 1000;
        return Math.min(backoffTime, maxBackoff);
    }

    /**
     * 简单重试（使用固定延迟）
     * 
     * @param operation 要执行的操作
     * @param maxAttempts 最大尝试次数
     * @param delay 重试延迟(毫秒)
     * @param operationName 操作名称
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> T executeWithSimpleRetry(Supplier<T> operation, int maxAttempts, 
                                       long delay, String operationName) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxAttempts) {
            try {
                if (attempt > 0) {
                    logger.info("简单重试操作: {} (第{}次尝试)", operationName, attempt + 1);
                }
                
                return operation.get();
                
            } catch (Exception e) {
                lastException = e;
                attempt++;
                
                if (attempt >= maxAttempts) {
                    logger.error("简单重试失败，已达到最大尝试次数: {} ({}次)", operationName, attempt);
                    break;
                }
                
                logger.warn("简单重试，{}ms后进行第{}次尝试: {}, 错误: {}", 
                    delay, attempt + 1, operationName, e.getMessage());
                
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("简单重试等待被中断: {}", operationName);
                    throw new RuntimeException("简单重试等待被中断", ie);
                }
            }
        }
        
        throw new RuntimeException("简单重试最终失败: " + operationName, lastException);
    }
}
