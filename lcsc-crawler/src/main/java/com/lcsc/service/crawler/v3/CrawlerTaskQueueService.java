package com.lcsc.service.crawler.v3;

import com.lcsc.entity.CategoryLevel1Code;
import com.lcsc.entity.CategoryLevel2Code;
import com.lcsc.mapper.CategoryLevel1CodeMapper;
import com.lcsc.mapper.CategoryLevel2CodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 爬虫任务队列服务V3
 * 负责任务的创建、弹出、完成和状态管理
 *
 * @author lcsc-crawler
 * @since 2025-10-08
 */
@Service
public class CrawlerTaskQueueService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerTaskQueueService.class);

    // Redis键常量
    private static final String QUEUE_PENDING = "crawler:queue:pending";
    private static final String QUEUE_PROCESSING = "crawler:queue:processing";
    private static final String DEDUP_SET = "crawler:dedup:category";
    private static final String CATALOG_TO_TASK_MAP = "crawler:map:catalog_to_task";
    private static final String STATE_KEY = "crawler:state";
    private static final String TASK_PREFIX = "crawler:task:";

    // 优先级常量
    public static final int PRIORITY_MANUAL = 10;   // 手动触发，高优先级
    public static final int PRIORITY_AUTO = 1;      // 自动全量，低优先级

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CategoryLevel2CodeMapper level2Mapper;

    @Autowired
    private CategoryLevel1CodeMapper level1Mapper;

    /**
     * 创建单个分类爬取任务
     * @param catalogId 二级分类ID
     * @param priority 优先级（10=手动, 1=自动）
     * @return 任务ID
     */
    public String createCategoryTask(Integer catalogId, int priority) {
        try {
            // 1. 检查任务是否正在处理中
            if (isTaskProcessing(catalogId)) {
                log.warn("分类任务正在处理中，无法操作: catalogId={}", catalogId);
                throw new RuntimeException("该分类正在爬取中，无法操作");
            }

            // 2. 检查任务是否已在待处理队列中
            String oldTaskId = (String) redisTemplate.opsForHash().get(CATALOG_TO_TASK_MAP, String.valueOf(catalogId));
            if (oldTaskId != null) {
                log.info("任务已在待处理队列，提升其优先级: catalogId={}, oldTaskId={}", catalogId, oldTaskId);
                // 2a. 从待处理队列移除旧任务
                redisTemplate.opsForZSet().remove(QUEUE_PENDING, oldTaskId);
                // 2b. 从映射中移除
                redisTemplate.opsForHash().delete(CATALOG_TO_TASK_MAP, String.valueOf(catalogId));
                // 2c. 从去重集合中移除
                redisTemplate.opsForSet().remove(DEDUP_SET, String.valueOf(catalogId));
                // 2d. 从总任务数中减一，因为我们会重新加回来
                redisTemplate.opsForHash().increment(STATE_KEY, "totalTasks", -1);
            }

            // 3. 查询分类信息
            CategoryLevel2Code level2 = level2Mapper.selectById(catalogId);
            if (level2 == null) {
                throw new RuntimeException("分类不存在: " + catalogId);
            }

            CategoryLevel1Code level1 = level1Mapper.selectById(level2.getCategoryLevel1Id());
            if (level1 == null) {
                throw new RuntimeException("一级分类不存在");
            }

            // 4. 生成任务ID
            String taskId = "TASK_" + catalogId + "_" + System.currentTimeMillis();

            // 5. 构建任务详情
            Map<String, String> taskDetail = new HashMap<>();
            taskDetail.put("catalogId", String.valueOf(catalogId));
            taskDetail.put("catalogName", level2.getCategoryLevel2Name());
            taskDetail.put("level1Id", String.valueOf(level2.getCategoryLevel1Id()));
            taskDetail.put("level1Name", level1.getCategoryLevel1Name());
            taskDetail.put("priority", String.valueOf(priority));
            taskDetail.put("status", "PENDING");
            taskDetail.put("createdAt", LocalDateTime.now().toString());

            // 6. 保存任务到Redis
            redisTemplate.opsForHash().putAll(TASK_PREFIX + taskId, taskDetail);

            // 7. 加入优先级队列
            double score = priority * 1_000_000_000_000_000L + System.currentTimeMillis();
            redisTemplate.opsForZSet().add(QUEUE_PENDING, taskId, score);

            // 8. 添加去重标记和映射
            redisTemplate.opsForSet().add(DEDUP_SET, String.valueOf(catalogId));
            redisTemplate.opsForHash().put(CATALOG_TO_TASK_MAP, String.valueOf(catalogId), taskId);

            // 9. 更新分类状态为IN_QUEUE
            level2.setCrawlStatus("IN_QUEUE");
            level2.setErrorMessage(null);
            level2Mapper.updateById(level2);

            // 10. 更新全局统计
            redisTemplate.opsForHash().increment(STATE_KEY, "totalTasks", 1);

            log.info("创建/更新任务成功: taskId={}, catalogId={}, priority={}",
                taskId, catalogId, priority);

            return taskId;

        } catch (Exception e) {
            log.error("创建任务失败: catalogId={}", catalogId, e);
            throw new RuntimeException("创建任务失败: " + e.getMessage());
        }
    }

    /**
     * 批量创建任务（用于全量爬取）
     * @param catalogIds 分类ID列表
     * @param priority 优先级
     * @return 创建成功的任务ID列表
     */
    public List<String> createBatchTasks(List<Integer> catalogIds, int priority) {
        List<String> taskIds = new ArrayList<>();

        log.info("开始批量创建任务: 总数={}, 优先级={}", catalogIds.size(), priority);

        int successCount = 0;
        int skipCount = 0;

        for (Integer catalogId : catalogIds) {
            try {
                // 跳过已存在的任务
                if (isDuplicated(catalogId)) {
                    skipCount++;
                    continue;
                }

                String taskId = createCategoryTask(catalogId, priority);
                taskIds.add(taskId);
                successCount++;

            } catch (Exception e) {
                log.error("创建任务失败: catalogId={}, error={}", catalogId, e.getMessage());
            }
        }

        log.info("批量创建任务完成: 成功={}, 跳过={}", successCount, skipCount);
        return taskIds;
    }

    /**
     * 从队列弹出下一个任务（按优先级）
     * @param workerThreadId Worker线程ID
     * @return 任务ID，如果队列为空则返回null
     */
    public String popNextTask(int workerThreadId) {
        try {
            // 1. 获取最高优先级任务（score最小的）
            Set<Object> taskIds = redisTemplate.opsForZSet()
                .range(QUEUE_PENDING, 0, 0);

            if (taskIds == null || taskIds.isEmpty()) {
                return null;
            }

            String taskId = (String) taskIds.iterator().next();

            // 2. 原子操作：移出待处理队列
            Long removed = redisTemplate.opsForZSet().remove(QUEUE_PENDING, taskId);
            if (removed == null || removed == 0) {
                // 任务已被其他线程取走
                return null;
            }

            // 3. 加入处理中队列
            redisTemplate.opsForSet().add(QUEUE_PROCESSING, taskId);

            // 4. 更新任务状态
            redisTemplate.opsForHash().put(TASK_PREFIX + taskId, "status", "PROCESSING");
            redisTemplate.opsForHash().put(TASK_PREFIX + taskId, "startedAt",
                LocalDateTime.now().toString());
            redisTemplate.opsForHash().put(TASK_PREFIX + taskId, "workerThread",
                String.valueOf(workerThreadId));

            // 5. 获取catalogId并更新数据库状态
            String catalogIdStr = (String) redisTemplate.opsForHash()
                .get(TASK_PREFIX + taskId, "catalogId");
            if (catalogIdStr != null) {
                // 5a. 从映射中移除
                redisTemplate.opsForHash().delete(CATALOG_TO_TASK_MAP, catalogIdStr);

                // 5b. 更新数据库状态
                Integer catalogId = Integer.valueOf(catalogIdStr);
                CategoryLevel2Code category = level2Mapper.selectById(catalogId);
                if (category != null) {
                    category.setCrawlStatus("PROCESSING");
                    level2Mapper.updateById(category);
                }
            }

            log.info("Worker-{} 弹出任务: {}", workerThreadId, taskId);
            return taskId;

        } catch (Exception e) {
            log.error("弹出任务失败", e);
            return null;
        }
    }

    /**
     * 完成任务
     * @param taskId 任务ID
     * @param success 是否成功
     * @param errorMessage 错误信息（失败时）
     */
    public void completeTask(String taskId, boolean success, String errorMessage) {
        try {
            // 1. 获取任务信息
            Map<Object, Object> taskMap = redisTemplate.opsForHash()
                .entries(TASK_PREFIX + taskId);

            if (taskMap.isEmpty()) {
                log.warn("任务不存在: {}", taskId);
                return;
            }

            String catalogIdStr = (String) taskMap.get("catalogId");
            Integer catalogId = Integer.valueOf(catalogIdStr);

            // 2. 移出处理中队列
            redisTemplate.opsForSet().remove(QUEUE_PROCESSING, taskId);

            // 3. 移除去重标记
            redisTemplate.opsForSet().remove(DEDUP_SET, catalogIdStr);
            
            // 3b. 从映射中移除（双保险）
            redisTemplate.opsForHash().delete(CATALOG_TO_TASK_MAP, catalogIdStr);

            // 4. 更新任务状态
            redisTemplate.opsForHash().put(TASK_PREFIX + taskId, "status",
                success ? "COMPLETED" : "FAILED");
            redisTemplate.opsForHash().put(TASK_PREFIX + taskId, "completedAt",
                LocalDateTime.now().toString());
            if (!success && errorMessage != null) {
                redisTemplate.opsForHash().put(TASK_PREFIX + taskId, "errorMessage", errorMessage);
            }

            // 5. 更新全局统计
            if (success) {
                redisTemplate.opsForHash().increment(STATE_KEY, "completedTasks", 1);
            } else {
                redisTemplate.opsForHash().increment(STATE_KEY, "failedTasks", 1);
            }

            // 6. 更新数据库中的分类状态
            CategoryLevel2Code category = level2Mapper.selectById(catalogId);
            if (category != null) {
                category.setCrawlStatus(success ? "COMPLETED" : "FAILED");
                category.setLastCrawlTime(LocalDateTime.now());
                if (!success && errorMessage != null) {
                    category.setErrorMessage(errorMessage);
                }
                level2Mapper.updateById(category);
            }

            log.info("任务完成: taskId={}, catalogId={}, success={}", taskId, catalogId, success);

        } catch (Exception e) {
            log.error("完成任务时出错: taskId={}", taskId, e);
        }
    }

    /**
     * 检查任务是否已在队列中（待处理或处理中）
     */
    private boolean isDuplicated(Integer catalogId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(DEDUP_SET, String.valueOf(catalogId)));
    }

    /**
     * 检查任务是否正在处理中
     */
    private boolean isTaskProcessing(Integer catalogId) {
        Set<Object> processingTaskIds = redisTemplate.opsForSet().members(QUEUE_PROCESSING);
        if (processingTaskIds == null || processingTaskIds.isEmpty()) {
            return false;
        }

        for (Object taskIdObj : processingTaskIds) {
            String taskId = (String) taskIdObj;
            String taskCatalogIdStr = (String) redisTemplate.opsForHash().get(TASK_PREFIX + taskId, "catalogId");
            if (taskCatalogIdStr != null && taskCatalogIdStr.equals(String.valueOf(catalogId))) {
                return true; // 找到匹配的任务
            }
        }
        return false;
    }

    /**
     * 获取队列状态
     */
    public Map<String, Object> getQueueStatus() {
        try {
            Long pending = redisTemplate.opsForZSet().size(QUEUE_PENDING);
            Long processing = redisTemplate.opsForSet().size(QUEUE_PROCESSING);

            Map<Object, Object> state = redisTemplate.opsForHash()
                .entries(STATE_KEY);

            Object completedObj = state.get("completedTasks");
            Object failedObj = state.get("failedTasks");
            Object totalObj = state.get("totalTasks");

            int completed = completedObj != null ? Integer.parseInt(String.valueOf(completedObj)) : 0;
            int failed = failedObj != null ? Integer.parseInt(String.valueOf(failedObj)) : 0;
            int total = totalObj != null ? Integer.parseInt(String.valueOf(totalObj)) : 0;

            return Map.of(
                "pending", pending != null ? pending.intValue() : 0,
                "processing", processing != null ? processing.intValue() : 0,
                "completed", completed,
                "failed", failed,
                "total", total
            );

        } catch (Exception e) {
            log.error("获取队列状态失败", e);
            return Map.of(
                "pending", 0,
                "processing", 0,
                "completed", 0,
                "failed", 0,
                "total", 0
            );
        }
    }

    /**
     * 获取任务详情
     */
    public Map<Object, Object> getTaskDetails(String taskId) {
        try {
            return redisTemplate.opsForHash().entries(TASK_PREFIX + taskId);
        } catch (Exception e) {
            log.error("获取任务详情失败: taskId={}", taskId, e);
            return new HashMap<>();
        }
    }

    /**
     * 清空所有队列（慎用）
     */
    public void clearAllQueues() {
        try {
            redisTemplate.delete(QUEUE_PENDING);
            redisTemplate.delete(QUEUE_PROCESSING);
            redisTemplate.delete(DEDUP_SET);
            redisTemplate.delete(STATE_KEY);

            log.info("所有队列已清空");
        } catch (Exception e) {
            log.error("清空队列失败", e);
        }
    }

    /**
     * 初始化全局状态
     */
    public void initializeState() {
        try {
            redisTemplate.opsForHash().put(STATE_KEY, "isRunning", false);
            redisTemplate.opsForHash().put(STATE_KEY, "totalTasks", 0);
            redisTemplate.opsForHash().put(STATE_KEY, "completedTasks", 0);
            redisTemplate.opsForHash().put(STATE_KEY, "failedTasks", 0);
            redisTemplate.opsForHash().put(STATE_KEY, "workerThreadCount", 3);

            log.info("全局状态已初始化");
        } catch (Exception e) {
            log.error("初始化全局状态失败", e);
        }
    }
}
