package com.lcsc.service.crawler;

import com.lcsc.dto.DownloadTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理文件下载任务队列的服务.
 * 基于Redis List实现，提供任务的入队和监控功能.
 *
 * @author Gemini-assisted
 * @since 2025-10-08
 */
@Service
public class DownloadQueueService {

    private static final Logger logger = LoggerFactory.getLogger(DownloadQueueService.class);

    /**
     * Redis键：存储待处理的下载任务.
     */
    public static final String PENDING_QUEUE_KEY = "lcsc:downloader:pending_tasks";

    /**
     * Redis键：存储处理失败的下载任务.
     */
    public static final String FAILED_QUEUE_KEY = "lcsc:downloader:failed_tasks";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 将单个下载任务添加到待处理队列的左侧 (LIFO).
     *
     * @param task 下载任务对象.
     */
    public void enqueueTask(DownloadTask task) {
        try {
            redisTemplate.opsForList().leftPush(PENDING_QUEUE_KEY, task);
            logger.debug("Enqueued task: {}", task.getTaskId());
        } catch (Exception e) {
            logger.error("Failed to enqueue task {}", task.getTaskId(), e);
        }
    }

    /**
     * 批量将下载任务添加到待处理队列.
     * 使用pipeline以提高性能.
     *
     * @param tasks 下载任务列表.
     */
    public void enqueueTasks(List<DownloadTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        try {
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<?>) session -> {
                for (DownloadTask task : tasks) {
                    byte[] serializedTask = ((org.springframework.data.redis.serializer.RedisSerializer<Object>) redisTemplate.getValueSerializer()).serialize(task);
                    session.lPush(PENDING_QUEUE_KEY.getBytes(), serializedTask);
                }
                return null;
            });
            logger.info("Enqueued {} tasks successfully.", tasks.size());
        } catch (Exception e) {
            logger.error("Failed to enqueue batch of {} tasks", tasks.size(), e);
        }
    }

    /**
     * 获取待处理队列中的任务数量.
     *
     * @return 队列长度.
     */
    public long getPendingQueueSize() {
        try {
            Long size = redisTemplate.opsForList().size(PENDING_QUEUE_KEY);
            return size != null ? size : 0;
        } catch (Exception e) {
            logger.error("Failed to get pending queue size", e);
            return 0;
        }
    }

    /**
     * 获取失败队列中的任务数量.
     *
     * @return 队列长度.
     */
    public long getFailedQueueSize() {
        try {
            Long size = redisTemplate.opsForList().size(FAILED_QUEUE_KEY);
            return size != null ? size : 0;
        } catch (Exception e) {
            logger.error("Failed to get failed queue size", e);
            return 0;
        }
    }
}
