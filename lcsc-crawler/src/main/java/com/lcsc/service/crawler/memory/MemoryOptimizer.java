package com.lcsc.service.crawler.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 内存管理优化器
 * 提供内存监控、流式处理和垃圾回收建议功能
 * 
 * @author lcsc-crawler
 * @since 2025-09-06
 */
@Component
public class MemoryOptimizer {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryOptimizer.class);
    
    @Value("${lcsc.crawler.memory.warning-threshold:0.80}")
    private double memoryWarningThreshold;
    
    @Value("${lcsc.crawler.memory.critical-threshold:0.90}")
    private double memoryCriticalThreshold;
    
    @Value("${lcsc.crawler.memory.auto-gc-threshold:0.85}")
    private double autoGcThreshold;
    
    @Value("${lcsc.crawler.memory.stream-batch-size:1000}")
    private int streamBatchSize;
    
    private final AtomicLong lastGcTime = new AtomicLong(0);
    private final AtomicLong gcCounter = new AtomicLong(0);
    private final AtomicLong memoryAlerts = new AtomicLong(0);
    
    /**
     * 获取当前内存使用情况
     */
    public MemoryStats getCurrentMemoryStats() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;
        
        double usageRatio = (double) usedMemory / maxMemory;
        
        return new MemoryStats(
            maxMemory,
            totalMemory,
            usedMemory,
            freeMemory,
            usageRatio,
            heapUsage.getUsed(),
            heapUsage.getMax(),
            nonHeapUsage.getUsed(),
            nonHeapUsage.getMax()
        );
    }
    
    /**
     * 检查内存状态并采取相应措施
     */
    public MemoryStatus checkMemoryStatus() {
        MemoryStats stats = getCurrentMemoryStats();
        double usageRatio = stats.getUsageRatio();
        
        if (usageRatio >= memoryCriticalThreshold) {
            memoryAlerts.incrementAndGet();
            logger.error("内存使用率达到临界值: {:.2f}% (阈值: {:.2f}%)", 
                usageRatio * 100, memoryCriticalThreshold * 100);
                
            // 强制垃圾回收
            performGarbageCollection("内存临界值触发");
            
            return MemoryStatus.CRITICAL;
            
        } else if (usageRatio >= memoryWarningThreshold) {
            logger.warn("内存使用率达到警告阈值: {:.2f}% (阈值: {:.2f}%)", 
                usageRatio * 100, memoryWarningThreshold * 100);
                
            return MemoryStatus.WARNING;
            
        } else if (usageRatio >= autoGcThreshold) {
            // 自动垃圾回收建议
            long timeSinceLastGc = System.currentTimeMillis() - lastGcTime.get();
            if (timeSinceLastGc > 300000) { // 5分钟后才建议GC
                performGarbageCollection("自动GC阈值触发");
            }
            
            return MemoryStatus.HIGH;
        }
        
        return MemoryStatus.NORMAL;
    }
    
    /**
     * 执行垃圾回收
     */
    public void performGarbageCollection(String reason) {
        long beforeUsed = getCurrentMemoryStats().getUsedMemory();
        long startTime = System.currentTimeMillis();
        
        logger.info("执行垃圾回收: {}", reason);
        
        // 执行垃圾回收
        System.gc();
        
        // 等待一小段时间让GC完成
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long afterUsed = getCurrentMemoryStats().getUsedMemory();
        long duration = System.currentTimeMillis() - startTime;
        long freedMemory = beforeUsed - afterUsed;
        
        lastGcTime.set(System.currentTimeMillis());
        gcCounter.incrementAndGet();
        
        logger.info("垃圾回收完成: 释放内存 {:.2f}MB, 耗时 {}ms", 
            freedMemory / 1024.0 / 1024.0, duration);
    }
    
    /**
     * 流式处理大数据集，避免内存溢出
     */
    public <T, R> Stream<R> processLargeDataSet(
            Stream<T> dataStream,
            Function<T, R> processor) {
        
        return processLargeDataSet(dataStream, processor, streamBatchSize);
    }
    
    /**
     * 带自定义批次大小的流式处理
     */
    public <T, R> Stream<R> processLargeDataSet(
            Stream<T> dataStream,
            Function<T, R> processor,
            int batchSize) {
        
        final AtomicLong processedCount = new AtomicLong(0);
        
        return dataStream
            .peek(item -> {
                // 每处理一批数据后检查内存
                long count = processedCount.incrementAndGet();
                if (count % batchSize == 0) {
                    MemoryStatus status = checkMemoryStatus();
                    
                    if (status == MemoryStatus.CRITICAL) {
                        logger.warn("内存压力过大，暂停流式处理进行垃圾回收");
                        performGarbageCollection("流式处理内存压力");
                        
                        // 暂停一下让系统恢复
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            })
            .map(processor);
    }
    
    /**
     * 创建内存友好的分页处理器
     */
    public <T> PageProcessor<T> createPageProcessor(int pageSize) {
        return new PageProcessor<>(pageSize, this);
    }
    
    /**
     * 定期内存监控和清理
     */
    @Scheduled(fixedRate = 60000) // 每分钟检查一次
    public void scheduleMemoryCheck() {
        MemoryStatus status = checkMemoryStatus();
        MemoryStats stats = getCurrentMemoryStats();
        
        // 记录内存使用情况（只在DEBUG级别）
        if (logger.isDebugEnabled()) {
            logger.debug("内存状态检查: 使用率 {:.2f}%, 状态: {}, 已用: {:.2f}MB, 可用: {:.2f}MB", 
                stats.getUsageRatio() * 100,
                status,
                stats.getUsedMemory() / 1024.0 / 1024.0,
                stats.getFreeMemory() / 1024.0 / 1024.0);
        }
        
        // 在内存使用率超过自动GC阈值时，且距离上次GC超过5分钟时，执行GC
        if (status.ordinal() >= MemoryStatus.HIGH.ordinal()) {
            long timeSinceLastGc = System.currentTimeMillis() - lastGcTime.get();
            if (timeSinceLastGc > 300000) { // 5分钟
                performGarbageCollection("定期内存检查");
            }
        }
    }
    
    /**
     * 获取内存优化器统计信息
     */
    public MemoryOptimizerStats getStats() {
        return new MemoryOptimizerStats(
            gcCounter.get(),
            memoryAlerts.get(),
            lastGcTime.get(),
            getCurrentMemoryStats()
        );
    }
    
    /**
     * 重置统计信息
     */
    public void resetStats() {
        gcCounter.set(0);
        memoryAlerts.set(0);
        lastGcTime.set(System.currentTimeMillis());
        logger.info("内存优化器统计信息已重置");
    }
    
    /**
     * 内存状态枚举
     */
    public enum MemoryStatus {
        NORMAL("正常"),
        HIGH("较高"),
        WARNING("警告"),
        CRITICAL("临界");
        
        private final String description;
        
        MemoryStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * 内存使用统计
     */
    public static class MemoryStats {
        private final long maxMemory;
        private final long totalMemory;
        private final long usedMemory;
        private final long freeMemory;
        private final double usageRatio;
        private final long heapUsed;
        private final long heapMax;
        private final long nonHeapUsed;
        private final long nonHeapMax;
        
        public MemoryStats(long maxMemory, long totalMemory, long usedMemory, long freeMemory,
                          double usageRatio, long heapUsed, long heapMax, 
                          long nonHeapUsed, long nonHeapMax) {
            this.maxMemory = maxMemory;
            this.totalMemory = totalMemory;
            this.usedMemory = usedMemory;
            this.freeMemory = freeMemory;
            this.usageRatio = usageRatio;
            this.heapUsed = heapUsed;
            this.heapMax = heapMax;
            this.nonHeapUsed = nonHeapUsed;
            this.nonHeapMax = nonHeapMax;
        }
        
        // Getters
        public long getMaxMemory() { return maxMemory; }
        public long getTotalMemory() { return totalMemory; }
        public long getUsedMemory() { return usedMemory; }
        public long getFreeMemory() { return freeMemory; }
        public double getUsageRatio() { return usageRatio; }
        public long getHeapUsed() { return heapUsed; }
        public long getHeapMax() { return heapMax; }
        public long getNonHeapUsed() { return nonHeapUsed; }
        public long getNonHeapMax() { return nonHeapMax; }
        
        // 便利方法
        public double getUsagePercentage() { return usageRatio * 100; }
        public double getUsedMemoryMB() { return usedMemory / 1024.0 / 1024.0; }
        public double getFreeMemoryMB() { return freeMemory / 1024.0 / 1024.0; }
        public double getMaxMemoryMB() { return maxMemory / 1024.0 / 1024.0; }
    }
    
    /**
     * 内存优化器统计信息
     */
    public static class MemoryOptimizerStats {
        private final long gcCount;
        private final long memoryAlerts;
        private final long lastGcTime;
        private final MemoryStats currentMemoryStats;
        
        public MemoryOptimizerStats(long gcCount, long memoryAlerts, long lastGcTime, 
                                  MemoryStats currentMemoryStats) {
            this.gcCount = gcCount;
            this.memoryAlerts = memoryAlerts;
            this.lastGcTime = lastGcTime;
            this.currentMemoryStats = currentMemoryStats;
        }
        
        // Getters
        public long getGcCount() { return gcCount; }
        public long getMemoryAlerts() { return memoryAlerts; }
        public long getLastGcTime() { return lastGcTime; }
        public MemoryStats getCurrentMemoryStats() { return currentMemoryStats; }
        public long getTimeSinceLastGc() { return System.currentTimeMillis() - lastGcTime; }
    }
    
    /**
     * 分页处理器
     */
    public static class PageProcessor<T> {
        private final int pageSize;
        private final MemoryOptimizer memoryOptimizer;
        
        public PageProcessor(int pageSize, MemoryOptimizer memoryOptimizer) {
            this.pageSize = pageSize;
            this.memoryOptimizer = memoryOptimizer;
        }
        
        /**
         * 处理分页数据
         */
        public void processPages(java.util.List<T> allData, java.util.function.Consumer<java.util.List<T>> pageProcessor) {
            for (int i = 0; i < allData.size(); i += pageSize) {
                int endIndex = Math.min(i + pageSize, allData.size());
                java.util.List<T> page = allData.subList(i, endIndex);
                
                // 在处理每页之前检查内存
                MemoryStatus status = memoryOptimizer.checkMemoryStatus();
                if (status == MemoryStatus.CRITICAL) {
                    memoryOptimizer.performGarbageCollection("分页处理内存压力");
                }
                
                try {
                    pageProcessor.accept(page);
                } catch (Exception e) {
                    logger.error("分页处理失败，页码: {}-{}", i, endIndex, e);
                    throw new RuntimeException("分页处理失败", e);
                }
            }
        }
        
        /**
         * 异步处理分页数据
         */
        public java.util.concurrent.CompletableFuture<Void> processPagesAsync(
                java.util.List<T> allData, 
                java.util.function.Consumer<java.util.List<T>> pageProcessor) {
            
            return java.util.concurrent.CompletableFuture.runAsync(() -> processPages(allData, pageProcessor));
        }
    }
}