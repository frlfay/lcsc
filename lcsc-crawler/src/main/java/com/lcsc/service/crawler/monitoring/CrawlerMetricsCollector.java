package com.lcsc.service.crawler.monitoring;

import com.lcsc.service.crawler.network.DynamicRateLimiter;
import com.lcsc.service.crawler.memory.MemoryOptimizer;
import com.lcsc.service.crawler.data.BatchDataProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * 爬虫指标收集器
 * 收集各种业务和系统指标，提供性能监控和分析能力
 * 
 * @author lcsc-crawler
 * @since 2025-09-06
 */
@Component
public class CrawlerMetricsCollector {
    
    private static final Logger logger = LoggerFactory.getLogger(CrawlerMetricsCollector.class);
    
    @Autowired
    private DynamicRateLimiter rateLimiter;
    
    @Autowired
    private MemoryOptimizer memoryOptimizer;
    
    @Autowired
    private BatchDataProcessor batchDataProcessor;
    
    // 业务指标计数器
    private final AtomicLong totalApiCalls = new AtomicLong(0);
    private final AtomicLong successfulApiCalls = new AtomicLong(0);
    private final AtomicLong failedApiCalls = new AtomicLong(0);
    private final AtomicLong totalProductsProcessed = new AtomicLong(0);
    private final AtomicLong totalCategoriesProcessed = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    
    // 性能指标
    private final ConcurrentHashMap<String, Long> apiResponseTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> errorCounts = new ConcurrentHashMap<>();
    private final List<MetricSnapshot> performanceHistory = new ArrayList<>();
    
    // 实时统计
    private volatile LocalDateTime lastResetTime = LocalDateTime.now();
    private volatile double currentThroughput = 0.0;
    private volatile double averageResponseTime = 0.0;
    private volatile double errorRate = 0.0;
    
    /**
     * 记录API调用指标
     */
    public void recordApiCall(String endpoint, long responseTime, boolean success) {
        totalApiCalls.incrementAndGet();
        
        if (success) {
            successfulApiCalls.incrementAndGet();
        } else {
            failedApiCalls.incrementAndGet();
            totalErrors.incrementAndGet();
        }
        
        // 记录响应时间
        apiResponseTimes.put(endpoint + "_" + System.currentTimeMillis(), responseTime);
        
        // 清理旧的响应时间记录（保留最近5分钟）
        long fiveMinutesAgo = System.currentTimeMillis() - 300000;
        apiResponseTimes.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            long timestamp = Long.parseLong(key.substring(key.lastIndexOf('_') + 1));
            return timestamp < fiveMinutesAgo;
        });
        
        logger.debug("API调用记录: endpoint={}, responseTime={}ms, success={}", 
            endpoint, responseTime, success);
    }
    
    /**
     * 记录产品处理指标
     */
    public void recordProductsProcessed(int count, String category) {
        totalProductsProcessed.addAndGet(count);
        
        logger.debug("产品处理记录: count={}, category={}", count, category);
    }
    
    /**
     * 记录分类处理指标
     */
    public void recordCategoryProcessed(String categoryName) {
        totalCategoriesProcessed.incrementAndGet();
        
        logger.debug("分类处理记录: category={}", categoryName);
    }
    
    /**
     * 记录错误指标
     */
    public void recordError(String errorType, String operation) {
        totalErrors.incrementAndGet();
        
        String key = errorType + "_" + operation;
        errorCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        
        logger.debug("错误记录: type={}, operation={}", errorType, operation);
    }
    
    /**
     * 获取当前性能指标
     */
    public CrawlerMetrics getCurrentMetrics() {
        // 计算实时指标
        calculateRealTimeMetrics();
        
        // 获取各组件状态
        DynamicRateLimiter.EndpointStats rateLimiterStats = 
            rateLimiter.getAllStats().values().stream().findFirst().orElse(null);
            
        MemoryOptimizer.MemoryOptimizerStats memoryStats = memoryOptimizer.getStats();
        BatchDataProcessor.BatchProcessorStats batchStats = batchDataProcessor.getStats();
        
        return new CrawlerMetrics(
            // 业务指标
            totalApiCalls.get(),
            successfulApiCalls.get(),
            failedApiCalls.get(),
            totalProductsProcessed.get(),
            totalCategoriesProcessed.get(),
            totalErrors.get(),
            
            // 性能指标
            currentThroughput,
            averageResponseTime,
            errorRate,
            
            // 组件状态
            rateLimiterStats,
            memoryStats,
            batchStats,
            
            // 时间戳
            LocalDateTime.now(),
            lastResetTime
        );
    }
    
    /**
     * 计算实时性能指标
     */
    private void calculateRealTimeMetrics() {
        long currentTime = System.currentTimeMillis();
        long fiveMinutesAgo = currentTime - 300000;
        
        // 计算5分钟内的平均响应时间
        List<Long> recentResponseTimes = apiResponseTimes.entrySet().stream()
            .filter(entry -> {
                String key = entry.getKey();
                long timestamp = Long.parseLong(key.substring(key.lastIndexOf('_') + 1));
                return timestamp >= fiveMinutesAgo;
            })
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
        
        if (!recentResponseTimes.isEmpty()) {
            averageResponseTime = recentResponseTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
        }
        
        // 计算吞吐量（每分钟处理的API调用数）
        long recentApiCalls = apiResponseTimes.entrySet().stream()
            .filter(entry -> {
                String key = entry.getKey();
                long timestamp = Long.parseLong(key.substring(key.lastIndexOf('_') + 1));
                return timestamp >= currentTime - 60000; // 最近1分钟
            })
            .count();
        
        currentThroughput = recentApiCalls; // 每分钟调用数
        
        // 计算错误率
        long totalCalls = totalApiCalls.get();
        if (totalCalls > 0) {
            errorRate = (double) failedApiCalls.get() / totalCalls * 100;
        }
    }
    
    /**
     * 获取性能历史数据
     */
    public List<MetricSnapshot> getPerformanceHistory() {
        return new ArrayList<>(performanceHistory);
    }
    
    /**
     * 获取错误统计
     */
    public Map<String, Integer> getErrorStatistics() {
        return errorCounts.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().get()
            ));
    }
    
    /**
     * 获取API端点统计
     */
    public Map<String, DynamicRateLimiter.EndpointStats> getApiEndpointStats() {
        return rateLimiter.getAllStats();
    }
    
    /**
     * 定期收集性能快照
     */
    @Scheduled(fixedRate = 60000) // 每分钟收集一次
    public void collectPerformanceSnapshot() {
        try {
            MetricSnapshot snapshot = new MetricSnapshot(
                LocalDateTime.now(),
                currentThroughput,
                averageResponseTime,
                errorRate,
                memoryOptimizer.getCurrentMemoryStats().getUsagePercentage(),
                batchDataProcessor.getStats().getCurrentQueueSize()
            );
            
            performanceHistory.add(snapshot);
            
            // 保留最近24小时的数据（1440个样本）
            if (performanceHistory.size() > 1440) {
                performanceHistory.remove(0);
            }
            
            logger.debug("性能快照收集完成: {}", snapshot);
            
        } catch (Exception e) {
            logger.error("收集性能快照时发生错误", e);
        }
    }
    
    /**
     * 定期生成性能报告
     */
    @Scheduled(fixedRate = 3600000) // 每小时生成一次报告
    public void generatePerformanceReport() {
        try {
            CrawlerMetrics metrics = getCurrentMetrics();
            
            logger.info("=== 爬虫性能报告 ===");
            logger.info("时间范围: {} - {}", lastResetTime, metrics.getTimestamp());
            logger.info("API调用: 总计 {}, 成功 {}, 失败 {}, 成功率 {:.2f}%", 
                metrics.getTotalApiCalls(),
                metrics.getSuccessfulApiCalls(),
                metrics.getFailedApiCalls(),
                metrics.getTotalApiCalls() > 0 ? 
                    (double) metrics.getSuccessfulApiCalls() / metrics.getTotalApiCalls() * 100 : 0);
            logger.info("数据处理: 产品 {} 个, 分类 {} 个", 
                metrics.getTotalProductsProcessed(), metrics.getTotalCategoriesProcessed());
            logger.info("性能指标: 吞吐量 {:.2f} 次/分钟, 平均响应时间 {:.2f} ms, 错误率 {:.2f}%", 
                metrics.getCurrentThroughput(), metrics.getAverageResponseTime(), metrics.getErrorRate());
            
            if (metrics.getMemoryStats() != null) {
                logger.info("内存使用: {:.2f}%, GC次数 {}", 
                    metrics.getMemoryStats().getCurrentMemoryStats().getUsagePercentage(),
                    metrics.getMemoryStats().getGcCount());
            }
            
            if (metrics.getBatchStats() != null) {
                logger.info("批处理: 队列大小 {}, 已处理 {} 批次", 
                    metrics.getBatchStats().getCurrentQueueSize(),
                    metrics.getBatchStats().getBatchesProcessed());
            }
            
            // 错误统计
            Map<String, Integer> errorStats = getErrorStatistics();
            if (!errorStats.isEmpty()) {
                logger.info("错误统计:");
                errorStats.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .forEach(entry -> logger.info("  {}: {} 次", entry.getKey(), entry.getValue()));
            }
            
            logger.info("=== 报告结束 ===");
            
        } catch (Exception e) {
            logger.error("生成性能报告时发生错误", e);
        }
    }
    
    /**
     * 重置统计数据
     */
    public void resetMetrics() {
        totalApiCalls.set(0);
        successfulApiCalls.set(0);
        failedApiCalls.set(0);
        totalProductsProcessed.set(0);
        totalCategoriesProcessed.set(0);
        totalErrors.set(0);
        
        apiResponseTimes.clear();
        errorCounts.clear();
        performanceHistory.clear();
        
        lastResetTime = LocalDateTime.now();
        
        // 重置组件统计
        memoryOptimizer.resetStats();
        batchDataProcessor.resetStats();
        
        logger.info("指标统计数据已重置");
    }
    
    /**
     * 检查系统健康状态
     */
    public SystemHealthStatus checkSystemHealth() {
        try {
            CrawlerMetrics metrics = getCurrentMetrics();
            List<String> issues = new ArrayList<>();
            HealthLevel overallHealth = HealthLevel.HEALTHY;
            
            // 检查错误率
            if (metrics.getErrorRate() > 10) {
                issues.add("错误率过高: " + String.format("%.2f%%", metrics.getErrorRate()));
                overallHealth = HealthLevel.WARNING;
            }
            if (metrics.getErrorRate() > 25) {
                overallHealth = HealthLevel.CRITICAL;
            }
            
            // 检查响应时间
            if (metrics.getAverageResponseTime() > 5000) {
                issues.add("平均响应时间过长: " + String.format("%.2f ms", metrics.getAverageResponseTime()));
                if (overallHealth == HealthLevel.HEALTHY) {
                    overallHealth = HealthLevel.WARNING;
                }
            }
            
            // 检查内存使用
            if (metrics.getMemoryStats() != null) {
                double memoryUsage = metrics.getMemoryStats().getCurrentMemoryStats().getUsagePercentage();
                if (memoryUsage > 85) {
                    issues.add("内存使用率过高: " + String.format("%.2f%%", memoryUsage));
                    overallHealth = HealthLevel.CRITICAL;
                } else if (memoryUsage > 75) {
                    issues.add("内存使用率较高: " + String.format("%.2f%%", memoryUsage));
                    if (overallHealth == HealthLevel.HEALTHY) {
                        overallHealth = HealthLevel.WARNING;
                    }
                }
            }
            
            // 检查批处理队列
            if (metrics.getBatchStats() != null) {
                int queueSize = metrics.getBatchStats().getCurrentQueueSize();
                if (queueSize > 5000) {
                    issues.add("批处理队列积压严重: " + queueSize + " 个待处理");
                    overallHealth = HealthLevel.CRITICAL;
                } else if (queueSize > 2000) {
                    issues.add("批处理队列积压较多: " + queueSize + " 个待处理");
                    if (overallHealth == HealthLevel.HEALTHY) {
                        overallHealth = HealthLevel.WARNING;
                    }
                }
            }
            
            return new SystemHealthStatus(overallHealth, issues, LocalDateTime.now());
            
        } catch (Exception e) {
            logger.error("检查系统健康状态时发生错误", e);
            return new SystemHealthStatus(HealthLevel.UNKNOWN, 
                List.of("健康检查失败: " + e.getMessage()), LocalDateTime.now());
        }
    }
    
    // 内部类定义
    
    /**
     * 健康状态级别
     */
    public enum HealthLevel {
        HEALTHY("健康"),
        WARNING("警告"),
        CRITICAL("严重"),
        UNKNOWN("未知");
        
        private final String description;
        
        HealthLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * 系统健康状态
     */
    public static class SystemHealthStatus {
        private final HealthLevel level;
        private final List<String> issues;
        private final LocalDateTime checkTime;
        
        public SystemHealthStatus(HealthLevel level, List<String> issues, LocalDateTime checkTime) {
            this.level = level;
            this.issues = new ArrayList<>(issues);
            this.checkTime = checkTime;
        }
        
        public HealthLevel getLevel() { return level; }
        public List<String> getIssues() { return new ArrayList<>(issues); }
        public LocalDateTime getCheckTime() { return checkTime; }
        public boolean isHealthy() { return level == HealthLevel.HEALTHY; }
    }
    
    /**
     * 性能快照
     */
    public static class MetricSnapshot {
        private final LocalDateTime timestamp;
        private final double throughput;
        private final double responseTime;
        private final double errorRate;
        private final double memoryUsage;
        private final int queueSize;
        
        public MetricSnapshot(LocalDateTime timestamp, double throughput, double responseTime, 
                            double errorRate, double memoryUsage, int queueSize) {
            this.timestamp = timestamp;
            this.throughput = throughput;
            this.responseTime = responseTime;
            this.errorRate = errorRate;
            this.memoryUsage = memoryUsage;
            this.queueSize = queueSize;
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public double getThroughput() { return throughput; }
        public double getResponseTime() { return responseTime; }
        public double getErrorRate() { return errorRate; }
        public double getMemoryUsage() { return memoryUsage; }
        public int getQueueSize() { return queueSize; }
        
        @Override
        public String toString() {
            return String.format("MetricSnapshot{time=%s, throughput=%.2f, responseTime=%.2f, errorRate=%.2f, memory=%.2f%%, queue=%d}",
                timestamp, throughput, responseTime, errorRate, memoryUsage, queueSize);
        }
    }
    
    /**
     * 爬虫综合指标
     */
    public static class CrawlerMetrics {
        private final long totalApiCalls;
        private final long successfulApiCalls;
        private final long failedApiCalls;
        private final long totalProductsProcessed;
        private final long totalCategoriesProcessed;
        private final long totalErrors;
        
        private final double currentThroughput;
        private final double averageResponseTime;
        private final double errorRate;
        
        private final DynamicRateLimiter.EndpointStats rateLimiterStats;
        private final MemoryOptimizer.MemoryOptimizerStats memoryStats;
        private final BatchDataProcessor.BatchProcessorStats batchStats;
        
        private final LocalDateTime timestamp;
        private final LocalDateTime lastResetTime;
        
        public CrawlerMetrics(long totalApiCalls, long successfulApiCalls, long failedApiCalls,
                            long totalProductsProcessed, long totalCategoriesProcessed, long totalErrors,
                            double currentThroughput, double averageResponseTime, double errorRate,
                            DynamicRateLimiter.EndpointStats rateLimiterStats,
                            MemoryOptimizer.MemoryOptimizerStats memoryStats,
                            BatchDataProcessor.BatchProcessorStats batchStats,
                            LocalDateTime timestamp, LocalDateTime lastResetTime) {
            this.totalApiCalls = totalApiCalls;
            this.successfulApiCalls = successfulApiCalls;
            this.failedApiCalls = failedApiCalls;
            this.totalProductsProcessed = totalProductsProcessed;
            this.totalCategoriesProcessed = totalCategoriesProcessed;
            this.totalErrors = totalErrors;
            this.currentThroughput = currentThroughput;
            this.averageResponseTime = averageResponseTime;
            this.errorRate = errorRate;
            this.rateLimiterStats = rateLimiterStats;
            this.memoryStats = memoryStats;
            this.batchStats = batchStats;
            this.timestamp = timestamp;
            this.lastResetTime = lastResetTime;
        }
        
        // Getters
        public long getTotalApiCalls() { return totalApiCalls; }
        public long getSuccessfulApiCalls() { return successfulApiCalls; }
        public long getFailedApiCalls() { return failedApiCalls; }
        public long getTotalProductsProcessed() { return totalProductsProcessed; }
        public long getTotalCategoriesProcessed() { return totalCategoriesProcessed; }
        public long getTotalErrors() { return totalErrors; }
        public double getCurrentThroughput() { return currentThroughput; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public double getErrorRate() { return errorRate; }
        public DynamicRateLimiter.EndpointStats getRateLimiterStats() { return rateLimiterStats; }
        public MemoryOptimizer.MemoryOptimizerStats getMemoryStats() { return memoryStats; }
        public BatchDataProcessor.BatchProcessorStats getBatchStats() { return batchStats; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public LocalDateTime getLastResetTime() { return lastResetTime; }
    }
}