package com.lcsc.service.crawler.core;

import com.lcsc.service.crawler.network.HttpClientService;
import com.lcsc.service.crawler.parser.CatalogParser;
import com.lcsc.service.crawler.parser.ProductParser;
import com.lcsc.service.crawler.LcscApiService;
import com.lcsc.service.crawler.LcscApiEndpoints;
import com.lcsc.service.ProductService;
import com.lcsc.service.TaskLogService;
import com.lcsc.entity.Product;
import com.lcsc.controller.CrawlerWebSocketController;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 立创商城爬虫核心服务
 * 协调各个模块完成爬取任务，支持新旧API
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 * @updated 2025-09-05
 */
@Service
public class LcscCrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(LcscCrawlerService.class);

    // 使用新API端点
    private static final String QUERY_PARAM_GROUP_URL = LcscApiEndpoints.Urls.QUERY_PARAM_GROUP;
    private static final String QUERY_LIST_URL = LcscApiEndpoints.Urls.QUERY_LIST;

    @Autowired
    private HttpClientService httpClientService;

    @Autowired
    private CatalogParser catalogParser;

    @Autowired
    private ProductParser productParser;

    @Autowired
    private ProductService productService;
    
    @Autowired
    private LcscApiService lcscApiService;
    
    @Autowired
    private TaskLogService taskLogService;

    @Autowired
    private CrawlerWebSocketController webSocketController;

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    /**
     * 获取并缓存产品目录
     * 
     * @return 目录映射列表
     */
    public List<Map<String, Object>> getCatalogMapping() {
        logger.info("开始获取产品目录");
        
        try {
            // 使用HttpClientService调用catalog API（POST请求）
            Map<String, Object> requestData = new HashMap<>();
            Map<String, Object> response = httpClientService.post(LcscApiEndpoints.Urls.CATALOG_LIST, requestData);
            
            // 数据真实性验证
            if (!validateApiResponse(response)) {
                logger.warn("API响应数据验证失败，可能存在网络问题或API变更");
                return new ArrayList<>();
            }
            
            // 检查响应是否包含错误
            if (response != null && response.containsKey("code")) {
                Integer code = (Integer) response.get("code");
                if (code != null && code != 200) {
                    String msg = (String) response.get("msg");
                    logger.warn("立创API返回错误: code={}, msg={}", code, msg);
                    
                    // 如果是系统繁忙，返回空列表，而不是抛出异常
                    if (code == 500) {
                        logger.info("立创API系统繁忙，返回空目录列表");
                        return new ArrayList<>();
                    }
                }
            }
            
            List<Map<String, Object>> catalogMapping = catalogParser.parseCatalogMapping(response);
            
            // 验证解析后的数据完整性
            if (catalogMapping != null && !catalogMapping.isEmpty()) {
                int validCatalogs = validateCatalogData(catalogMapping);
                logger.info("成功获取产品目录，包含 {} 个一级分类，其中 {} 个数据完整", 
                    catalogMapping.size(), validCatalogs);
            } else {
                logger.warn("解析后的目录数据为空");
            }
            
            return catalogMapping != null ? catalogMapping : new ArrayList<>();
            
        } catch (Exception e) {
            logger.error("获取产品目录失败", e);
            // 返回空列表而不是抛出异常，让系统能继续运行
            logger.info("返回空目录列表以保持系统运行");
            return new ArrayList<>();
        }
    }

    /**
     * 爬取指定目录的所有产品数据
     * 
     * @param catalogId 目录ID
     * @param categoryLevel1Id 一级分类ID（用于数据库存储）
     * @return 爬取的产品数量
     */
    public int crawlCatalogData(Integer catalogId, Integer categoryLevel1Id) {
        logger.info("开始爬取目录数据: catalogId={}, categoryLevel1Id={}", catalogId, categoryLevel1Id);
        
        AtomicInteger totalProducts = new AtomicInteger(0);
        
        try {
            // 1. 获取制造商列表
            List<Map<String, Object>> manufacturers = getManufacturers(catalogId);
            logger.info("目录 {} 包含 {} 个制造商", catalogId, manufacturers.size());
            
            // 2. 遍历制造商
            for (Map<String, Object> manufacturer : manufacturers) {
                Integer brandId = getIntegerValue(manufacturer, "id", null);
                if (brandId == null) {
                    logger.warn("制造商缺少ID字段: {}", manufacturer);
                    continue;
                }
                
                try {
                    int count = processManufacturer(catalogId, categoryLevel1Id, brandId);
                    totalProducts.addAndGet(count);
                    
                    logger.info("制造商 {} 爬取完成，产品数量: {}", brandId, count);
                    
                } catch (Exception e) {
                    logger.error("处理制造商 {} 时发生错误", brandId, e);
                    // 继续处理下一个制造商
                }
            }
            
            logger.info("目录 {} 爬取完成，总产品数量: {}", catalogId, totalProducts.get());
            return totalProducts.get();
            
        } catch (Exception e) {
            logger.error("爬取目录数据失败: catalogId={}", catalogId, e);
            throw new RuntimeException("爬取目录数据失败", e);
        }
    }

    /**
     * 处理指定制造商的数据
     * 
     * @param catalogId 目录ID
     * @param categoryLevel1Id 一级分类ID
     * @param brandId 品牌ID
     * @return 爬取的产品数量
     */
    private int processManufacturer(Integer catalogId, Integer categoryLevel1Id, Integer brandId) {
        logger.debug("开始处理制造商: brandId={}", brandId);
        
        AtomicInteger manufacturerProducts = new AtomicInteger(0);
        
        try {
            // 获取该制造商的封装列表
            List<Map<String, Object>> packages = getPackages(catalogId, brandId);
            logger.debug("制造商 {} 包含 {} 个封装", brandId, packages.size());
            
            // 遍历封装
            for (Map<String, Object> packageInfo : packages) {
                String packageName = getStringValue(packageInfo, "name", null);
                if (packageName == null || packageName.isEmpty()) {
                    logger.warn("封装缺少名称: {}", packageInfo);
                    continue;
                }
                
                try {
                    int count = processPackage(catalogId, categoryLevel1Id, brandId, packageName);
                    manufacturerProducts.addAndGet(count);
                    
                    logger.debug("封装 {} 处理完成，产品数量: {}", packageName, count);
                    
                } catch (Exception e) {
                    logger.error("处理封装 {} 时发生错误", packageName, e);
                    // 继续处理下一个封装
                }
            }
            
            return manufacturerProducts.get();
            
        } catch (Exception e) {
            logger.error("处理制造商失败: brandId={}", brandId, e);
            return 0;
        }
    }

    /**
     * 处理指定封装的分页数据
     * 
     * @param catalogId 目录ID
     * @param categoryLevel1Id 一级分类ID
     * @param brandId 品牌ID
     * @param packageName 封装名称
     * @return 爬取的产品数量
     */
    private int processPackage(Integer catalogId, Integer categoryLevel1Id, 
                             Integer brandId, String packageName) {
        logger.debug("开始处理封装: packageName={}", packageName);
        
        AtomicInteger packageProducts = new AtomicInteger(0);
        int currentPage = 1;
        int totalPages = 1; // 初始假设只有1页
        
        do {
            try {
                // 构造请求参数
                Map<String, Object> requestData = new HashMap<>();
                requestData.put("currentPage", currentPage);
                requestData.put("pageSize", 25);
                requestData.put("catalogIdList", Arrays.asList(catalogId));
                requestData.put("brandIdList", Arrays.asList(brandId));
                requestData.put("encapValueList", Arrays.asList(packageName));
                
                // 请求产品列表
                Map<String, Object> response = httpClientService.post(QUERY_LIST_URL, requestData);
                Map<String, Object> parseResult = productParser.parseProductListResponse(response);
                
                // 获取分页信息
                totalPages = (Integer) parseResult.get("totalPage");
                int totalCount = (Integer) parseResult.get("totalCount");
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> dataList = 
                    (List<Map<String, Object>>) parseResult.get("dataList");
                
                if (dataList.isEmpty()) {
                    logger.debug("第 {} 页没有数据，结束处理", currentPage);
                    break;
                }
                
                // 处理产品数据
                List<Product> products = new ArrayList<>();
                for (Map<String, Object> productData : dataList) {
                    try {
                        Product product = productParser.convertToProduct(
                            productData, categoryLevel1Id, catalogId);
                        products.add(product);
                    } catch (Exception e) {
                        logger.error("转换产品数据失败: {}", 
                            productData.get("productCode"), e);
                    }
                }
                
                // 批量保存产品
                if (!products.isEmpty()) {
                    saveProducts(products);
                    packageProducts.addAndGet(products.size());
                    
                    logger.debug("第 {} 页处理完成: {}/{} 页, 产品数量: {}, 总数量: {}", 
                        currentPage, currentPage, totalPages, products.size(), totalCount);
                }
                
                currentPage++;
                
            } catch (Exception e) {
                logger.error("处理第 {} 页时发生错误", currentPage, e);
                // 继续处理下一页
                currentPage++;
            }
            
        } while (currentPage <= totalPages);
        
        logger.debug("封装 {} 处理完成，总产品数量: {}", packageName, packageProducts.get());
        return packageProducts.get();
    }

    /**
     * 获取制造商列表
     * 
     * @param catalogId 目录ID
     * @return 制造商列表
     */
    private List<Map<String, Object>> getManufacturers(Integer catalogId) {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("catalogIdList", Arrays.asList(catalogId));
        
        Map<String, Object> response = httpClientService.post(QUERY_PARAM_GROUP_URL, requestData);
        return catalogParser.parseManufacturers(response);
    }

    /**
     * 获取封装列表
     * 
     * @param catalogId 目录ID
     * @param brandId 品牌ID
     * @return 封装列表
     */
    private List<Map<String, Object>> getPackages(Integer catalogId, Integer brandId) {
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("catalogIdList", Arrays.asList(catalogId));
        requestData.put("brandIdList", Arrays.asList(brandId));
        
        Map<String, Object> response = httpClientService.post(QUERY_PARAM_GROUP_URL, requestData);
        return catalogParser.parsePackages(response);
    }

    /**
     * 批量保存产品（增强版本，包含数据验证和WebSocket推送）
     * 
     * @param products 产品列表
     */
    private void saveProducts(List<Product> products) {
        try {
            List<Product> validProducts = new ArrayList<>();
            int invalidCount = 0;
            
            // 清洗和验证数据
            for (Product product : products) {
                Product cleanedProduct = cleanAndValidateProduct(product);
                if (cleanedProduct != null) {
                    validProducts.add(cleanedProduct);
                } else {
                    invalidCount++;
                    logger.debug("跳过无效产品数据: {}", 
                        product != null ? product.getProductCode() : "null");
                }
            }
            
            // 保存有效的产品
            int savedCount = 0;
            for (Product product : validProducts) {
                try {
                    productService.saveOrUpdateProduct(product);
                    savedCount++;
                } catch (Exception e) {
                    logger.error("保存单个产品失败: productCode={}", product.getProductCode(), e);
                }
            }
            
            logger.info("批量保存产品完成: 总数={}, 有效={}, 已保存={}, 无效={}", 
                products.size(), validProducts.size(), savedCount, invalidCount);
            
            // 推送保存统计到WebSocket
            if (webSocketController != null && savedCount > 0) {
                Map<String, Object> saveStats = Map.of(
                    "totalCount", products.size(),
                    "validCount", validProducts.size(),
                    "savedCount", savedCount,
                    "invalidCount", invalidCount,
                    "timestamp", System.currentTimeMillis()
                );
                webSocketController.broadcastStatistics(saveStats);
            }
            
        } catch (Exception e) {
            logger.error("批量保存产品失败", e);
            throw new RuntimeException("批量保存产品失败", e);
        }
    }

    /**
     * 保存单个产品（带验证和推送）
     */
    private boolean saveSingleProduct(Product product, String taskId) {
        try {
            Product cleanedProduct = cleanAndValidateProduct(product);
            if (cleanedProduct == null) {
                logger.debug("产品数据验证失败，跳过保存: {}", 
                    product != null ? product.getProductCode() : "null");
                return false;
            }
            
            productService.saveOrUpdateProduct(cleanedProduct);
            
            // 推送单个产品保存成功到WebSocket
            if (webSocketController != null && taskId != null) {
                webSocketController.broadcastLog(taskId, "INFO", 
                    String.format("产品保存成功: %s", cleanedProduct.getProductCode()),
                    Map.of("productCode", cleanedProduct.getProductCode()));
            }
            
            return true;
        } catch (Exception e) {
            logger.error("保存产品失败: productCode={}", 
                product != null ? product.getProductCode() : "null", e);
            return false;
        }
    }

    /**
     * 异步爬取单个产品
     * 
     * @param productCode 产品编码
     * @return CompletableFuture
     */
    public CompletableFuture<Boolean> crawlSingleProductAsync(String productCode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("开始爬取单个产品: {}", productCode);
                // 这里可以实现单个产品的爬取逻辑
                // 暂时返回true表示成功
                return true;
            } catch (Exception e) {
                logger.error("爬取单个产品失败: {}", productCode, e);
                return false;
            }
        }, executorService);
    }

    /**
     * 异步批量爬取产品
     * 
     * @param productCodes 产品编码列表
     * @return CompletableFuture
     */
    public CompletableFuture<Map<String, Boolean>> crawlProductBatchAsync(List<String> productCodes) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Boolean> results = new HashMap<>();
            
            for (String productCode : productCodes) {
                try {
                    logger.info("批量爬取产品: {}", productCode);
                    // 这里可以实现批量爬取逻辑
                    results.put(productCode, true);
                } catch (Exception e) {
                    logger.error("批量爬取产品失败: {}", productCode, e);
                    results.put(productCode, false);
                }
            }
            
            return results;
        }, executorService);
    }

    // 辅助方法：安全获取字符串值
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    // 辅助方法：安全获取整数值
    private Integer getIntegerValue(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                logger.debug("无法解析整数值: {} = {}", key, value);
            }
        }
        return defaultValue;
    }

    /**
     * 关闭资源
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            logger.info("爬虫服务线程池已关闭");
        }
    }

    // ========== 数据验证方法 ==========

    /**
     * 验证API响应数据的真实性和完整性
     */
    private boolean validateApiResponse(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            logger.warn("API响应为空");
            return false;
        }

        // 检查是否有基本的响应结构
        if (!response.containsKey("code") && !response.containsKey("data")) {
            logger.warn("API响应缺少基本结构字段 (code/data)");
            return false;
        }

        // 检查响应大小是否合理（防止异常大的响应）
        String responseStr = response.toString();
        if (responseStr.length() > 10 * 1024 * 1024) { // 10MB限制
            logger.warn("API响应数据过大，可能存在异常: {} 字符", responseStr.length());
            return false;
        }

        return true;
    }

    /**
     * 验证目录数据的完整性
     */
    private int validateCatalogData(List<Map<String, Object>> catalogMapping) {
        int validCount = 0;
        
        for (Map<String, Object> catalog : catalogMapping) {
            if (catalog != null && 
                catalog.containsKey("id") && 
                catalog.containsKey("name") &&
                catalog.get("id") != null &&
                catalog.get("name") != null &&
                !catalog.get("name").toString().trim().isEmpty()) {
                validCount++;
            } else {
                logger.debug("发现无效的目录数据: {}", catalog);
            }
        }
        
        return validCount;
    }

    /**
     * 验证产品数据的完整性
     */
    private boolean validateProductData(Product product) {
        if (product == null) {
            return false;
        }

        // 检查必要字段
        if (product.getProductCode() == null || product.getProductCode().trim().isEmpty()) {
            logger.debug("产品缺少产品编码");
            return false;
        }

        if (product.getCategoryLevel1Id() == null || product.getCategoryLevel2Id() == null) {
            logger.debug("产品缺少分类ID: productCode={}", product.getProductCode());
            return false;
        }

        // 检查产品编码格式（应该是C开头的8位数字）
        String productCode = product.getProductCode();
        if (!productCode.matches("^C\\d{7,}$")) {
            logger.debug("产品编码格式异常: {}", productCode);
            return false;
        }

        return true;
    }

    /**
     * 验证并清洗产品数据
     */
    private Product cleanAndValidateProduct(Product product) {
        if (product == null) {
            return null;
        }

        // 清理和验证产品编码
        if (product.getProductCode() != null) {
            String cleaned = product.getProductCode().trim().toUpperCase();
            product.setProductCode(cleaned);
        }

        // 清理品牌名称（处理&符号）
        if (product.getBrand() != null) {
            String cleaned = product.getBrand().replace("&", " ").trim();
            product.setBrand(cleaned);
        }

        // 清理封装名称（删除-符号）
        if (product.getPackageName() != null) {
            String cleaned = product.getPackageName().replace("-", "").trim();
            product.setPackageName(cleaned);
        }

        // 验证库存数量合理性
        if (product.getTotalStockQuantity() != null && product.getTotalStockQuantity() < 0) {
            logger.debug("产品库存数量异常，重置为0: productCode={}, stock={}", 
                product.getProductCode(), product.getTotalStockQuantity());
            product.setTotalStockQuantity(0);
        }

        return validateProductData(product) ? product : null;
    }
    
    // ======= 新API爬取方法（推荐使用） =======
    
    /**
     * 使用新API获取产品分类目录
     * 
     * @param taskId 任务ID（用于日志追踪）
     * @return 分类目录结果
     */
    public Map<String, Object> getCatalogListWithNewApi(String taskId) {
        TaskLogService.TaskExecutionContext context = taskLogService.createExecutionContext(taskId, "获取分类目录");
        
        try {
            context.startStep("FETCH_CATALOG", "使用新API获取分类目录", 10);
            
            Map<String, Object> catalogResult = lcscApiService.getCatalogList().get();
            
            context.completeStep("成功获取分类目录", Map.of(
                "catalogCount", ((List<?>) catalogResult.get("catalogList")).size(),
                "brandCount", ((List<?>) catalogResult.get("brandList")).size()
            ));
            
            logger.info("任务 {} - 成功获取分类目录：{} 个分类，{} 个品牌", 
                taskId, 
                ((List<?>) catalogResult.get("catalogList")).size(),
                ((List<?>) catalogResult.get("brandList")).size());
            
            return catalogResult;
            
        } catch (Exception e) {
            context.failStep("API_ERROR", "获取分类目录失败", e);
            logger.error("任务 {} - 获取分类目录失败", taskId, e);
            throw new RuntimeException("获取分类目录失败", e);
        } finally {
            context.cleanup();
        }
    }
    
    /**
     * 使用新API爬取指定分类的产品数据
     * 
     * @param taskId 任务ID
     * @param catalogId 分类ID
     * @param categoryLevel1Id 一级分类ID（用于数据库存储）
     * @param maxProducts 最大产品数量限制（0表示无限制）
     * @return 爬取的产品数量
     */
    public int crawlCatalogDataWithNewApi(String taskId, Integer catalogId, Integer categoryLevel1Id, int maxProducts) {
        TaskLogService.TaskExecutionContext context = taskLogService.createExecutionContext(taskId, "爬取分类产品");
        AtomicInteger totalProducts = new AtomicInteger(0);
        
        try {
            context.startStep("INIT", String.format("开始爬取分类 %d 的产品数据", catalogId), 0);
            
            // 1. 获取筛选条件
            context.startStep("FETCH_FILTERS", "获取筛选条件", 10);
            Map<String, Object> filterParams = new HashMap<>();
            filterParams.put(LcscApiEndpoints.FilterParams.CATALOG_ID_LIST, Arrays.asList(catalogId));
            
            Map<String, Object> filtersResult = lcscApiService.getQueryParamGroup(filterParams).get();
            context.completeStep("成功获取筛选条件", Map.of("filterCount", filtersResult.size()));
            
            // 2. 获取品牌列表
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> manufacturers = (List<Map<String, Object>>) filtersResult.get("Manufacturer");
            
            if (manufacturers == null || manufacturers.isEmpty()) {
                context.completeStep("该分类下没有制造商", null);
                return 0;
            }
            
            logger.info("任务 {} - 分类 {} 包含 {} 个制造商", taskId, catalogId, manufacturers.size());
            
            // 3. 遍历制造商进行爬取
            int processedManufacturers = 0;
            for (Map<String, Object> manufacturer : manufacturers) {
                if (maxProducts > 0 && totalProducts.get() >= maxProducts) {
                    logger.info("任务 {} - 达到最大产品数量限制 {}", taskId, maxProducts);
                    break;
                }
                
                String brandIdStr = getStringValue(manufacturer, "id", null);
                String brandName = getStringValue(manufacturer, "name", "Unknown");
                
                if (brandIdStr == null) {
                    logger.warn("任务 {} - 制造商缺少ID字段: {}", taskId, manufacturer);
                    continue;
                }
                
                try {
                    int brandProducts = processBrandWithNewApi(taskId, catalogId, categoryLevel1Id, 
                        brandIdStr, brandName, maxProducts - totalProducts.get());
                    totalProducts.addAndGet(brandProducts);
                    processedManufacturers++;
                    
                    // 更新进度
                    int progress = (int) (20 + (processedManufacturers * 70.0 / manufacturers.size()));
                    context.updateProgress(progress, String.format("已处理 %d/%d 制造商，爬取 %d 个产品", 
                        processedManufacturers, manufacturers.size(), totalProducts.get()));
                    
                } catch (Exception e) {
                    logger.error("任务 {} - 处理制造商 {} 时发生错误", taskId, brandName, e);
                    // 继续处理下一个制造商
                }
            }
            
            context.completeStep(String.format("分类爬取完成，总计 %d 个产品", totalProducts.get()), 
                Map.of("totalProducts", totalProducts.get(), "processedManufacturers", processedManufacturers));
            
            logger.info("任务 {} - 分类 {} 爬取完成，总产品数量: {}", taskId, catalogId, totalProducts.get());
            return totalProducts.get();
            
        } catch (Exception e) {
            context.failStep("CRAWL_ERROR", "分类数据爬取失败", e);
            logger.error("任务 {} - 爬取分类数据失败: catalogId={}", taskId, catalogId, e);
            throw new RuntimeException("爬取分类数据失败", e);
        } finally {
            context.cleanup();
        }
    }
    
    /**
     * 使用新API处理指定品牌的产品数据
     */
    private int processBrandWithNewApi(String taskId, Integer catalogId, Integer categoryLevel1Id, 
                                      String brandId, String brandName, int maxProducts) {
        AtomicInteger brandProducts = new AtomicInteger(0);
        
        try {
            taskLogService.logTaskProgress(taskId, "PROCESS_BRAND", 
                String.format("开始处理品牌: %s (ID: %s)", brandName, brandId), null);
            
            // 构建筛选参数
            Map<String, Object> filterParams = new HashMap<>();
            filterParams.put(LcscApiEndpoints.FilterParams.CATALOG_ID_LIST, Arrays.asList(catalogId));
            filterParams.put(LcscApiEndpoints.FilterParams.BRAND_ID_LIST, Arrays.asList(brandId));
            
            // 分页爬取产品
            int currentPage = 1;
            int pageSize = Math.min(maxProducts > 0 ? maxProducts : LcscApiEndpoints.Config.DEFAULT_PAGE_SIZE, 50);
            
            while (maxProducts == 0 || brandProducts.get() < maxProducts) {
                filterParams.put(LcscApiEndpoints.FilterParams.CURRENT_PAGE, currentPage);
                filterParams.put(LcscApiEndpoints.FilterParams.PAGE_SIZE, pageSize);
                
                Map<String, Object> queryResult = lcscApiService.getQueryList(filterParams).get();
                
                int totalPages = (Integer) queryResult.get("totalPages");
                int totalRows = (Integer) queryResult.get("totalRows");
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) queryResult.get("dataList");
                
                if (dataList.isEmpty()) {
                    logger.debug("任务 {} - 品牌 {} 第 {} 页没有数据", taskId, brandName, currentPage);
                    break;
                }
                
                // 处理产品数据
                List<Product> products = new ArrayList<>();
                for (Map<String, Object> productData : dataList) {
                    if (maxProducts > 0 && brandProducts.get() >= maxProducts) {
                        break;
                    }
                    
                    try {
                        Product product = productParser.convertToProduct(productData, categoryLevel1Id, catalogId);
                        products.add(product);
                        brandProducts.incrementAndGet();
                    } catch (Exception e) {
                        logger.error("任务 {} - 转换产品数据失败: {}", taskId, 
                            productData.get("productCode"), e);
                    }
                }
                
                // 批量保存产品
                if (!products.isEmpty()) {
                    saveProducts(products);
                    
                    taskLogService.logTaskProgress(taskId, "SAVE_PRODUCTS", 
                        String.format("品牌 %s 第 %d 页保存 %d 个产品", brandName, currentPage, products.size()), 
                        brandProducts.get());
                }
                
                logger.debug("任务 {} - 品牌 {} 第 {}/{} 页处理完成，产品数量: {}, 总数: {}", 
                    taskId, brandName, currentPage, totalPages, products.size(), totalRows);
                
                currentPage++;
                
                // 检查是否还有更多页面
                if (currentPage > totalPages) {
                    break;
                }
            }
            
            logger.info("任务 {} - 品牌 {} 处理完成，产品数量: {}", taskId, brandName, brandProducts.get());
            return brandProducts.get();
            
        } catch (Exception e) {
            taskLogService.logTaskFailed(taskId, String.format("处理品牌 %s 失败: %s", brandName, e.getMessage()));
            logger.error("任务 {} - 处理品牌 {} 失败", taskId, brandName, e);
            return brandProducts.get();
        }
    }
}
