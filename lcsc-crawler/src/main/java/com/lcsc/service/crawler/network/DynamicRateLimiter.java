package com.lcsc.service.crawler.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 动态频率控制器
 * 根据API响应情况智能调整请求间隔，避免触发反爬虫机制
 * 
 * @author lcsc-crawler
 * @since 2025-09-06
 */
@Component
public class DynamicRateLimiter {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamicRateLimiter.class);
    
    // 配置常量
    private static final long MIN_INTERVAL = 3000;        // 最小间隔3秒
    private static final long MAX_INTERVAL = 60000;       // 最大间隔60秒
    private static final long DEFAULT_INTERVAL = 5000;    // 默认间隔5秒
    private static final int MAX_CONSECUTIVE_ERRORS = 5;   // 最大连续错误数
    private static final double SUCCESS_DECREASE_FACTOR = 0.95; // 成功时减少因子
    private static final double ERROR_INCREASE_FACTOR = 2.0;    // 错误时增加因子
    
    // 各API端点的独立控制
    private final ConcurrentHashMap<String, EndpointRateLimit> endpointLimits = new ConcurrentHashMap<>();
    
    /**
     * 单个API端点的频率控制状态
     */
    private static class EndpointRateLimit {
        private volatile long currentInterval;
        private volatile long lastRequestTime;
        private final AtomicInteger consecutiveErrors;
        private final AtomicLong totalRequests;
        private final AtomicLong successfulRequests;
        private volatile long lastAdjustTime;
        
        public EndpointRateLimit() {
            this.currentInterval = DEFAULT_INTERVAL;
            this.lastRequestTime = 0;
            this.consecutiveErrors = new AtomicInteger(0);
            this.totalRequests = new AtomicLong(0);
            this.successfulRequests = new AtomicLong(0);
            this.lastAdjustTime = System.currentTimeMillis();
        }
    }
    
    /**
     * 获取指定端点的频率控制器
     */
    private EndpointRateLimit getEndpointLimit(String endpoint) {
        return endpointLimits.computeIfAbsent(endpoint, k -> new EndpointRateLimit());
    }
    
    /**
     * 等待下次请求 - 智能频率控制的核心方法
     * 
     * @param endpoint API端点
     */
    public void waitForNextRequest(String endpoint) {
        EndpointRateLimit limit = getEndpointLimit(endpoint);
        
        long now = System.currentTimeMillis();
        long elapsed = now - limit.lastRequestTime;
        
        // 如果距离上次请求还没达到间隔时间，则等待
        if (elapsed < limit.currentInterval) {
            long waitTime = limit.currentInterval - elapsed;
            try {
                logger.debug("端点 {} 等待 {}ms 后发起请求", endpoint, waitTime);
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("等待被中断: {}", e.getMessage());
                throw new RuntimeException("请求被中断", e);
            }
        }
        
        limit.lastRequestTime = System.currentTimeMillis();
        limit.totalRequests.incrementAndGet();
        
        logger.debug("端点 {} 当前间隔: {}ms, 总请求数: {}", 
            endpoint, limit.currentInterval, limit.totalRequests.get());
    }
    
    /**
     * 根据响应情况调整间隔
     * 
     * @param endpoint API端点
     * @param success 是否成功
     * @param statusCode HTTP状态码
     * @param responseTime 响应时间（毫秒）
     */
    public void adjustInterval(String endpoint, boolean success, HttpStatus statusCode, long responseTime) {
        EndpointRateLimit limit = getEndpointLimit(endpoint);
        long now = System.currentTimeMillis();
        
        if (success) {
            // 请求成功
            limit.consecutiveErrors.set(0);
            limit.successfulRequests.incrementAndGet();
            
            // 如果响应时间正常，适度减少间隔
            if (responseTime < 3000) { // 3秒以内认为正常
                long oldInterval = limit.currentInterval;
                limit.currentInterval = Math.max(MIN_INTERVAL, 
                    (long) (limit.currentInterval * SUCCESS_DECREASE_FACTOR));
                
                if (oldInterval != limit.currentInterval) {
                    logger.debug("端点 {} 成功响应，间隔调整: {}ms -> {}ms", 
                        endpoint, oldInterval, limit.currentInterval);
                }
            }
            
        } else {
            // 请求失败
            int errorCount = limit.consecutiveErrors.incrementAndGet();
            long oldInterval = limit.currentInterval;
            
            if (statusCode == HttpStatus.TOO_MANY_REQUESTS) {
                // 429错误，大幅增加间隔
                limit.currentInterval = Math.min(MAX_INTERVAL, limit.currentInterval * 3);
                logger.warn("端点 {} 触发频率限制(429)，间隔大幅调整: {}ms -> {}ms", 
                    endpoint, oldInterval, limit.currentInterval);
                    
            } else if (statusCode != null && statusCode.is5xxServerError()) {
                // 5xx错误，适度增加间隔
                limit.currentInterval = Math.min(MAX_INTERVAL, 
                    (long) (limit.currentInterval * ERROR_INCREASE_FACTOR));
                logger.warn("端点 {} 服务器错误({}), 间隔调整: {}ms -> {}ms", 
                    endpoint, statusCode.value(), oldInterval, limit.currentInterval);
                    
            } else if (errorCount > MAX_CONSECUTIVE_ERRORS) {
                // 连续错误过多，进一步增加间隔
                limit.currentInterval = Math.min(MAX_INTERVAL, 
                    (long) (limit.currentInterval * ERROR_INCREASE_FACTOR));
                logger.warn("端点 {} 连续错误{}次，间隔进一步调整: {}ms -> {}ms", 
                    endpoint, errorCount, oldInterval, limit.currentInterval);
            }
        }
        
        limit.lastAdjustTime = now;
        
        // 记录调整后的统计信息
        double successRate = limit.totalRequests.get() > 0 ? 
            (double) limit.successfulRequests.get() / limit.totalRequests.get() * 100 : 0;
            
        logger.debug("端点 {} 统计: 成功率{:.2f}%, 连续错误{}, 当前间隔{}ms", 
            endpoint, successRate, limit.consecutiveErrors.get(), limit.currentInterval);
    }
    
    /**
     * 获取当前间隔时间
     * 
     * @param endpoint API端点
     * @return 当前间隔时间（毫秒）
     */
    public long getCurrentInterval(String endpoint) {
        return getEndpointLimit(endpoint).currentInterval;
    }
    
    /**
     * 获取端点统计信息
     * 
     * @param endpoint API端点
     * @return 统计信息
     */
    public EndpointStats getStats(String endpoint) {
        EndpointRateLimit limit = getEndpointLimit(endpoint);
        
        double successRate = limit.totalRequests.get() > 0 ? 
            (double) limit.successfulRequests.get() / limit.totalRequests.get() * 100 : 0;
            
        return new EndpointStats(
            endpoint,
            limit.currentInterval,
            limit.totalRequests.get(),
            limit.successfulRequests.get(),
            successRate,
            limit.consecutiveErrors.get(),
            System.currentTimeMillis() - limit.lastAdjustTime
        );
    }
    
    /**
     * 获取所有端点的统计信息
     */
    public java.util.Map<String, EndpointStats> getAllStats() {
        java.util.Map<String, EndpointStats> allStats = new java.util.HashMap<>();
        for (String endpoint : endpointLimits.keySet()) {
            allStats.put(endpoint, getStats(endpoint));
        }
        return allStats;
    }
    
    /**
     * 重置指定端点的统计信息
     * 
     * @param endpoint API端点
     */
    public void resetStats(String endpoint) {
        EndpointRateLimit limit = getEndpointLimit(endpoint);
        limit.currentInterval = DEFAULT_INTERVAL;
        limit.consecutiveErrors.set(0);
        limit.totalRequests.set(0);
        limit.successfulRequests.set(0);
        limit.lastAdjustTime = System.currentTimeMillis();
        
        logger.info("端点 {} 的统计信息已重置", endpoint);
    }
    
    /**
     * 强制设置间隔时间（用于测试或紧急调整）
     * 
     * @param endpoint API端点
     * @param interval 间隔时间（毫秒）
     */
    public void forceSetInterval(String endpoint, long interval) {
        if (interval < MIN_INTERVAL || interval > MAX_INTERVAL) {
            throw new IllegalArgumentException(
                String.format("间隔时间必须在 %d-%d ms 之间", MIN_INTERVAL, MAX_INTERVAL));
        }
        
        EndpointRateLimit limit = getEndpointLimit(endpoint);
        long oldInterval = limit.currentInterval;
        limit.currentInterval = interval;
        
        logger.info("端点 {} 间隔时间被强制设置: {}ms -> {}ms", endpoint, oldInterval, interval);
    }
    
    /**
     * 端点统计信息
     */
    public static class EndpointStats {
        private final String endpoint;
        private final long currentInterval;
        private final long totalRequests;
        private final long successfulRequests;
        private final double successRate;
        private final int consecutiveErrors;
        private final long timeSinceLastAdjust;
        
        public EndpointStats(String endpoint, long currentInterval, long totalRequests, 
                           long successfulRequests, double successRate, int consecutiveErrors,
                           long timeSinceLastAdjust) {
            this.endpoint = endpoint;
            this.currentInterval = currentInterval;
            this.totalRequests = totalRequests;
            this.successfulRequests = successfulRequests;
            this.successRate = successRate;
            this.consecutiveErrors = consecutiveErrors;
            this.timeSinceLastAdjust = timeSinceLastAdjust;
        }
        
        // Getters
        public String getEndpoint() { return endpoint; }
        public long getCurrentInterval() { return currentInterval; }
        public long getTotalRequests() { return totalRequests; }
        public long getSuccessfulRequests() { return successfulRequests; }
        public double getSuccessRate() { return successRate; }
        public int getConsecutiveErrors() { return consecutiveErrors; }
        public long getTimeSinceLastAdjust() { return timeSinceLastAdjust; }
        
        @Override
        public String toString() {
            return String.format("EndpointStats{endpoint='%s', interval=%dms, success=%.2f%%, errors=%d}", 
                endpoint, currentInterval, successRate, consecutiveErrors);
        }
    }
}