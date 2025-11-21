package com.lcsc.service.crawler.v3;

import com.lcsc.dto.BrandSplitUnit;
import com.lcsc.service.crawler.LcscApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 任务拆分服务.
 * 负责检测是否需要拆分任务，以及执行品牌拆分策略.
 *
 * @author Claude Code
 * @since 2025-11-21
 */
@Service
public class TaskSplitService {

    private static final Logger log = LoggerFactory.getLogger(TaskSplitService.class);

    /**
     * 触发拆分的产品数量阈值（保守策略，留200条buffer）.
     */
    private static final int SPLIT_THRESHOLD = 4800;

    /**
     * 最大拆分子任务数量限制（防止任务过多）.
     */
    private static final int MAX_SPLIT_TASKS = 50;

    @Autowired
    private LcscApiService lcscApiService;

    /**
     * 检测是否需要拆分任务.
     *
     * @param totalProducts 产品总数
     * @return true 如果需要拆分，false 否则
     */
    public boolean needSplit(int totalProducts) {
        boolean need = totalProducts > SPLIT_THRESHOLD;
        if (need) {
            log.info("产品总数 {} 超过拆分阈值 {}，需要拆分", totalProducts, SPLIT_THRESHOLD);
        }
        return need;
    }

    /**
     * 执行品牌拆分策略.
     * 调用立创API获取该分类下的所有品牌，并为每个品牌创建拆分单元.
     *
     * @param catalogId 分类的catalog ID
     * @param categoryName 分类名称（用于日志）
     * @return 品牌拆分单元列表
     */
    public List<BrandSplitUnit> splitByBrand(String catalogId, String categoryName) {
        log.info("开始执行品牌拆分策略: catalogId={}, categoryName={}", catalogId, categoryName);

        try {
            // 1. 调用API获取筛选参数组
            Map<String, Object> filterParams = new HashMap<>();
            filterParams.put("catalogIdList", List.of(catalogId));

            CompletableFuture<Map<String, Object>> future = lcscApiService.getQueryParamGroup(filterParams);
            Map<String, Object> paramGroups = future.join();

            log.info("获取到筛选参数组: {}", paramGroups.keySet());

            // 2. 提取品牌列表
            List<BrandSplitUnit> brandUnits = extractBrandList(paramGroups, catalogId);

            if (brandUnits.isEmpty()) {
                log.warn("未找到任何品牌，无法拆分: catalogId={}", catalogId);
                return Collections.emptyList();
            }

            // 3. 限制拆分数量
            if (brandUnits.size() > MAX_SPLIT_TASKS) {
                log.warn("品牌数量 {} 超过最大限制 {}，仅取前 {} 个品牌",
                        brandUnits.size(), MAX_SPLIT_TASKS, MAX_SPLIT_TASKS);
                brandUnits = brandUnits.subList(0, MAX_SPLIT_TASKS);
            }

            // 4. 按产品数量降序排序（产品多的品牌优先爬取）
            brandUnits.sort(Comparator.comparingInt(BrandSplitUnit::getProductCount).reversed());

            log.info("品牌拆分完成: 共 {} 个品牌", brandUnits.size());
            logBrandSplitSummary(brandUnits);

            return brandUnits;

        } catch (Exception e) {
            log.error("品牌拆分失败: catalogId={}, error={}", catalogId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 从参数组中提取品牌列表.
     *
     * @param paramGroups API返回的参数组
     * @param catalogId 分类ID
     * @return 品牌拆分单元列表
     */
    @SuppressWarnings("unchecked")
    private List<BrandSplitUnit> extractBrandList(Map<String, Object> paramGroups, String catalogId) {
        List<BrandSplitUnit> brandUnits = new ArrayList<>();

        // 尝试多种可能的品牌字段名称（立创API返回的是Manufacturer）
        String[] possibleBrandKeys = {"Manufacturer", "manufacturer", "Brand", "brand", "brandList", "brands"};

        for (String key : possibleBrandKeys) {
            if (paramGroups.containsKey(key)) {
                Object brandData = paramGroups.get(key);

                if (brandData instanceof List) {
                    List<Map<String, Object>> brandList = (List<Map<String, Object>>) brandData;
                    log.info("找到品牌列表字段: {}, 共 {} 个品牌", key, brandList.size());

                    for (Map<String, Object> brand : brandList) {
                        try {
                            // 提取品牌ID和名称（字段名可能有多种形式）
                            String brandId = extractStringValue(brand, "brandId", "id", "catalogId");
                            String brandName = extractStringValue(brand, "brandName", "name", "catalogName");
                            int productCount = extractIntValue(brand, "productNum", "count", "num");

                            if (brandId != null && !brandId.isEmpty()) {
                                BrandSplitUnit unit = new BrandSplitUnit(
                                        brandId,
                                        brandName != null ? brandName : "未知品牌",
                                        productCount,
                                        catalogId
                                );
                                brandUnits.add(unit);
                            }
                        } catch (Exception e) {
                            log.warn("解析品牌数据失败: {}, error={}", brand, e.getMessage());
                        }
                    }

                    break; // 找到品牌列表后退出循环
                }
            }
        }

        return brandUnits;
    }

    /**
     * 从Map中提取字符串值（支持多个可能的键名）.
     */
    private String extractStringValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                Object value = map.get(key);
                if (value != null) {
                    return value.toString();
                }
            }
        }
        return null;
    }

    /**
     * 从Map中提取整数值（支持多个可能的键名）.
     */
    private int extractIntValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                Object value = map.get(key);
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                if (value instanceof String) {
                    try {
                        return Integer.parseInt((String) value);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return 0;
    }

    /**
     * 记录品牌拆分汇总信息.
     */
    private void logBrandSplitSummary(List<BrandSplitUnit> brandUnits) {
        int totalProducts = brandUnits.stream()
                .mapToInt(BrandSplitUnit::getProductCount)
                .sum();

        log.info("=== 品牌拆分汇总 ===");
        log.info("总品牌数: {}", brandUnits.size());
        log.info("总产品数: {}", totalProducts);
        log.info("品牌详情:");

        // 只显示前10个品牌的详情
        int displayCount = Math.min(10, brandUnits.size());
        for (int i = 0; i < displayCount; i++) {
            BrandSplitUnit unit = brandUnits.get(i);
            log.info("  {}. {} (ID: {}) - {} 个产品",
                    i + 1, unit.getBrandName(), unit.getBrandId(), unit.getProductCount());
        }

        if (brandUnits.size() > displayCount) {
            log.info("  ... 还有 {} 个品牌", brandUnits.size() - displayCount);
        }

        log.info("=====================");
    }
}
