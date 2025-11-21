package com.lcsc.service.crawler.v3;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lcsc.entity.CategoryLevel1Code;
import com.lcsc.entity.CategoryLevel2Code;
import com.lcsc.entity.CategoryLevel3Code;
import com.lcsc.mapper.CategoryLevel1CodeMapper;
import com.lcsc.mapper.CategoryLevel2CodeMapper;
import com.lcsc.mapper.CategoryLevel3CodeMapper;
import com.lcsc.service.crawler.LcscApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 分类同步服务V3
 * 负责从API爬取分类信息并同步到数据库
 *
 * @author lcsc-crawler
 * @since 2025-10-08
 */
@Service
public class CategorySyncService {

    private static final Logger log = LoggerFactory.getLogger(CategorySyncService.class);

    @Autowired
    private LcscApiService apiService;

    @Autowired
    private CategoryLevel1CodeMapper level1Mapper;

    @Autowired
    private CategoryLevel2CodeMapper level2Mapper;

    @Autowired
    private CategoryLevel3CodeMapper level3Mapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_STATE_KEY = "crawler:state";

    /**
     * 爬取并同步分类信息（覆盖模式）
     * 这是用户使用系统的第一步操作
     */
    @Transactional(rollbackFor = Exception.class)
    public CompletableFuture<Map<String, Object>> crawlAndSyncCategories() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("========== 开始爬取分类信息 ==========");

                // 1. 调用API获取分类列表
                log.info("步骤1: 调用API获取分类列表");
                Map<String, Object> apiResponse = apiService.getCatalogList().join();

                if (apiResponse == null || !apiResponse.containsKey("catalogList")) {
                    throw new RuntimeException("API返回数据格式错误");
                }

                // 2. 解析分类数据
                log.info("步骤2: 解析分类数据");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> catalogList =
                    (List<Map<String, Object>>) apiResponse.get("catalogList");

                if (catalogList == null || catalogList.isEmpty()) {
                    throw new RuntimeException("API返回的分类列表为空");
                }

                // 3. 使用UPSERT模式，保持ID稳定（不再删除旧数据）
                log.info("步骤3: 使用UPSERT模式同步分类（保持ID稳定）");

                int level1Count = 0;
                int level2Count = 0;
                int level3Count = 0;  // 新增三级分类计数
                int level1Updated = 0;
                int level2Updated = 0;
                int level3Updated = 0;

                // 4. UPSERT新数据（基于catalog_id判断是否存在）
                log.info("步骤4: 开始同步分类数据");

                for (Map<String, Object> level1Data : catalogList) {
                    // 获取分类名称,如果为空则跳过
                    String catalogName = (String) level1Data.get("catalogNameEn");
                    if (catalogName == null || catalogName.trim().isEmpty()) {
                        log.warn("跳过空分类名称,catalogId: {}", level1Data.get("catalogId"));
                        continue;
                    }

                    // UPSERT一级分类（基于catalog_id查询是否存在）
                    String level1CatalogId = String.valueOf(level1Data.get("catalogId"));
                    CategoryLevel1Code level1 = level1Mapper.selectOne(
                        new QueryWrapper<CategoryLevel1Code>().eq("catalog_id", level1CatalogId)
                    );

                    boolean isLevel1New = (level1 == null);
                    if (isLevel1New) {
                        level1 = new CategoryLevel1Code();
                        level1.setCatalogId(level1CatalogId);
                        level1.setCreatedAt(LocalDateTime.now());
                    }

                    level1.setCategoryLevel1Name(catalogName);
                    level1.setUpdatedAt(LocalDateTime.now());

                    if (isLevel1New) {
                        level1Mapper.insert(level1);
                        level1Count++;
                        log.debug("插入一级分类: {} (ID: {}, catalogId: {})",
                            level1.getCategoryLevel1Name(), level1.getId(), level1CatalogId);
                    } else {
                        level1Mapper.updateById(level1);
                        level1Updated++;
                        log.debug("更新一级分类: {} (ID: {}, catalogId: {})",
                            level1.getCategoryLevel1Name(), level1.getId(), level1CatalogId);
                    }

                    // 创建二级分类
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children =
                        (List<Map<String, Object>>) level1Data.get("childCatelogs");

                    if (children != null && !children.isEmpty()) {
                        for (Map<String, Object> level2Data : children) {
                            // 获取二级分类名称,如果为空则跳过
                            String level2CatalogName = (String) level2Data.get("catalogNameEn");
                            if (level2CatalogName == null || level2CatalogName.trim().isEmpty()) {
                                log.warn("跳过空二级分类名称,catalogId: {}", level2Data.get("catalogId"));
                                continue;
                            }

                            // UPSERT二级分类（基于catalog_id查询是否存在）
                            String level2CatalogId = String.valueOf(level2Data.get("catalogId"));
                            CategoryLevel2Code level2 = level2Mapper.selectOne(
                                new QueryWrapper<CategoryLevel2Code>().eq("catalog_id", level2CatalogId)
                            );

                            boolean isLevel2New = (level2 == null);
                            if (isLevel2New) {
                                level2 = new CategoryLevel2Code();
                                level2.setCatalogId(level2CatalogId);
                                level2.setCrawlStatus("NOT_STARTED");
                                level2.setCrawlProgress(0);
                                level2.setCreatedAt(LocalDateTime.now());
                            }

                            level2.setCategoryLevel2Name(level2CatalogName);
                            level2.setCategoryLevel1Id(level1.getId());
                            level2.setUpdatedAt(LocalDateTime.now());

                            if (isLevel2New) {
                                level2Mapper.insert(level2);
                                level2Count++;
                                log.debug("  插入二级分类: {} (ID: {}, catalogId: {})",
                                    level2.getCategoryLevel2Name(), level2.getId(), level2CatalogId);
                            } else {
                                level2Mapper.updateById(level2);
                                level2Updated++;
                                log.debug("  更新二级分类: {} (ID: {}, catalogId: {})",
                                    level2.getCategoryLevel2Name(), level2.getId(), level2CatalogId);
                            }

                            // 🔑 关键：处理三级分类
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> level3Children =
                                (List<Map<String, Object>>) level2Data.get("childCatelogs");

                            if (level3Children != null && !level3Children.isEmpty()) {
                                for (Map<String, Object> level3Data : level3Children) {
                                    // 获取三级分类名称，如果为空则跳过
                                    String level3CatalogName = (String) level3Data.get("catalogNameEn");
                                    if (level3CatalogName == null || level3CatalogName.trim().isEmpty()) {
                                        log.warn("    跳过空三级分类名称,catalogId: {}", level3Data.get("catalogId"));
                                        continue;
                                    }

                                    // UPSERT三级分类（基于catalog_id查询是否存在）
                                    String level3CatalogId = String.valueOf(level3Data.get("catalogId"));
                                    CategoryLevel3Code level3 = level3Mapper.selectOne(
                                        new QueryWrapper<CategoryLevel3Code>().eq("catalog_id", level3CatalogId)
                                    );

                                    boolean isLevel3New = (level3 == null);
                                    if (isLevel3New) {
                                        level3 = new CategoryLevel3Code();
                                        level3.setCatalogId(level3CatalogId);
                                        level3.setCrawlStatus("NOT_STARTED");
                                        level3.setCrawlProgress(0);
                                        level3.setCreatedAt(LocalDateTime.now());
                                    }

                                    level3.setCategoryLevel3Name(level3CatalogName);
                                    level3.setCategoryLevel1Id(level1.getId());
                                    level3.setCategoryLevel2Id(level2.getId());
                                    level3.setUpdatedAt(LocalDateTime.now());

                                    if (isLevel3New) {
                                        level3Mapper.insert(level3);
                                        level3Count++;
                                        log.debug("    插入三级分类: {} (ID: {}, catalogId: {})",
                                            level3.getCategoryLevel3Name(), level3.getId(), level3CatalogId);
                                    } else {
                                        level3Mapper.updateById(level3);
                                        level3Updated++;
                                        log.debug("    更新三级分类: {} (ID: {}, catalogId: {})",
                                            level3.getCategoryLevel3Name(), level3.getId(), level3CatalogId);
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. 更新Redis状态
                log.info("步骤5: 更新Redis状态");
                redisTemplate.opsForHash().put(REDIS_STATE_KEY, "categoriesSynced", true);

                log.info("========== 分类同步完成 ==========");
                log.info("一级分类: 新增={} 个, 更新={} 个", level1Count, level1Updated);
                log.info("二级分类: 新增={} 个, 更新={} 个", level2Count, level2Updated);
                log.info("三级分类: 新增={} 个, 更新={} 个", level3Count, level3Updated);

                // 同步完成后，刷新 Redis 中的分类名称映射，便于后续下载/处理快速读取
                try {
                    refreshCategoryNameMappings();
                } catch (Exception e) {
                    log.warn("刷新分类名称映射到Redis失败: {}", e.getMessage());
                }

                return Map.of(
                    "success", true,
                    "level1Count", level1Count,
                    "level2Count", level2Count,
                    "level3Count", level3Count,
                    "level1Updated", level1Updated,
                    "level2Updated", level2Updated,
                    "level3Updated", level3Updated,
                    "message", "分类同步成功"
                );

            } catch (Exception e) {
                log.error("分类同步失败", e);
                return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "message", "分类同步失败: " + e.getMessage()
                );
            }
        });
    }

    /**
     * 刷新 Redis 中的分类名称映射
     * - crawler:category:names:l1: { level1Id -> level1Name }
     * - crawler:category:names:l2: { level2Id -> level2Name }
     */
    private void refreshCategoryNameMappings() {
        log.info("刷新Redis分类名称映射...");
        try {
            // 清理旧的映射
            redisTemplate.delete("crawler:category:names:l1");
            redisTemplate.delete("crawler:category:names:l2");

            // 查询所有分类
            List<CategoryLevel1Code> l1List = level1Mapper.selectList(null);
            List<CategoryLevel2Code> l2List = level2Mapper.selectList(null);

            Map<String, String> l1Map = new HashMap<>();
            for (CategoryLevel1Code l1 : l1List) {
                if (l1 != null && l1.getId() != null) {
                    l1Map.put(String.valueOf(l1.getId()), l1.getCategoryLevel1Name());
                }
            }

            Map<String, String> l2Map = new HashMap<>();
            for (CategoryLevel2Code l2 : l2List) {
                if (l2 != null && l2.getId() != null) {
                    l2Map.put(String.valueOf(l2.getId()), l2.getCategoryLevel2Name());
                }
            }

            if (!l1Map.isEmpty()) {
                redisTemplate.opsForHash().putAll("crawler:category:names:l1", l1Map);
            }
            if (!l2Map.isEmpty()) {
                redisTemplate.opsForHash().putAll("crawler:category:names:l2", l2Map);
            }

            log.info("Redis分类名称映射刷新完成: l1={}, l2={}", l1Map.size(), l2Map.size());
        } catch (Exception e) {
            log.error("刷新分类名称映射失败", e);
            throw new RuntimeException("刷新分类名称映射失败: " + e.getMessage());
        }
    }

    /**
     * 检查分类是否已同步
     */
    public boolean isCategoriesSynced() {
        // 先检查Redis缓存
        try {
            Boolean synced = (Boolean) redisTemplate.opsForHash()
                .get(REDIS_STATE_KEY, "categoriesSynced");

            if (synced != null && synced) {
                return true;
            }
        } catch (Exception e) {
            log.warn("检查Redis状态失败", e);
        }

        // 再检查数据库
        try {
            Long count = level1Mapper.selectCount(null);
            boolean exists = count != null && count > 0;

            if (exists) {
                // 更新Redis缓存
                redisTemplate.opsForHash().put(REDIS_STATE_KEY, "categoriesSynced", true);
            }

            return exists;
        } catch (Exception e) {
            log.error("检查分类是否存在失败", e);
            return false;
        }
    }

    /**
     * 获取所有二级分类
     */
    public List<CategoryLevel2Code> getAllLevel2Categories() {
        try {
            return level2Mapper.selectList(null);
        } catch (Exception e) {
            log.error("获取二级分类列表失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取分类统计信息
     */
    public Map<String, Object> getCategoryStatistics() {
        try {
            Long level1Count = level1Mapper.selectCount(null);
            Long level2Count = level2Mapper.selectCount(null);

            // 统计各状态的分类数量
            Map<String, Long> statusCount = new HashMap<>();
            statusCount.put("NOT_STARTED", level2Mapper.selectCount(
                new QueryWrapper<CategoryLevel2Code>().eq("crawl_status", "NOT_STARTED")));
            statusCount.put("IN_QUEUE", level2Mapper.selectCount(
                new QueryWrapper<CategoryLevel2Code>().eq("crawl_status", "IN_QUEUE")));
            statusCount.put("PROCESSING", level2Mapper.selectCount(
                new QueryWrapper<CategoryLevel2Code>().eq("crawl_status", "PROCESSING")));
            statusCount.put("COMPLETED", level2Mapper.selectCount(
                new QueryWrapper<CategoryLevel2Code>().eq("crawl_status", "COMPLETED")));
            statusCount.put("FAILED", level2Mapper.selectCount(
                new QueryWrapper<CategoryLevel2Code>().eq("crawl_status", "FAILED")));

            return Map.of(
                "level1Count", level1Count != null ? level1Count : 0,
                "level2Count", level2Count != null ? level2Count : 0,
                "statusCount", statusCount
            );
        } catch (Exception e) {
            log.error("获取分类统计信息失败", e);
            return Map.of(
                "level1Count", 0,
                "level2Count", 0,
                "statusCount", new HashMap<>()
            );
        }
    }

    /**
     * 清除Redis缓存
     */
    public void clearRedisCache() {
        try {
            redisTemplate.opsForHash().delete(REDIS_STATE_KEY, "categoriesSynced");
            // 同时清理名称映射，确保下次同步后重建
            redisTemplate.delete("crawler:category:names:l1");
            redisTemplate.delete("crawler:category:names:l2");
            log.info("Redis缓存已清除");
        } catch (Exception e) {
            log.error("清除Redis缓存失败", e);
        }
    }
}
