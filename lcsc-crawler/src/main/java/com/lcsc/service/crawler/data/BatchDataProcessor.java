package com.lcsc.service.crawler.data;

import com.lcsc.entity.Product;
import com.lcsc.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 批量数据处理器
 * 优化数据库写入性能，减少IO开销
 * 
 * @author lcsc-crawler
 * @since 2025-09-06
 */
@Component
public class BatchDataProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(BatchDataProcessor.class);
    
    @Value("${lcsc.crawler.batch.product-batch-size:1000}")
    private int productBatchSize;
    
    @Value("${lcsc.crawler.batch.auto-flush-interval:30000}")
    private long autoFlushInterval;
    
    @Value("${lcsc.crawler.batch.max-memory-buffer:50000}")
    private int maxMemoryBuffer;
    
    @Value("${lcsc.crawler.batch.enable-duplicate-check:true}")
    private boolean enableDuplicateCheck;
    
    @Autowired
    private ProductService productService;
    
    // 批处理队列
    private final ConcurrentLinkedQueue<Product> productQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, Product> productCache = new ConcurrentHashMap<>();
    
    // 统计信息
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger duplicatesSkipped = new AtomicInteger(0);
    private final AtomicInteger batchesProcessed = new AtomicInteger(0);
    private final AtomicLong lastFlushTime = new AtomicLong(System.currentTimeMillis());
    
    /**
     * 添加产品到批处理队列
     * 
     * @param product 产品对象
     */
    public void addProduct(Product product) {
        if (product == null) {
            logger.warn("尝试添加null产品到批处理队列");
            return;
        }
        
        // 数据预处理
        preprocessProduct(product);
        
        // 重复检查
        if (enableDuplicateCheck && isDuplicate(product)) {
            duplicatesSkipped.incrementAndGet();
            logger.debug("跳过重复产品: {}", product.getProductCode());
            return;
        }
        
        // 添加到队列
        productQueue.offer(product);
        
        // 缓存产品（用于重复检查）
        if (enableDuplicateCheck) {
            productCache.put(product.getProductCode(), product);
        }
        
        // 检查是否需要刷新
        checkAndFlushIfNeeded();
        
        logger.debug("产品已添加到批处理队列: {}, 当前队列大小: {}", 
            product.getProductCode(), productQueue.size());
    }
    
    /**
     * 批量添加产品
     * 
     * @param products 产品列表
     */
    public void addProducts(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        
        logger.info("批量添加 {} 个产品到处理队列", products.size());
        
        for (Product product : products) {
            addProduct(product);
        }
    }
    
    /**
     * 数据预处理
     */
    private void preprocessProduct(Product product) {
        // 数据清洗和标准化
        if (product.getModel() != null) {
            product.setModel(product.getModel().trim());
        }
        
        if (product.getBriefDescription() != null) {
            product.setBriefDescription(product.getBriefDescription().trim());
        }
        
        // 设置处理时间戳
        if (product.getCreatedAt() == null) {
            product.setCreatedAt(java.time.LocalDateTime.now());
        }
        product.setUpdatedAt(java.time.LocalDateTime.now());
        product.setLastCrawledAt(java.time.LocalDateTime.now());
        
        // 库存数据处理
        if (product.getTotalStockQuantity() == null || product.getTotalStockQuantity() < 0) {
            product.setTotalStockQuantity(0);
        }
    }
    
    /**
     * 检查是否为重复产品
     */
    private boolean isDuplicate(Product product) {
        if (!enableDuplicateCheck) {
            return false;
        }
        
        String productCode = product.getProductCode();
        if (productCode == null) {
            return false;
        }
        
        // 检查内存缓存
        Product cached = productCache.get(productCode);
        if (cached != null) {
            // 检查是否有重要更新
            return !hasSignificantChanges(cached, product);
        }
        
        return false;
    }
    
    /**
     * 检查产品是否有重要变化
     */
    private boolean hasSignificantChanges(Product existing, Product newProduct) {
        // 价格变化 (检查tierPrices字段)
        if (!Objects.equals(existing.getTierPrices(), newProduct.getTierPrices())) {
            return true;
        }
        
        // 库存变化
        if (!Objects.equals(existing.getTotalStockQuantity(), newProduct.getTotalStockQuantity())) {
            return true;
        }
        
        // 产品描述变化
        if (!Objects.equals(existing.getBriefDescription(), newProduct.getBriefDescription())) {
            return true;
        }
        
        // 详细参数变化
        if (!Objects.equals(existing.getDetailedParameters(), newProduct.getDetailedParameters())) {
            return true;
        }
        
        // 封装变化
        if (!Objects.equals(existing.getPackageName(), newProduct.getPackageName())) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查并在需要时刷新批次
     */
    private void checkAndFlushIfNeeded() {
        // 检查队列大小
        if (productQueue.size() >= productBatchSize) {
            logger.info("队列大小达到批次阈值 {}, 开始刷新", productBatchSize);
            flushBatch();
            return;
        }
        
        // 检查内存使用
        if (productQueue.size() + productCache.size() >= maxMemoryBuffer) {
            logger.warn("内存缓冲区接近限制 {}, 强制刷新", maxMemoryBuffer);
            flushBatch();
            return;
        }
        
        // 检查时间间隔
        long timeSinceLastFlush = System.currentTimeMillis() - lastFlushTime.get();
        if (timeSinceLastFlush >= autoFlushInterval && !productQueue.isEmpty()) {
            logger.info("距离上次刷新已超过 {}ms, 自动刷新", autoFlushInterval);
            flushBatch();
        }
    }
    
    /**
     * 手动刷新批次
     */
    public void flushBatch() {
        if (productQueue.isEmpty()) {
            logger.debug("产品队列为空，跳过刷新");
            return;
        }
        
        List<Product> batch = new ArrayList<>();
        Product product;
        
        // 收集批次数据
        while ((product = productQueue.poll()) != null && batch.size() < productBatchSize) {
            batch.add(product);
        }
        
        if (batch.isEmpty()) {
            return;
        }
        
        logger.info("开始处理批次，产品数量: {}", batch.size());
        
        try {
            processBatchAsync(batch);
            
        } catch (Exception e) {
            logger.error("批次处理失败，产品数量: {}", batch.size(), e);
            
            // 错误处理：将失败的产品重新加入队列
            batch.forEach(productQueue::offer);
            
            throw new RuntimeException("批次数据处理失败", e);
        }
    }
    
    /**
     * 异步处理批次数据
     */
    @Async
    @Transactional
    public void processBatchAsync(List<Product> batch) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 批量保存
            saveBatch(batch);
            
            // 更新统计信息
            totalProcessed.addAndGet(batch.size());
            batchesProcessed.incrementAndGet();
            lastFlushTime.set(System.currentTimeMillis());
            
            long duration = System.currentTimeMillis() - startTime;
            double throughput = batch.size() / (duration / 1000.0);
            
            logger.info("批次处理完成: {} 个产品, 耗时: {}ms, 吞吐量: {:.2f} 个/秒", 
                batch.size(), duration, throughput);
                
        } catch (Exception e) {
            logger.error("异步批次处理失败", e);
            throw e;
        }
    }
    
    /**
     * 执行批量保存
     */
    private void saveBatch(List<Product> batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        try {
            // 按分类分组，提高数据库性能
            Map<Integer, List<Product>> groupedByCategory = batch.stream()
                .filter(p -> p.getCategoryLevel1Id() != null)
                .collect(Collectors.groupingBy(Product::getCategoryLevel1Id));
            
            // 分组处理
            for (Map.Entry<Integer, List<Product>> entry : groupedByCategory.entrySet()) {
                Integer categoryId = entry.getKey();
                List<Product> products = entry.getValue();
                
                logger.debug("处理分类 {} 下的 {} 个产品", categoryId, products.size());
                
                for (Product product : products) {
                    try {
                        productService.saveOrUpdateProduct(product);
                    } catch (Exception e) {
                        logger.error("保存产品失败: {}", product.getProductCode(), e);
                        // 继续处理其他产品
                    }
                }
            }
            
            // 处理没有分类的产品
            List<Product> uncategorized = batch.stream()
                .filter(p -> p.getCategoryLevel1Id() == null)
                .collect(Collectors.toList());
                
            if (!uncategorized.isEmpty()) {
                logger.debug("处理 {} 个未分类产品", uncategorized.size());
                for (Product product : uncategorized) {
                    try {
                        productService.saveOrUpdateProduct(product);
                    } catch (Exception e) {
                        logger.error("保存未分类产品失败: {}", product.getProductCode(), e);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("批量保存失败", e);
            throw new RuntimeException("批量保存产品失败", e);
        }
    }
    
    /**
     * 定时自动刷新
     */
    @Scheduled(fixedDelayString = "${lcsc.crawler.batch.auto-flush-interval:30000}")
    public void scheduledFlush() {
        if (!productQueue.isEmpty()) {
            logger.debug("定时自动刷新，当前队列大小: {}", productQueue.size());
            flushBatch();
        }
        
        // 清理过期缓存
        cleanupExpiredCache();
    }
    
    /**
     * 清理过期缓存
     */
    private void cleanupExpiredCache() {
        if (productCache.size() > maxMemoryBuffer * 0.8) {
            int initialSize = productCache.size();
            
            // 简单的清理策略：保留一半
            Set<String> keysToRemove = productCache.keySet().stream()
                .skip(productCache.size() / 2)
                .collect(Collectors.toSet());
                
            keysToRemove.forEach(productCache::remove);
            
            logger.debug("清理产品缓存: {} -> {}", initialSize, productCache.size());
        }
    }
    
    /**
     * 获取处理统计信息
     */
    public BatchProcessorStats getStats() {
        return new BatchProcessorStats(
            productQueue.size(),
            productCache.size(),
            totalProcessed.get(),
            duplicatesSkipped.get(),
            batchesProcessed.get(),
            lastFlushTime.get()
        );
    }
    
    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalProcessed.set(0);
        duplicatesSkipped.set(0);
        batchesProcessed.set(0);
        lastFlushTime.set(System.currentTimeMillis());
        logger.info("批处理统计信息已重置");
    }
    
    /**
     * 关闭处理器，刷新剩余数据
     */
    @PreDestroy
    public void shutdown() {
        logger.info("批处理器关闭中，刷新剩余数据");
        
        try {
            // 强制刷新所有剩余数据
            while (!productQueue.isEmpty()) {
                flushBatch();
                Thread.sleep(100); // 短暂等待异步处理完成
            }
            
            logger.info("批处理器关闭完成，最终统计: {}", getStats());
            
        } catch (Exception e) {
            logger.error("批处理器关闭时发生错误", e);
        }
    }
    
    /**
     * 批处理统计信息
     */
    public static class BatchProcessorStats {
        private final int currentQueueSize;
        private final int currentCacheSize;
        private final int totalProcessed;
        private final int duplicatesSkipped;
        private final int batchesProcessed;
        private final long lastFlushTime;
        
        public BatchProcessorStats(int currentQueueSize, int currentCacheSize, 
                                 int totalProcessed, int duplicatesSkipped,
                                 int batchesProcessed, long lastFlushTime) {
            this.currentQueueSize = currentQueueSize;
            this.currentCacheSize = currentCacheSize;
            this.totalProcessed = totalProcessed;
            this.duplicatesSkipped = duplicatesSkipped;
            this.batchesProcessed = batchesProcessed;
            this.lastFlushTime = lastFlushTime;
        }
        
        // Getters
        public int getCurrentQueueSize() { return currentQueueSize; }
        public int getCurrentCacheSize() { return currentCacheSize; }
        public int getTotalProcessed() { return totalProcessed; }
        public int getDuplicatesSkipped() { return duplicatesSkipped; }
        public int getBatchesProcessed() { return batchesProcessed; }
        public long getLastFlushTime() { return lastFlushTime; }
        public long getTimeSinceLastFlush() { return System.currentTimeMillis() - lastFlushTime; }
        
        @Override
        public String toString() {
            return String.format("BatchProcessorStats{queue=%d, cache=%d, processed=%d, skipped=%d, batches=%d, lastFlush=%dms ago}", 
                currentQueueSize, currentCacheSize, totalProcessed, duplicatesSkipped, batchesProcessed, getTimeSinceLastFlush());
        }
    }
}