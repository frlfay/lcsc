package com.lcsc.service.crawler.v3;

import com.lcsc.entity.CategoryLevel1Code;
import com.lcsc.entity.CategoryLevel2Code;
import com.lcsc.entity.CategoryLevel3Code;
import com.lcsc.mapper.CategoryLevel1CodeMapper;
import com.lcsc.mapper.CategoryLevel2CodeMapper;
import com.lcsc.service.CategoryLevel3CodeService;
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

    @Autowired
    private CategoryLevel3CodeService level3Service;

    /**
     * 创建单个分类爬取任务（智能识别二级或三级分类）
     * @param categoryId 分类ID（可能是二级或三级分类的ID）
     * @param priority 优先级（10=手动, 1=自动）
     * @return 任务ID
     */
    public String createCategoryTask(Integer categoryId, int priority) {
        return createCategoryTask(categoryId, null, priority);
    }

    /**
     * 创建单个分类爬取任务（支持明确指定分类级别）
     * @param categoryId 分类ID
     * @param forcedLevel 强制指定分类级别（"level2" 或 "level3"），传null则自动识别
     * @param priority 优先级（10=手动, 1=自动）
     * @return 任务ID
     */
    public String createCategoryTask(Integer categoryId, String forcedLevel, int priority) {
        try {
            // 1. 检查任务是否正在处理中
            if (isTaskProcessing(categoryId)) {
                log.warn("分类任务正在处理中，无法操作: categoryId={}", categoryId);
                throw new RuntimeException("该分类正在爬取中，无法操作");
            }

            // 2. 检查任务是否已在待处理队列中
            String oldTaskId = (String) redisTemplate.opsForHash().get(CATALOG_TO_TASK_MAP, String.valueOf(categoryId));
            if (oldTaskId != null) {
                log.info("任务已在待处理队列，提升其优先级: categoryId={}, oldTaskId={}", categoryId, oldTaskId);
                // 2a. 从待处理队列移除旧任务
                redisTemplate.opsForZSet().remove(QUEUE_PENDING, oldTaskId);
                // 2b. 从映射中移除
                redisTemplate.opsForHash().delete(CATALOG_TO_TASK_MAP, String.valueOf(categoryId));
                // 2c. 从去重集合中移除
                redisTemplate.opsForSet().remove(DEDUP_SET, String.valueOf(categoryId));
                // 2d. 从总任务数中减一，因为我们会重新加回来
                redisTemplate.opsForHash().increment(STATE_KEY, "totalTasks", -1);
            }

            // 3. 识别分类级别（如果指定了forcedLevel，直接使用；否则智能识别）
            CategoryLevel2Code level2 = null;
            CategoryLevel3Code level3 = null;
            String categoryLevel;
            String catalogName;
            String catalogApiId; // 立创API的catalogId
            Integer level1Id;
            Integer level2Id = null;

            if (forcedLevel != null) {
                // 使用强制指定的分类级别，跳过智能识别
                categoryLevel = forcedLevel;
                if ("level2".equals(forcedLevel)) {
                    level2 = level2Mapper.selectById(categoryId);
                    if (level2 == null) {
                        throw new RuntimeException("二级分类不存在: " + categoryId);
                    }
                    catalogName = level2.getCategoryLevel2Name();
                    catalogApiId = level2.getCatalogId();
                    level1Id = level2.getCategoryLevel1Id();
                    level2Id = level2.getId();
                    log.debug("强制指定为二级分类: id={}, name={}", categoryId, catalogName);
                } else if ("level3".equals(forcedLevel)) {
                    level3 = level3Service.getOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CategoryLevel3Code>()
                        .eq(CategoryLevel3Code::getId, categoryId));
                    if (level3 == null) {
                        throw new RuntimeException("三级分类不存在: " + categoryId);
                    }
                    catalogName = level3.getCategoryLevel3Name();
                    catalogApiId = level3.getCatalogId();
                    level1Id = level3.getCategoryLevel1Id();
                    level2Id = level3.getCategoryLevel2Id();
                    log.debug("强制指定为三级分类: id={}, name={}, level2Id={}", categoryId, catalogName, level2Id);
                } else {
                    throw new RuntimeException("无效的分类级别: " + forcedLevel);
                }
            } else {
                // 智能识别分类级别（先查二级，再查三级）
                level2 = level2Mapper.selectById(categoryId);

                if (level2 != null) {
                    // 是二级分类
                    categoryLevel = "level2";
                    catalogName = level2.getCategoryLevel2Name();
                    catalogApiId = level2.getCatalogId();
                    level1Id = level2.getCategoryLevel1Id();
                    level2Id = level2.getId();
                    log.debug("智能识别为二级分类: id={}, name={}, catalogApiId={}", categoryId, catalogName, catalogApiId);
                } else {
                    // 尝试查询三级分类
                    level3 = level3Service.getOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CategoryLevel3Code>()
                        .eq(CategoryLevel3Code::getId, categoryId));

                    if (level3 == null) {
                        throw new RuntimeException("分类不存在: " + categoryId);
                    }

                    // 是三级分类
                    categoryLevel = "level3";
                    catalogName = level3.getCategoryLevel3Name();
                    catalogApiId = level3.getCatalogId();
                    level1Id = level3.getCategoryLevel1Id();
                    level2Id = level3.getCategoryLevel2Id();
                    log.debug("智能识别为三级分类: id={}, name={}, catalogApiId={}, level2Id={}",
                        categoryId, catalogName, catalogApiId, level2Id);
                }
            }

            // 4. 查询一级分类信息
            CategoryLevel1Code level1 = level1Mapper.selectById(level1Id);
            if (level1 == null) {
                throw new RuntimeException("一级分类不存在");
            }

            // 5. 生成任务ID
            String taskId = "TASK_" + categoryId + "_" + System.currentTimeMillis();

            // 6. 构建任务详情
            Map<String, String> taskDetail = new HashMap<>();
            taskDetail.put("categoryId", String.valueOf(categoryId)); // 数据库ID
            taskDetail.put("categoryLevel", categoryLevel); // "level2" 或 "level3"
            taskDetail.put("catalogApiId", catalogApiId); // 立创API的catalogId
            taskDetail.put("catalogName", catalogName);
            taskDetail.put("level1Id", String.valueOf(level1Id));
            taskDetail.put("level1Name", level1.getCategoryLevel1Name());
            if (level2Id != null) {
                taskDetail.put("level2Id", String.valueOf(level2Id));
            }
            taskDetail.put("priority", String.valueOf(priority));
            taskDetail.put("status", "PENDING");
            taskDetail.put("createdAt", LocalDateTime.now().toString());

            // 7. 保存任务到Redis
            redisTemplate.opsForHash().putAll(TASK_PREFIX + taskId, taskDetail);

            // 8. 加入优先级队列
            double score = priority * 1_000_000_000_000_000L + System.currentTimeMillis();
            redisTemplate.opsForZSet().add(QUEUE_PENDING, taskId, score);

            // 9. 添加去重标记和映射
            redisTemplate.opsForSet().add(DEDUP_SET, String.valueOf(categoryId));
            redisTemplate.opsForHash().put(CATALOG_TO_TASK_MAP, String.valueOf(categoryId), taskId);

            // 10. 更新分类状态为IN_QUEUE
            if (level2 != null) {
                level2.setCrawlStatus("IN_QUEUE");
                level2.setErrorMessage(null);
                level2Mapper.updateById(level2);
            } else if (level3 != null) {
                level3.setCrawlStatus("IN_QUEUE");
                level3.setErrorMessage(null);
                level3Service.updateById(level3);
            }

            // 11. 更新全局统计
            redisTemplate.opsForHash().increment(STATE_KEY, "totalTasks", 1);

            log.info("创建/更新任务成功: taskId={}, categoryId={}, level={}, name={}, priority={}",
                taskId, categoryId, categoryLevel, catalogName, priority);

            return taskId;

        } catch (Exception e) {
            log.error("创建任务失败: categoryId={}", categoryId, e);
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

            String catalogIdStr = (String) taskMap.get("categoryId");
            Integer catalogId = Integer.valueOf(catalogIdStr);
            String categoryLevel = (String) taskMap.get("categoryLevel");

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

            // 6. 更新数据库中的分类状态（支持二级和三级分类）
            if ("level3".equals(categoryLevel)) {
                // 三级分类
                CategoryLevel3Code level3 = level3Service.getOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CategoryLevel3Code>()
                        .eq(CategoryLevel3Code::getId, catalogId)
                );
                if (level3 != null) {
                    level3.setCrawlStatus(success ? "COMPLETED" : "FAILED");
                    level3.setLastCrawlTime(LocalDateTime.now());
                    if (!success && errorMessage != null) {
                        level3.setErrorMessage(errorMessage);
                    }
                    level3Service.updateById(level3);
                }
            } else {
                // 二级分类
                CategoryLevel2Code category = level2Mapper.selectById(catalogId);
                if (category != null) {
                    category.setCrawlStatus(success ? "COMPLETED" : "FAILED");
                    category.setLastCrawlTime(LocalDateTime.now());
                    if (!success && errorMessage != null) {
                        category.setErrorMessage(errorMessage);
                    }
                    level2Mapper.updateById(category);
                }
            }

            log.info("任务完成: taskId={}, catalogId={}, level={}, success={}",
                taskId, catalogId, categoryLevel, success);

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

            // 统计子任务数量
            int subTaskCount = countSubTasks();

            Map<String, Object> result = new HashMap<>();
            result.put("pending", pending != null ? pending.intValue() : 0);
            result.put("processing", processing != null ? processing.intValue() : 0);
            result.put("completed", completed);
            result.put("failed", failed);
            result.put("total", total);
            result.put("subTaskCount", subTaskCount);
            return result;

        } catch (Exception e) {
            log.error("获取队列状态失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("pending", 0);
            errorResult.put("processing", 0);
            errorResult.put("completed", 0);
            errorResult.put("failed", 0);
            errorResult.put("total", 0);
            errorResult.put("subTaskCount", 0);
            return errorResult;
        }
    }

    /**
     * 统计子任务数量
     */
    private int countSubTasks() {
        try {
            Set<Object> pendingTasks = redisTemplate.opsForZSet().range(QUEUE_PENDING, 0, -1);
            Set<Object> processingTasks = redisTemplate.opsForSet().members(QUEUE_PROCESSING);

            int count = 0;
            if (pendingTasks != null) {
                for (Object taskId : pendingTasks) {
                    String isSubTask = (String) redisTemplate.opsForHash().get(TASK_PREFIX + taskId, "isSubTask");
                    if ("true".equals(isSubTask)) {
                        count++;
                    }
                }
            }
            if (processingTasks != null) {
                for (Object taskId : processingTasks) {
                    String isSubTask = (String) redisTemplate.opsForHash().get(TASK_PREFIX + taskId, "isSubTask");
                    if ("true".equals(isSubTask)) {
                        count++;
                    }
                }
            }
            return count;
        } catch (Exception e) {
            log.error("统计子任务数量失败", e);
            return 0;
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

    /**
     * 智能创建所有分类任务（自动判断二级或三级）
     * 关键逻辑：如果二级分类下有三级分类，则只为三级创建任务；否则为二级创建任务
     *
     * @param priority 任务优先级
     * @return 创建的任务ID列表
     */
    public List<String> createSmartCategoryTasks(int priority) {
        List<String> taskIds = new ArrayList<>();

        log.info("========== 开始智能创建任务 ==========");

        // 1. 获取所有二级分类
        List<CategoryLevel2Code> level2Categories = level2Mapper.selectList(null);
        log.info("共有 {} 个二级分类", level2Categories.size());

        int level2TaskCount = 0;
        int level3TaskCount = 0;

        // 2. 遍历每个二级分类
        for (CategoryLevel2Code level2 : level2Categories) {
            // 3. 检查该二级分类下是否有三级分类
            List<CategoryLevel3Code> level3List = level3Service.getByLevel2Id(level2.getId());

            if (level3List != null && !level3List.isEmpty()) {
                // 有三级分类 → 为每个三级分类创建任务
                log.info("二级分类 [{}] (ID:{}) 下有 {} 个三级分类，为三级创建任务",
                    level2.getCategoryLevel2Name(),
                    level2.getId(),
                    level3List.size());

                for (CategoryLevel3Code level3 : level3List) {
                    try {
                        if (!isDuplicated(level3.getId())) {
                            String taskId = createCategoryTask(level3.getId(), priority);
                            taskIds.add(taskId);
                            level3TaskCount++;
                        }
                    } catch (Exception e) {
                        log.error("创建三级分类任务失败: level3Id={}, name={}, error={}",
                            level3.getId(),
                            level3.getCategoryLevel3Name(),
                            e.getMessage());
                    }
                }
            } else {
                // 没有三级分类 → 为二级分类创建任务
                try {
                    if (!isDuplicated(level2.getId())) {
                        String taskId = createCategoryTask(level2.getId(), priority);
                        taskIds.add(taskId);
                        level2TaskCount++;
                    }
                } catch (Exception e) {
                    log.error("创建二级分类任务失败: level2Id={}, name={}, error={}",
                        level2.getId(),
                        level2.getCategoryLevel2Name(),
                        e.getMessage());
                }
            }
        }

        log.info("========== 智能任务创建完成 ==========");
        log.info("为二级分类创建任务: {} 个", level2TaskCount);
        log.info("为三级分类创建任务: {} 个", level3TaskCount);
        log.info("总任务数: {} 个", taskIds.size());

        return taskIds;
    }

    /**
     * 创建品牌筛选子任务（用于突破5000条限制）
     * @param parentTaskId 父任务ID
     * @param categoryId 分类ID
     * @param categoryLevel 分类级别（level2 或 level3）
     * @param catalogApiId 立创API的catalogId
     * @param catalogName 分类名称
     * @param brandId 品牌ID
     * @param brandName 品牌名称
     * @param expectedCount 预期产品数量
     * @param level1Id 一级分类ID
     * @param level1Name 一级分类名称
     * @param level2Id 二级分类ID（可选）
     * @param priority 优先级
     * @return 任务ID
     */
    public String createBrandFilteredTask(
            String parentTaskId,
            Integer categoryId,
            String categoryLevel,
            String catalogApiId,
            String catalogName,
            String brandId,
            String brandName,
            int expectedCount,
            Integer level1Id,
            String level1Name,
            Integer level2Id,
            int priority) {
        try {
            // 1. 生成子任务ID
            String taskId = "TASK_" + categoryId + "_BRAND_" + brandId + "_" + System.currentTimeMillis();

            // 2. 构建筛选参数（JSON格式）
            String filterParams = String.format("{\"brandIdList\":[\"%s\"]}", brandId);

            // 3. 构建任务详情
            Map<String, String> taskDetail = new HashMap<>();
            taskDetail.put("categoryId", String.valueOf(categoryId));
            taskDetail.put("categoryLevel", categoryLevel);
            taskDetail.put("catalogApiId", catalogApiId);
            taskDetail.put("catalogName", catalogName);
            taskDetail.put("level1Id", String.valueOf(level1Id));
            taskDetail.put("level1Name", level1Name);
            if (level2Id != null) {
                taskDetail.put("level2Id", String.valueOf(level2Id));
            }
            taskDetail.put("priority", String.valueOf(priority));
            taskDetail.put("status", "PENDING");
            taskDetail.put("createdAt", LocalDateTime.now().toString());

            // 4. 添加拆分相关字段
            taskDetail.put("isSubTask", "true");
            taskDetail.put("parentTaskId", parentTaskId);
            taskDetail.put("splitStrategy", "BRAND");
            taskDetail.put("filterParams", filterParams);
            taskDetail.put("brandId", brandId);
            taskDetail.put("brandName", brandName);
            taskDetail.put("expectedCount", String.valueOf(expectedCount));

            // 5. 保存任务到Redis
            redisTemplate.opsForHash().putAll(TASK_PREFIX + taskId, taskDetail);

            // 6. 加入优先级队列（子任务继承父任务优先级）
            double score = priority * 1_000_000_000_000_000L + System.currentTimeMillis();
            redisTemplate.opsForZSet().add(QUEUE_PENDING, taskId, score);

            // 7. 更新全局统计
            redisTemplate.opsForHash().increment(STATE_KEY, "totalTasks", 1);

            log.info("创建品牌筛选子任务成功: taskId={}, parentTaskId={}, brand={}, expectedCount={}",
                    taskId, parentTaskId, brandName, expectedCount);

            return taskId;

        } catch (Exception e) {
            log.error("创建品牌筛选子任务失败: parentTaskId={}, brandId={}", parentTaskId, brandId, e);
            throw new RuntimeException("创建品牌筛选子任务失败: " + e.getMessage());
        }
    }

    /**
     * 创建通用拆分子任务（支持多级拆分：品牌→封装→参数）
     *
     * @param parentTaskId 父任务ID
     * @param categoryId 分类ID
     * @param categoryLevel 分类级别（level2 或 level3）
     * @param catalogApiId 立创API的catalogId
     * @param catalogName 分类名称
     * @param dimensionName 拆分维度名称（Brand, Package, Voltage等）
     * @param filterId 筛选值ID
     * @param filterValue 筛选值名称（用于显示）
     * @param expectedCount 预期产品数量
     * @param filterParams 累积的筛选参数（Map格式，包含所有层级的筛选条件）
     * @param splitLevel 拆分深度（0=第一次拆分，1=第二次拆分，以此类推）
     * @param level1Id 一级分类ID
     * @param level1Name 一级分类名称
     * @param level2Id 二级分类ID（可选）
     * @param priority 优先级
     * @return 任务ID
     */
    public String createSplitTask(
            String parentTaskId,
            Integer categoryId,
            String categoryLevel,
            String catalogApiId,
            String catalogName,
            String dimensionName,
            String filterId,
            String filterValue,
            int expectedCount,
            Map<String, Object> filterParams,
            int splitLevel,
            Integer level1Id,
            String level1Name,
            Integer level2Id,
            int priority) {
        try {
            // 1. 生成子任务ID（包含维度信息）
            String sanitizedFilterId = filterId.replaceAll("[^a-zA-Z0-9]", "_");
            if (sanitizedFilterId.length() > 20) {
                sanitizedFilterId = sanitizedFilterId.substring(0, 20);
            }
            String taskId = String.format("TASK_%d_%s_%s_%d",
                    categoryId, dimensionName, sanitizedFilterId, System.currentTimeMillis());

            // 2. 序列化筛选参数为JSON
            String filterParamsJson;
            try {
                com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                filterParamsJson = objectMapper.writeValueAsString(filterParams);
            } catch (Exception e) {
                log.error("序列化筛选参数失败: {}", e.getMessage());
                filterParamsJson = "{}";
            }

            // 3. 构建任务详情
            Map<String, String> taskDetail = new HashMap<>();
            taskDetail.put("categoryId", String.valueOf(categoryId));
            taskDetail.put("categoryLevel", categoryLevel);
            taskDetail.put("catalogApiId", catalogApiId);
            taskDetail.put("catalogName", catalogName);
            taskDetail.put("level1Id", String.valueOf(level1Id));
            taskDetail.put("level1Name", level1Name);
            if (level2Id != null) {
                taskDetail.put("level2Id", String.valueOf(level2Id));
            }
            taskDetail.put("priority", String.valueOf(priority));
            taskDetail.put("status", "PENDING");
            taskDetail.put("createdAt", LocalDateTime.now().toString());

            // 4. 添加拆分相关字段
            taskDetail.put("isSubTask", "true");
            taskDetail.put("parentTaskId", parentTaskId);
            taskDetail.put("splitLevel", String.valueOf(splitLevel));
            taskDetail.put("dimensionName", dimensionName);
            taskDetail.put("filterId", filterId);
            taskDetail.put("filterValue", filterValue);
            taskDetail.put("filterParams", filterParamsJson);
            taskDetail.put("expectedCount", String.valueOf(expectedCount));

            // 5. 兼容旧逻辑：如果是品牌维度，也设置brandId/brandName
            if ("Brand".equals(dimensionName)) {
                taskDetail.put("brandId", filterId);
                taskDetail.put("brandName", filterValue);
                taskDetail.put("splitStrategy", "BRAND");
            } else if ("Package".equals(dimensionName)) {
                taskDetail.put("splitStrategy", "PACKAGE");
            } else {
                taskDetail.put("splitStrategy", "PARAMETER");
            }

            // 6. 保存任务到Redis
            redisTemplate.opsForHash().putAll(TASK_PREFIX + taskId, taskDetail);

            // 7. 加入优先级队列
            double score = priority * 1_000_000_000_000_000L + System.currentTimeMillis();
            redisTemplate.opsForZSet().add(QUEUE_PENDING, taskId, score);

            // 8. 更新全局统计
            redisTemplate.opsForHash().increment(STATE_KEY, "totalTasks", 1);

            log.info("创建拆分子任务成功: taskId={}, splitLevel={}, dimension={}, value={}, expectedCount={}",
                    taskId, splitLevel, dimensionName, filterValue, expectedCount);

            return taskId;

        } catch (Exception e) {
            log.error("创建拆分子任务失败: parentTaskId={}, dimension={}, filterId={}",
                    parentTaskId, dimensionName, filterId, e);
            throw new RuntimeException("创建拆分子任务失败: " + e.getMessage());
        }
    }

    /**
     * 智能批量创建任务（用于手动选择分类爬取）
     * 关键逻辑：对于传入的每个二级分类ID，检查是否有三级分类
     * - 如果有三级分类，则为所有三级分类创建任务
     * - 如果没有三级分类，则为该二级分类创建任务
     *
     * @param level2CategoryIds 二级分类ID列表（用户选择的分类）
     * @param priority 任务优先级
     * @return 创建的任务ID列表
     */
    public List<String> createSmartBatchTasks(List<Integer> level2CategoryIds, int priority) {
        List<String> taskIds = new ArrayList<>();

        log.info("========== 开始智能批量创建任务 ==========");
        log.info("用户选择了 {} 个二级分类", level2CategoryIds.size());

        int level2TaskCount = 0;
        int level3TaskCount = 0;

        // 遍历用户选择的每个二级分类
        for (Integer level2Id : level2CategoryIds) {
            // 获取二级分类信息
            CategoryLevel2Code level2 = level2Mapper.selectById(level2Id);
            if (level2 == null) {
                log.warn("二级分类不存在: level2Id={}", level2Id);
                continue;
            }

            // 检查该二级分类下是否有三级分类
            List<CategoryLevel3Code> level3List = level3Service.getByLevel2Id(level2Id);

            if (level3List != null && !level3List.isEmpty()) {
                // 有三级分类 → 为每个三级分类创建任务
                log.info("二级分类 [{}] (ID:{}) 下有 {} 个三级分类，为三级创建任务",
                    level2.getCategoryLevel2Name(),
                    level2Id,
                    level3List.size());

                for (CategoryLevel3Code level3 : level3List) {
                    try {
                        if (!isDuplicated(level3.getId())) {
                            // ✅ 关键修复：明确指定这是三级分类，避免ID冲突导致的误识别
                            String taskId = createCategoryTask(level3.getId(), "level3", priority);
                            taskIds.add(taskId);
                            level3TaskCount++;
                            log.info("  → 创建三级分类任务: [{}] (ID:{}, catalogId={})",
                                level3.getCategoryLevel3Name(),
                                level3.getId(),
                                level3.getCatalogId());
                        } else {
                            log.debug("  → 跳过已存在的三级分类任务: [{}]",
                                level3.getCategoryLevel3Name());
                        }
                    } catch (Exception e) {
                        log.error("创建三级分类任务失败: level3Id={}, name={}, error={}",
                            level3.getId(),
                            level3.getCategoryLevel3Name(),
                            e.getMessage());
                    }
                }
            } else {
                // 没有三级分类 → 为二级分类创建任务
                log.info("二级分类 [{}] (ID:{}) 下无三级分类，为二级创建任务",
                    level2.getCategoryLevel2Name(),
                    level2Id);

                try {
                    if (!isDuplicated(level2Id)) {
                        // ✅ 明确指定为二级分类，保持代码一致性
                        String taskId = createCategoryTask(level2Id, "level2", priority);
                        taskIds.add(taskId);
                        level2TaskCount++;
                    } else {
                        log.debug("  → 跳过已存在的二级分类任务");
                    }
                } catch (Exception e) {
                    log.error("创建二级分类任务失败: level2Id={}, name={}, error={}",
                        level2Id,
                        level2.getCategoryLevel2Name(),
                        e.getMessage());
                }
            }
        }

        log.info("========== 智能批量任务创建完成 ==========");
        log.info("为二级分类创建任务: {} 个", level2TaskCount);
        log.info("为三级分类创建任务: {} 个", level3TaskCount);
        log.info("总任务数: {} 个", taskIds.size());

        return taskIds;
    }
}
