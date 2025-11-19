package com.lcsc.service.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件下载服务 (V2 - Queue Based)
 * 负责下载产品图片和PDF文档，自动启动和停止，并提供实时统计。
 *
 * @author lcsc-crawler
 * @since 2025-10-09
 */
@Service
public class FileDownloadService implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(FileDownloadService.class);

    // Redis 键常量
    private static final String STATS_COMPLETED_IMAGES = "crawler:stats:completed_images";
    private static final String STATS_COMPLETED_PDFS = "crawler:stats:completed_pdfs";
    private static final String STATS_FAILED_DOWNLOADS = "crawler:stats:failed_downloads";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final BlockingQueue<DownloadTask> downloadQueue = new LinkedBlockingQueue<>();
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);
    private volatile boolean isRunning = false;
    private Thread workerThread;

    // 配置常量
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 45000;
    private static final long DOWNLOAD_INTERVAL_MS = 500; // 下载间隔

    /**
     * 定义下载任务记录
     */
    private record DownloadTask(String url, String localPath, String type) {}

    /**
     * 提交一个新的下载任务到队列
     * @param url 文件URL
     * @param localPath 本地保存路径
     * @param type 文件类型 ("image" or "pdf")
     */
    public void submitDownloadTask(String url, String localPath, String type) {
        if (url == null || url.isBlank() || localPath == null || localPath.isBlank()) {
            log.warn("无效的下载任务，URL或路径为空");
            return;
        }
        try {
            downloadQueue.put(new DownloadTask(url, localPath, type));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("提交下载任务被中断", e);
        }
    }

    /**
     * 服务启动方法
     */
    public void start() {
        if (isRunning) {
            log.warn("下载服务已在运行中");
            return;
        }
        isRunning = true;
        workerThread = new Thread(this::workerLoop, "DownloadWorker");
        workerThread.start();
        log.info("========== 文件下载服务已启动 ==========");
    }

    /**
     * 服务停止方法
     */
    public void stop() {
        isRunning = false;
        if (workerThread != null) {
            workerThread.interrupt(); // 中断线程以唤醒并使其退出
        }
        log.info("========== 文件下载服务已停止 ==========");
    }

    /**
     * 工作线程循环
     */
    private void workerLoop() {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                DownloadTask task = downloadQueue.poll(5, TimeUnit.SECONDS); // 等待5秒以避免CPU空转
                if (task == null) {
                    continue;
                }

                activeTaskCount.incrementAndGet();
                handleDownload(task);
                activeTaskCount.decrementAndGet();

                // 增加下载间隔
                Thread.sleep(DOWNLOAD_INTERVAL_MS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break; // 退出循环
            } catch (Exception e) {
                log.error("下载工作线程发生未知异常", e);
            }
        }
        log.info("下载工作线程已退出");
    }

    /**
     * 处理单个下载任务
     */
    private void handleDownload(DownloadTask task) {
        Path filePath = Paths.get(task.localPath());
        
        // 如果文件已存在，跳过下载
        if (Files.exists(filePath)) {
            log.debug("文件已存在，跳过下载: {}", task.localPath());
            return;
        }

        try {
            // 确保父目录存在
            Files.createDirectories(filePath.getParent());

            String processedUrl = processUrl(task.url());
            URL url = new URL(processedUrl);
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);

            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("下载成功: {}", task.localPath());
            // 更新统计
            if ("image".equalsIgnoreCase(task.type())) {
                redisTemplate.opsForValue().increment(STATS_COMPLETED_IMAGES);
            } else if ("pdf".equalsIgnoreCase(task.type())) {
                redisTemplate.opsForValue().increment(STATS_COMPLETED_PDFS);
            }

        } catch (Exception e) {
            log.error("下载失败: url={}, error={}", task.url(), e.getMessage());
            redisTemplate.opsForValue().increment(STATS_FAILED_DOWNLOADS);
            // 下载失败时删除不完整的文件
            try {
                Files.deleteIfExists(filePath);
            } catch (Exception deleteEx) {
                log.error("删除失败文件时出错: {}", filePath, deleteEx);
            }
        }
    }

    private String processUrl(String url) {
        if (!url.startsWith("http")) {
            return "https:" + url;
        }
        return url;
    }

    // --- Spring Bean Lifecycle ---

    @Override
    public void afterPropertiesSet() {
        start();
    }

    @Override
    public void destroy() {
        stop();
    }

    // --- 统计信息API ---

    public int getPendingTaskCount() {
        return downloadQueue.size();
    }

    public int getActiveTaskCount() {
        return activeTaskCount.get();
    }

    public long getCompletedImageCount() {
        String value = redisTemplate.opsForValue().get(STATS_COMPLETED_IMAGES);
        return value != null ? Long.parseLong(value) : 0L;
    }

    public long getCompletedPdfCount() {
        String value = redisTemplate.opsForValue().get(STATS_COMPLETED_PDFS);
        return value != null ? Long.parseLong(value) : 0L;
    }

    public long getFailedTaskCount() {
        String value = redisTemplate.opsForValue().get(STATS_FAILED_DOWNLOADS);
        return value != null ? Long.parseLong(value) : 0L;
    }

    public Map<String, Object> getStatus() {
        return Map.of(
            "isRunning", isRunning,
            "pending", getPendingTaskCount(),
            "processing", getActiveTaskCount(),
            "completedImages", getCompletedImageCount(),
            "completedPdfs", getCompletedPdfCount(),
            "failed", getFailedTaskCount()
        );
    }
}