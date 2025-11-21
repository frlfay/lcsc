package com.lcsc.service.crawler.v3;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lcsc.controller.CrawlerWebSocketController;
import com.lcsc.entity.CategoryLevel2Code;
import com.lcsc.entity.CategoryLevel3Code;
import com.lcsc.entity.Product;
import com.lcsc.mapper.CategoryLevel2CodeMapper;
import com.lcsc.mapper.ProductMapper;
import com.lcsc.service.CategoryLevel3CodeService;
import com.lcsc.service.ProductService;
import com.lcsc.service.crawler.FileDownloadService;
import com.lcsc.service.crawler.LcscApiService;

import jakarta.annotation.PreDestroy;

/**
 * 分类爬虫多线程工作池V3
 * 使用线程池并发执行分类爬取任务
 *
 * @author lcsc-crawler
 * @since 2025-10-08
 */
@Service
public class CategoryCrawlerWorkerPool {

    private static final Logger log = LoggerFactory.getLogger(CategoryCrawlerWorkerPool.class);

    @Autowired
    private CrawlerTaskQueueService queueService;

    @Autowired
    private LcscApiService apiService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryLevel2CodeMapper categoryMapper;

    @Autowired
    private CategoryLevel3CodeService level3Service;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private CrawlerWebSocketController webSocketController;

    @Autowired
    private FileDownloadService fileDownloadService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${crawler.storage.base-path}")
    private String storageBasePath;

    @Value("${crawler.storage.image-dir:images}")
    private String imageDirName;

    @Value("${crawler.storage.pdf-dir:pdfs}")
    private String pdfDirName;

    private ExecutorService executorService;
    private volatile boolean isRunning = false;
    private int workerThreadCount = 3; // 默认3个线程，可配置

    private static final String STATE_KEY = "crawler:state";
    private static final String PROGRESS_PREFIX = "crawler:progress:";
    private static final Pattern IMAGE_SIZE_PATTERN = Pattern.compile("/(\\d+)x(\\d+)/");

    /**
     * 启动Worker池
     */
    public synchronized void start() {
        if (isRunning) {
            log.warn("Worker池已在运行中");
            throw new RuntimeException("Worker池已在运行中");
        }

        log.info("========== 启动Worker池 ==========");

        // 从配置读取线程数（2-4）
        workerThreadCount = getWorkerThreadCountFromConfig();
        log.info("配置的工作线程数: {}", workerThreadCount);

        isRunning = true;
        redisTemplate.opsForHash().put(STATE_KEY, "isRunning", true);
        redisTemplate.opsForHash().put(STATE_KEY, "workerThreadCount", workerThreadCount);

        // 创建固定大小的线程池
        executorService = Executors.newFixedThreadPool(workerThreadCount);

        // 启动多个Worker线程
        for (int i = 1; i <= workerThreadCount; i++) {
            final int workerId = i;
            executorService.submit(() -> workerLoop(workerId));
            log.info("Worker-{} 已启动", workerId);
        }

        log.info("========== Worker池启动完成，共 {} 个线程 ==========", workerThreadCount);
    }

    /**
     * 停止Worker池（当前任务执行完后停止）
     */
    public synchronized void stop() {
        if (!isRunning) {
            log.warn("Worker池未运行");
            return;
        }

        log.info("========== 收到停止信号 ==========");
        isRunning = false;
        redisTemplate.opsForHash().put(STATE_KEY, "isRunning", false);

        log.info("Worker池将在所有当前任务完成后停止");
    }

    /**
     * Worker工作循环
     */
    private void workerLoop(int workerId) {
        log.info("Worker-{} 进入工作循环", workerId);

        while (isRunning) {
            try {
                // 1. 从队列弹出任务
                String taskId = queueService.popNextTask(workerId);

                if (taskId == null) {
                    // 队列为空，等待
                    Thread.sleep(2000);
                    continue;
                }

                // 2. 执行任务
                log.info("Worker-{} 开始执行任务: {}", workerId, taskId);
                boolean success = executeCategoryTask(taskId, workerId);

                // 3. 完成任务
                queueService.completeTask(taskId, success, success ? null : "执行失败");

                // 4. 检查是否需要停止
                if (!isRunning) {
                    log.info("Worker-{} 检测到停止信号，准备退出", workerId);
                    break;
                }

            } catch (InterruptedException e) {
                log.warn("Worker-{} 被中断", workerId);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Worker-{} 执行异常", workerId, e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("Worker-{} 已退出工作循环", workerId);
    }

    /**
     * 执行单个分类爬取任务（支持二级和三级分类）
     */
    private boolean executeCategoryTask(String taskId, int workerId) {
        try {
            // 1. 获取任务详情
            Map<Object, Object> taskMap = queueService.getTaskDetails(taskId);
            if (taskMap.isEmpty()) {
                log.error("Worker-{} 任务详情为空: {}", workerId, taskId);
                return false;
            }

            // 2. 从任务详情中提取信息
            Integer categoryId = Integer.valueOf((String) taskMap.get("categoryId"));
            String categoryLevel = (String) taskMap.get("categoryLevel"); // "level2" 或 "level3"
            String catalogApiId = (String) taskMap.get("catalogApiId"); // 立创API的catalogId
            String catalogName = (String) taskMap.get("catalogName");
            String level1Name = (String) taskMap.get("level1Name");

            log.info("Worker-{} 开始爬取分类: {} > {} (级别:{}, DB_ID:{}, API_ID:{})",
                workerId, level1Name, catalogName, categoryLevel, categoryId, catalogApiId);

            // 3. 根据分类级别更新对应表的状态
            CategoryLevel2Code level2Category = null;
            CategoryLevel3Code level3Category = null;
            Integer categoryLevel1Id = null;
            Integer categoryLevel2Id = null;

            if ("level2".equals(categoryLevel)) {
                // 二级分类
                level2Category = categoryMapper.selectById(categoryId);
                if (level2Category == null) {
                    log.error("Worker-{} 二级分类不存在: {}", workerId, categoryId);
                    return false;
                }
                level2Category.setCrawlStatus("PROCESSING");
                level2Category.setCurrentPage(0);
                level2Category.setCrawledProducts(0);
                level2Category.setTotalProducts(0);
                categoryMapper.updateById(level2Category);

                categoryLevel1Id = level2Category.getCategoryLevel1Id();
                categoryLevel2Id = level2Category.getId();

            } else if ("level3".equals(categoryLevel)) {
                // 三级分类
                level3Category = level3Service.getById(categoryId);
                if (level3Category == null) {
                    log.error("Worker-{} 三级分类不存在: {}", workerId, categoryId);
                    return false;
                }
                level3Category.setCrawlStatus("PROCESSING");
                level3Category.setCurrentPage(0);
                level3Category.setCrawledProducts(0);
                level3Category.setTotalProducts(0);
                level3Service.updateById(level3Category);

                categoryLevel1Id = level3Category.getCategoryLevel1Id();
                categoryLevel2Id = level3Category.getCategoryLevel2Id();

                // 同步更新父二级分类状态为PROCESSING（如果还不是）
                if (categoryLevel2Id != null) {
                    CategoryLevel2Code parentLevel2 = categoryMapper.selectById(categoryLevel2Id);
                    if (parentLevel2 != null && !"PROCESSING".equals(parentLevel2.getCrawlStatus())) {
                        parentLevel2.setCrawlStatus("PROCESSING");
                        categoryMapper.updateById(parentLevel2);
                        log.info("Worker-{} 同步更新父二级分类状态: {} -> PROCESSING", workerId, parentLevel2.getCategoryLevel2Name());
                    }
                }

            } else {
                log.error("Worker-{} 未知的分类级别: {}", workerId, categoryLevel);
                return false;
            }

            // 将分类名称映射写入Redis，便于其他组件/下载阶段快速查找
            try {
                if (categoryLevel1Id != null) {
                    redisTemplate.opsForHash().put("crawler:category:names:l1",
                        String.valueOf(categoryLevel1Id), level1Name);
                }
                if (categoryLevel2Id != null && "level2".equals(categoryLevel)) {
                    redisTemplate.opsForHash().put("crawler:category:names:l2",
                        String.valueOf(categoryLevel2Id), catalogName);
                } else if (categoryLevel2Id != null && level2Category == null) {
                    // 对于三级分类，也需要获取二级分类名称
                    CategoryLevel2Code parentLevel2 = categoryMapper.selectById(categoryLevel2Id);
                    if (parentLevel2 != null) {
                        redisTemplate.opsForHash().put("crawler:category:names:l2",
                            String.valueOf(categoryLevel2Id), parentLevel2.getCategoryLevel2Name());
                    }
                }
                if ("level3".equals(categoryLevel)) {
                    redisTemplate.opsForHash().put("crawler:category:names:l3",
                        String.valueOf(categoryId), catalogName);
                }
            } catch (Exception e) {
                log.warn("写入分类名称映射到Redis失败: categoryId={}, err={}",
                    categoryId, e.getMessage());
            }

            // 4. 推送任务开始事件
            broadcastWebSocket("TASK_START", Map.of(
                "taskId", taskId,
                "catalogId", categoryId,
                "catalogName", catalogName,
                "level1Name", level1Name,
                "categoryLevel", categoryLevel,
                "workerId", workerId
            ));

            // 5. 第一次请求，获取总页数和总产品数（使用立创API的catalogId）
            Map<String, Object> filterParams = new HashMap<>();
            filterParams.put("catalogIdList", List.of(catalogApiId));
            filterParams.put("currentPage", 1);
            filterParams.put("pageSize", 25); // 与curl请求一致

            Map<String, Object> firstPage = apiService.getQueryList(filterParams).join();
            int totalPages = (int) firstPage.get("totalPages");
            int totalProducts = (int) firstPage.get("totalRows");

            log.info("Worker-{} 分类 {} ({}) 总页数: {}, 总产品数: {}",
                workerId, catalogName, categoryLevel, totalPages, totalProducts);

            // 更新任务和分类信息
            redisTemplate.opsForHash().put("crawler:task:" + taskId, "totalPages", totalPages);
            redisTemplate.opsForHash().put("crawler:task:" + taskId, "totalProducts", totalProducts);

            if (level2Category != null) {
                level2Category.setTotalProducts(totalProducts);
                categoryMapper.updateById(level2Category);
            } else if (level3Category != null) {
                level3Category.setTotalProducts(totalProducts);
                level3Service.updateById(level3Category);
            }

            // 6. 处理第一页
            Integer categoryLevel3IdForProduct = "level3".equals(categoryLevel) ? categoryId : null;
            int savedCount = processAndSavePageData(categoryLevel1Id, categoryLevel2Id, categoryLevel3IdForProduct,
                level1Name, catalogName, firstPage, workerId);

            // 保存第一页的完整原始API响应
            String firstPageRawResponse = (String) firstPage.get("rawResponse");
            log.info("Worker-{} 第一页原始响应: 存在={}, 长度={}, 空检查={}",
                workerId,
                firstPageRawResponse != null,
                firstPageRawResponse != null ? firstPageRawResponse.length() : 0,
                firstPageRawResponse == null || firstPageRawResponse.isBlank());

            if (firstPageRawResponse != null && !firstPageRawResponse.isBlank()) {
                log.info("Worker-{} 已获取第一页原始API响应，长度: {} (数据将直接存储到数据库)", workerId, firstPageRawResponse.length());
            } else {
                log.warn("Worker-{} 第一页原始API响应为空或null，跳过处理", workerId);
            }

            updateProgress(taskId, categoryId, 1, totalPages, savedCount, totalProducts, workerId);

            int totalSaved = savedCount;

            // 7. 循环处理剩余页面
            for (int page = 2; page <= totalPages; page++) {
                // 检查是否需要停止
                if (!isRunning) {
                    log.warn("Worker-{} 检测到停止信号，中断爬取: {}", workerId, catalogName);
                    // 停止时，如果已经爬取了部分数据，标记为已完成；否则标记为失败
                    if (totalSaved > 0) {
                        if (level2Category != null) {
                            level2Category.setCrawlStatus("COMPLETED");
                            level2Category.setCrawlProgress(100);
                            level2Category.setCrawledProducts(totalSaved);
                            level2Category.setLastCrawlTime(LocalDateTime.now());
                            categoryMapper.updateById(level2Category);
                        } else if (level3Category != null) {
                            level3Category.setCrawlStatus("COMPLETED");
                            level3Category.setCrawlProgress(100);
                            level3Category.setCrawledProducts(totalSaved);
                            level3Category.setLastCrawlTime(LocalDateTime.now());
                            level3Service.updateById(level3Category);
                            // 同步更新父二级分类状态
                            if (categoryLevel2Id != null) {
                                updateParentLevel2StatusIfNeeded(categoryLevel2Id, workerId);
                            }
                        }
                        log.info("Worker-{} 已停止，但已爬取 {} 个产品，标记分类为已完成", workerId, totalSaved);
                        return true;  // 返回true，因为已有数据
                    } else {
                        if (level2Category != null) {
                            level2Category.setCrawlStatus("STOPPED");
                            level2Category.setCrawlProgress(0);
                            categoryMapper.updateById(level2Category);
                        } else if (level3Category != null) {
                            level3Category.setCrawlStatus("STOPPED");
                            level3Category.setCrawlProgress(0);
                            level3Service.updateById(level3Category);
                            // 同步更新父二级分类状态
                            if (categoryLevel2Id != null) {
                                updateParentLevel2StatusIfNeeded(categoryLevel2Id, workerId);
                            }
                        }
                        log.info("Worker-{} 已停止，未爬取任何产品，标记分类为已停止", workerId);
                        return false;
                    }
                }

                filterParams.put("currentPage", page);

                Map<String, Object> pageData = apiService.getQueryList(filterParams).join();
                savedCount = processAndSavePageData(categoryLevel1Id, categoryLevel2Id, categoryLevel3IdForProduct,
                    level1Name, catalogName, pageData, workerId);

                // 保存完整的原始API响应
                String rawResponse = (String) pageData.get("rawResponse");
                log.info("Worker-{} 检查原始响应: rawResponse存在={}, 长度={}, 空检查={}",
                    workerId,
                    rawResponse != null,
                    rawResponse != null ? rawResponse.length() : 0,
                    rawResponse == null || rawResponse.isBlank());

                if (rawResponse != null && !rawResponse.isBlank()) {
                    log.info("Worker-{} 已获取原始API响应，长度: {} (数据将直接存储到数据库)", workerId, rawResponse.length());
                } else {
                    log.warn("Worker-{} 原始API响应为空或null，跳过处理", workerId);
                }

                totalSaved += savedCount;

                updateProgress(taskId, categoryId, page, totalPages, totalSaved, totalProducts, workerId);

                // 推送进度
                int progress = (page * 100) / totalPages;
                broadcastWebSocket("PROGRESS_UPDATE", Map.of(
                    "catalogId", categoryId,
                    "catalogName", catalogName,
                    "currentPage", page,
                    "totalPages", totalPages,
                    "progress", progress,
                    "crawledProducts", totalSaved,
                    "totalProducts", totalProducts,
                    "categoryLevel", categoryLevel,
                    "workerId", workerId
                ));

                // 延迟，避免请求过快
                Thread.sleep(500);
            }

            // 8. 完成
            if (level2Category != null) {
                level2Category.setCrawlStatus("COMPLETED");
                level2Category.setCrawlProgress(100);
                level2Category.setCrawledProducts(totalSaved);
                level2Category.setLastCrawlTime(LocalDateTime.now());
                categoryMapper.updateById(level2Category);
            } else if (level3Category != null) {
                level3Category.setCrawlStatus("COMPLETED");
                level3Category.setCrawlProgress(100);
                level3Category.setCrawledProducts(totalSaved);
                level3Category.setLastCrawlTime(LocalDateTime.now());
                level3Service.updateById(level3Category);

                // 检查并同步更新父二级分类状态
                if (categoryLevel2Id != null) {
                    updateParentLevel2StatusIfNeeded(categoryLevel2Id, workerId);
                }
            }

            // 9. 推送完成事件
            broadcastWebSocket("TASK_COMPLETE", Map.of(
                "taskId", taskId,
                "catalogId", categoryId,
                "catalogName", catalogName,
                "categoryLevel", categoryLevel,
                "success", true,
                "totalProducts", totalSaved,
                "workerId", workerId
            ));

            log.info("Worker-{} 分类爬取完成: {} ({}) (产品数: {})",
                workerId, catalogName, categoryLevel, totalSaved);
            return true;

        } catch (Exception e) {
            log.error("Worker执行任务失败: taskId={}", taskId, e);

            // 推送失败事件
            try {
                Map<Object, Object> taskMap = queueService.getTaskDetails(taskId);
                broadcastWebSocket("TASK_FAILED", Map.of(
                    "taskId", taskId,
                    "catalogId", taskMap.get("catalogId"),
                    "error", e.getMessage()
                ));
            } catch (Exception ex) {
                log.error("推送失败事件异常", ex);
            }

            return false;
        }
    }

    /**
     * 处理并保存页面数据（支持三级分类）
     */
    private int processAndSavePageData(Integer categoryLevel1Id, Integer categoryLevel2Id, Integer categoryLevel3Id,
                                       String level1Name, String catalogName, Map<String, Object> pageData, int workerId) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) pageData.get("dataList");

        if (products == null || products.isEmpty()) {
            return 0;
        }

        int savedCount = 0;
        for (Map<String, Object> productData : products) {
            try {
                Product product = convertToProduct(productData, categoryLevel1Id, categoryLevel2Id, categoryLevel3Id,
                    level1Name, catalogName);
                boolean saved = productService.saveOrUpdateProduct(product);
                if (saved) {
                    savedCount++;
                }
            } catch (Exception e) {
                log.error("Worker-{} 保存产品失败: productCode={}",
                    workerId, productData.get("productCode"), e);
            }
        }

        return savedCount;
    }

    /**
     * 更新进度
     */
    private void updateProgress(String taskId, Integer catalogId,
                                int currentPage, int totalPages,
                                int crawledProducts, int totalProducts,
                                int workerId) {
        try {
            // Redis任务进度
            redisTemplate.opsForHash().put("crawler:task:" + taskId, "currentPage", currentPage);
            redisTemplate.opsForHash().put("crawler:task:" + taskId, "crawledProducts", crawledProducts);

            // Redis进度详情
            int progress = totalPages > 0 ? (currentPage * 100) / totalPages : 0;
            Map<String, Object> progressMap = Map.of(
                "currentPage", currentPage,
                "totalPages", totalPages,
                "crawledProducts", crawledProducts,
                "totalProducts", totalProducts,
                "progress", progress,
                "lastUpdateTime", LocalDateTime.now().toString(),
                "workerId", workerId
            );
            redisTemplate.opsForHash().putAll(PROGRESS_PREFIX + catalogId, progressMap);

            // 数据库
            CategoryLevel2Code category = categoryMapper.selectById(catalogId);
            if (category != null) {
                category.setCurrentPage(currentPage);
                category.setCrawledProducts(crawledProducts);
                category.setCrawlProgress(progress);
                categoryMapper.updateById(category);
            }
        } catch (Exception e) {
            log.error("更新进度失败: taskId={}, catalogId={}", taskId, catalogId, e);
        }
    }

    /**
     * 转换产品数据（支持多图片下载和三级分类）
     */
    private Product convertToProduct(Map<String, Object> productData, Integer categoryLevel1Id, Integer categoryLevel2Id, Integer categoryLevel3Id,
                                     String level1Name, String catalogName) {
        Product product = new Product();

        product.setProductCode((String) productData.get("productCode"));
        product.setCategoryLevel1Id(categoryLevel1Id);
        product.setCategoryLevel2Id(categoryLevel2Id);
        product.setCategoryLevel3Id(categoryLevel3Id);  // 新增：设置三级分类ID

        // 同步填充分类名称（便于前端展示及导出）
        product.setCategoryLevel1Name(level1Name);
        // 如果是三级分类任务，catalogName是三级分类名称
        if (categoryLevel3Id != null) {
            // TODO: 可能需要同时保存二级分类名称
            product.setCategoryLevel2Name(catalogName);
        } else {
            product.setCategoryLevel2Name(catalogName);
        }

        String brandNameEn = (String) productData.get("brandNameEn");
        if (brandNameEn != null) {
            product.setBrand(brandNameEn.replace("&", " "));
        }
        product.setModel((String) productData.get("productModel"));

        String encap = (String) productData.get("encapStandard");
        if (encap != null && !"-".equals(encap)) {
            product.setPackageName(encap);
        }

        Object stockObj = productData.get("stockNumber");
        if (stockObj != null) {
            product.setTotalStockQuantity(stockObj instanceof Integer ?
                (Integer) stockObj : Integer.parseInt(stockObj.toString()));
        }

        String intro = (String) productData.get("productIntroEn");
        if (intro != null && intro.length() > 60) {
            intro = intro.substring(0, 60);
        }
        product.setBriefDescription(intro);

        // --- 处理阶梯价格 ---
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> priceList = (List<Map<String, Object>>) productData.get("productPriceList");
        product.setTierPrices(parseTierPrices(priceList));
        product.setTierPricesLastUpdate(LocalDate.now());
        product.setTierPricesManualEdit(false);
        // 同步填充拆分后的前5阶梯价字段（数量/价格）
        processLadderPrices(product, priceList);

        // --- 处理详细参数 ---
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> paramList = (List<Map<String, Object>>) productData.get("paramVOList");
        product.setDetailedParameters(parseDetailedParameters(paramList));
        // 同步生成简明参数文本，格式类似："声压（SPL）: 85dB; 谐振频率: 2.731kHz"
        product.setParametersText(formatParametersText(paramList));

        product.setProductImagesInfo("[]");
        product.setMainImageLocalPath(null);

        ImageSelection preferredImage = selectPreferredImage(productData);
        if (preferredImage != null) {
            Path imagePath = resolveStoragePath(imageDirName).resolve(preferredImage.filename());
            String imagePathStr = imagePath.toString();

            product.setImageName(preferredImage.filename());
            product.setImageLocalPath(imagePathStr);
            product.setMainImageLocalPath(imagePathStr);

            // 设置主图URL（按优先顺序选择的图片URL）
            product.setProductImageUrlBig(preferredImage.url());

            // 序列化所有图片信息（包含候选+优选）
            String imagesInfoJson = serializeAllImagesInfo(productData, preferredImage, imagePathStr);
            product.setProductImagesInfo(imagesInfoJson);

            fileDownloadService.submitDownloadTask(preferredImage.url(), imagePathStr, "image");
            log.info("Worker 产品 {} 选择图片 {} (优先级 {})", product.getProductCode(), preferredImage.filename(), preferredImage.priority());
        } else {
            product.setImageName(null);
            product.setImageLocalPath(null);
        }

        // 处理PDF下载
        String pdfUrl = (String) productData.get("pdfUrl");
        log.debug("Worker 产品 {} PDF URL: {}", product.getProductCode(), pdfUrl);
        if (pdfUrl != null && !pdfUrl.isBlank()) {
            String normalizedPdfUrl = normalizeAssetUrl(pdfUrl);

            String pdfFilename = generatePdfFilename(product.getProductCode(), product.getBrand(), product.getModel());
            product.setPdfFilename(pdfFilename);
            Path pdfPath = resolveStoragePath(pdfDirName).resolve(pdfFilename);

            String pdfPathStr = pdfPath.toString();
            product.setPdfLocalPath(pdfPathStr);
            // 同步保存远程PDF URL，便于前端直接访问
            product.setPdfUrl(normalizedPdfUrl);
            fileDownloadService.submitDownloadTask(normalizedPdfUrl, pdfPathStr, "pdf");
            log.info("Worker 提交PDF下载任务: {} -> {}", pdfFilename, normalizedPdfUrl);
        } else {
            log.debug("Worker 产品 {} 没有PDF数据", product.getProductCode());
        }

        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        product.setLastCrawledAt(LocalDateTime.now());

        return product;
    }

    /**
     * 推送WebSocket消息
     */
    private void broadcastWebSocket(String type, Map<String, Object> data) {
        if (webSocketController != null) {
            try {
                webSocketController.broadcast(type, data);
            } catch (Exception e) {
                log.error("推送WebSocket消息失败: type={}", type, e);
            }
        }
    }

    /**
     * 从配置读取线程数
     */
    private int getWorkerThreadCountFromConfig() {
        try {
            // 从Redis或数据库读取配置
            // 这里简化为返回默认值3
            Object threadCount = redisTemplate.opsForHash().get(STATE_KEY, "workerThreadCount");
            if (threadCount != null) {
                int count = Integer.parseInt(threadCount.toString());
                // 限制在2-4之间
                return Math.max(2, Math.min(4, count));
            }
        } catch (Exception e) {
            log.warn("读取线程数配置失败，使用默认值", e);
        }

        return 3; // 默认3个线程
    }

    /**
     * 检查是否运行中
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 获取当前线程数
     */
    public int getWorkerThreadCount() {
        return workerThreadCount;
    }

    /**
     * 销毁时清理资源
     */
    @PreDestroy
    public void destroy() {
        if (isRunning) {
            stop();
        }

        if (executorService != null && !executorService.isShutdown()) {
            log.info("关闭线程池...");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("线程池未在30秒内关闭，强制关闭");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("等待线程池关闭被中断", e);
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("Worker池已销毁");
    }

    // ==================== 多图片支持辅助方法 ====================

    /**
     * 解析阶梯价格（统一使用人民币）
     * 使用字段：ladder（起订量）, productPrice（人民币单价），currency=CNY
     */
    private String parseTierPrices(List<Map<String, Object>> priceList) {
        if (priceList == null || priceList.isEmpty()) {
            return "[]";
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> tierPrices = new ArrayList<>();

            for (Map<String, Object> price : priceList) {
                Map<String, Object> tierPrice = new HashMap<>();
                tierPrice.put("ladder", getIntegerValue(price, "ladder", 0));
                tierPrice.put("price", getStringValue(price, "productPrice", ""));
                tierPrice.put("currency", "CNY");
                tierPrice.put("date", LocalDate.now().toString());
                tierPrices.add(tierPrice);
            }

            // 按阶梯数量排序
            tierPrices.sort((a, b) -> {
                Integer ladderA = (Integer) a.get("ladder");
                Integer ladderB = (Integer) b.get("ladder");
                return ladderA.compareTo(ladderB);
            });

            return objectMapper.writeValueAsString(tierPrices);

        } catch (Exception e) {
            log.error("解析阶梯价格时出错", e);
            return "[]";
        }
    }

    /**
     * 解析详细参数
     */
    private String parseDetailedParameters(List<Map<String, Object>> paramList) {
        if (paramList == null || paramList.isEmpty()) {
            return "{}";
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> parameters = new HashMap<>();

            for (Map<String, Object> param : paramList) {
                String paramNameEn = getStringValue(param, "paramNameEn", "");
                String paramNameCn = getStringValue(param, "paramName", "");
                String paramValueEn = getStringValue(param, "paramValueEn", "");
                String paramValueCn = getStringValue(param, "paramValue", "");

                if (!paramNameEn.isEmpty()) {
                    Map<String, String> paramInfo = new HashMap<>();
                    paramInfo.put("nameEn", paramNameEn);
                    paramInfo.put("nameCn", cleanParameterValue(paramNameCn));
                    paramInfo.put("valueEn", paramValueEn);
                    paramInfo.put("valueCn", cleanParameterValue(paramValueCn));

                    parameters.put(paramNameEn, paramInfo);
                }
            }

            return objectMapper.writeValueAsString(parameters);

        } catch (Exception e) {
            log.error("解析详细参数时出错", e);
            return "{}";
        }
    }

    private Path resolveStoragePath(String directoryName) {
        Path basePath;
        if (storageBasePath == null || storageBasePath.isBlank()) {
            basePath = Paths.get(System.getProperty("user.dir"), "data");
        } else {
            basePath = Paths.get(storageBasePath);
        }

        if (directoryName == null || directoryName.isBlank()) {
            return basePath.normalize();
        }

        return basePath.resolve(directoryName).normalize();
    }

    private ImageSelection selectPreferredImage(Map<String, Object> productData) {
        @SuppressWarnings("unchecked")
        List<String> productImages = (List<String>) productData.get("productImages");

        Set<String> candidates = new LinkedHashSet<>();
        if (productImages != null) {
            for (String imageUrl : productImages) {
                String normalized = normalizeAssetUrl(imageUrl);
                if (normalized != null) {
                    candidates.add(normalized);
                }
            }
        }

        String fallbackUrl = (String) productData.get("productImageUrl");
        String normalizedFallback = normalizeAssetUrl(fallbackUrl);
        if (normalizedFallback != null) {
            candidates.add(normalizedFallback);
        }

        return candidates.stream()
            .map(this::toImageSelection)
            .filter(Objects::nonNull)
            .min(Comparator
                .comparingInt(ImageSelection::priority)
                .thenComparing(ImageSelection::resolution, Comparator.reverseOrder())
                .thenComparing(ImageSelection::filename))
            .orElse(null);
    }

    private String normalizeAssetUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // 过滤掉包含"null"字符串的URL（立创API可能返回"null"字符串）
        if (trimmed.equalsIgnoreCase("null") || trimmed.contains(":null")) {
            return null;
        }
        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }
        if (!trimmed.startsWith("http")) {
            return "https:" + trimmed;
        }
        return trimmed;
    }

    private ImageSelection toImageSelection(String url) {
        String filename = extractFilename(url);
        if (filename.isEmpty()) {
            return null;
        }
        int priority = computeImagePriority(filename);
        int resolution = computeImageResolutionScore(url);
        return new ImageSelection(url, filename, priority, resolution);
    }

    private String extractFilename(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }

        String sanitized = url;
        int queryIndex = sanitized.indexOf('?');
        if (queryIndex >= 0) {
            sanitized = sanitized.substring(0, queryIndex);
        }

        int lastSlash = sanitized.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < sanitized.length() - 1) {
            return sanitized.substring(lastSlash + 1);
        }
        return sanitized;
    }

    private int computeImagePriority(String filename) {
        String lower = filename.toLowerCase();
        if (lower.contains("_front")) {
            return 0;
        }
        if (lower.contains("_blank")) {
            return 1;
        }
        if (lower.contains("_package")) {
            return 2;
        }
        return 3;
    }

    private int computeImageResolutionScore(String url) {
        if (url == null) {
            return 0;
        }
        Matcher matcher = IMAGE_SIZE_PATTERN.matcher(url);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignore) {
                return 0;
            }
        }
        return 0;
    }

    private String serializeImageInfo(ImageSelection selection, String localPath) {
        try {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            ObjectNode node = arrayNode.addObject();
            node.put("url", selection.url());
            node.put("filename", selection.filename());
            node.put("priority", selection.priority());
            node.put("resolution", selection.resolution());
            node.put("localPath", localPath);
            return objectMapper.writeValueAsString(arrayNode);
        } catch (Exception e) {
            log.error("序列化图片信息失败: {}", e.getMessage(), e);
            return "[]";
        }
    }

    /**
     * 将 paramVOList 转换为简明可读的参数文本
     * 规则：
     * - 名称优先使用中文 paramName，回退到 paramNameEn
     * - 将半角括号 () 替换为全角括号 （） 以匹配视觉需求
     * - 值优先使用 paramValue，回退到 paramValueEn
     * - 为兼容导出格式化：键值用冒号无空格分隔，条目之间以空格分隔
     * - 产出示例："声压（SPL）:85dB 谐振频率:2.731kHz"
     */
    private String formatParametersText(List<Map<String, Object>> paramList) {
        if (paramList == null || paramList.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> param : paramList) {
            String nameCn = getStringValue(param, "paramName", "");
            String nameEn = getStringValue(param, "paramNameEn", "");
            String valueCn = getStringValue(param, "paramValue", "");
            String valueEn = getStringValue(param, "paramValueEn", "");

            String name = !nameCn.isEmpty() ? nameCn : nameEn;
            String value = !valueCn.isEmpty() ? valueCn : valueEn;

            if (name.isEmpty() || value.isEmpty()) {
                continue;
            }

            // 视觉优化：使用全角括号
            String normalizedName = name.replace('(', '（').replace(')', '）');

            if (sb.length() > 0) {
                sb.append(' ');
            }
            // 与导出逻辑兼容：不在冒号后添加空格
            sb.append(normalizedName).append(':').append(value);
        }
        return sb.toString();
    }

    /**
     * 将 productPriceList（数组）拆分到产品的 1-5 阶梯数量/价格字段
     * - 数量取 ladder
     * - 价格取 productPrice（字符串），保持与已有处理器一致
     * - 最多填充前5阶
     */
    private void processLadderPrices(Product product, List<Map<String, Object>> priceList) {
        try {
            // 先清空
            product.setLadderPrice1Quantity(null);
            product.setLadderPrice1Price(null);
            product.setLadderPrice2Quantity(null);
            product.setLadderPrice2Price(null);
            product.setLadderPrice3Quantity(null);
            product.setLadderPrice3Price(null);
            product.setLadderPrice4Quantity(null);
            product.setLadderPrice4Price(null);
            product.setLadderPrice5Quantity(null);
            product.setLadderPrice5Price(null);

            if (priceList == null || priceList.isEmpty()) {
                return;
            }

            // 尽量按 ladder 从小到大排序，确保阶梯1是最小起订量
            List<Map<String, Object>> sorted = new ArrayList<>(priceList);
            sorted.sort((a, b) -> {
                Integer la = getIntegerValue(a, "ladder", Integer.MAX_VALUE);
                Integer lb = getIntegerValue(b, "ladder", Integer.MAX_VALUE);
                return Integer.compare(la, lb);
            });

            for (int i = 0; i < Math.min(sorted.size(), 5); i++) {
                Map<String, Object> priceData = sorted.get(i);
                Object ladder = priceData.get("ladder");
                // 统一使用 currencyPrice（CNY），回退 productPrice
                Object price = priceData.get("currencyPrice") != null ? priceData.get("currencyPrice") : priceData.get("productPrice");
                if (ladder == null || price == null) continue;

                Integer quantity = (ladder instanceof Number)
                    ? ((Number) ladder).intValue()
                    : Integer.parseInt(ladder.toString());
                java.math.BigDecimal priceValue = new java.math.BigDecimal(price.toString());

                switch (i) {
                    case 0 -> { product.setLadderPrice1Quantity(quantity); product.setLadderPrice1Price(priceValue); }
                    case 1 -> { product.setLadderPrice2Quantity(quantity); product.setLadderPrice2Price(priceValue); }
                    case 2 -> { product.setLadderPrice3Quantity(quantity); product.setLadderPrice3Price(priceValue); }
                    case 3 -> { product.setLadderPrice4Quantity(quantity); product.setLadderPrice4Price(priceValue); }
                    case 4 -> { product.setLadderPrice5Quantity(quantity); product.setLadderPrice5Price(priceValue); }
                }
            }
        } catch (Exception e) {
            log.error("处理阶梯价格失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 序列化所有候选图片信息（包含优选标记与本地路径，仅优选项包含localPath）
     */
    private String serializeAllImagesInfo(Map<String, Object> productData, ImageSelection preferred, String preferredLocalPath) {
        try {
            ArrayNode arrayNode = objectMapper.createArrayNode();

            // 收集候选（保持与选择逻辑一致）
            Set<String> candidates = new LinkedHashSet<>();
            @SuppressWarnings("unchecked")
            List<String> productImages = (List<String>) productData.get("productImages");
            if (productImages != null) {
                for (String imageUrl : productImages) {
                    String normalized = normalizeAssetUrl(imageUrl);
                    if (normalized != null) {
                        candidates.add(normalized);
                    }
                }
            }
            String fallbackUrl = (String) productData.get("productImageUrl");
            String normalizedFallback = normalizeAssetUrl(fallbackUrl);
            if (normalizedFallback != null) {
                candidates.add(normalizedFallback);
            }

            for (String url : candidates) {
                ImageSelection sel = toImageSelection(url);
                if (sel == null) continue;
                ObjectNode node = arrayNode.addObject();
                node.put("url", sel.url());
                node.put("filename", sel.filename());
                node.put("priority", sel.priority());
                node.put("resolution", sel.resolution());
                boolean isPreferred = preferred != null && url.equals(preferred.url());
                node.put("selected", isPreferred);
                node.put("localPath", isPreferred ? preferredLocalPath : null);
            }

            return objectMapper.writeValueAsString(arrayNode);

        } catch (Exception e) {
            log.error("序列化所有图片信息失败: {}", e.getMessage(), e);
            return "[]";
        }
    }

    private record ImageSelection(String url, String filename, int priority, int resolution) {}

    /**
     * 生成PDF文件名
     */
    private String generatePdfFilename(String productCode, String brand, String model) {
        String cleanBrand = cleanBrandName(brand);
        String cleanModel = model.replace("-", "");
        return String.format("%s_%s_%s.pdf", productCode, cleanBrand, cleanModel);
    }

    /**
     * 清理品牌名称（将&替换为空格）
     */
    private String cleanBrandName(String brandName) {
        if (brandName == null || brandName.isEmpty()) {
            return "";
        }
        return brandName.replace("&", " ").trim();
    }

    /**
     * 清理参数值（删除-)
     */
    private String cleanParameterValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("-", "").trim();
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
                log.debug("无法解析整数值: {} = {}", key, value);
            }
        }
        return defaultValue;
    }

    /**
     * 检查并更新父二级分类状态
     * 当该二级分类下的所有三级分类都完成时，将父二级分类标记为COMPLETED
     *
     * @param parentLevel2Id 父二级分类ID
     * @param workerId Worker ID
     */
    private void updateParentLevel2StatusIfNeeded(Integer parentLevel2Id, int workerId) {
        try {
            // 查询该二级分类下的所有三级分类
            List<CategoryLevel3Code> level3List = level3Service.getByLevel2Id(parentLevel2Id);

            if (level3List == null || level3List.isEmpty()) {
                log.debug("Worker-{} 父二级分类 {} 下没有三级分类", workerId, parentLevel2Id);
                return;
            }

            // 统计状态
            int totalCount = level3List.size();
            int completedCount = 0;
            int processingCount = 0;
            int totalProducts = 0;

            for (CategoryLevel3Code level3 : level3List) {
                String status = level3.getCrawlStatus();
                if ("COMPLETED".equals(status)) {
                    completedCount++;
                    if (level3.getCrawledProducts() != null) {
                        totalProducts += level3.getCrawledProducts();
                    }
                } else if ("PROCESSING".equals(status) || "IN_QUEUE".equals(status)) {
                    processingCount++;
                }
            }

            // 获取父二级分类
            CategoryLevel2Code parentLevel2 = categoryMapper.selectById(parentLevel2Id);
            if (parentLevel2 == null) {
                log.warn("Worker-{} 父二级分类不存在: {}", workerId, parentLevel2Id);
                return;
            }

            log.debug("Worker-{} 检查父二级分类 [{}]: 总计={}, 已完成={}, 处理中={}",
                workerId, parentLevel2.getCategoryLevel2Name(), totalCount, completedCount, processingCount);

            // 如果所有三级分类都完成了，更新父二级分类为COMPLETED
            if (completedCount == totalCount) {
                parentLevel2.setCrawlStatus("COMPLETED");
                parentLevel2.setCrawlProgress(100);
                parentLevel2.setCrawledProducts(totalProducts);
                parentLevel2.setLastCrawlTime(LocalDateTime.now());
                categoryMapper.updateById(parentLevel2);
                log.info("Worker-{} 父二级分类 [{}] 下所有三级分类已完成，同步更新状态为COMPLETED，总产品数: {}",
                    workerId, parentLevel2.getCategoryLevel2Name(), totalProducts);
            } else if (processingCount == 0 && completedCount < totalCount) {
                // 如果没有正在处理的，但也不是全部完成，可能是部分STOPPED/FAILED
                parentLevel2.setCrawlStatus("COMPLETED");
                parentLevel2.setCrawlProgress(100);
                parentLevel2.setCrawledProducts(totalProducts);
                parentLevel2.setLastCrawlTime(LocalDateTime.now());
                categoryMapper.updateById(parentLevel2);
                log.info("Worker-{} 父二级分类 [{}] 部分三级分类完成({}个)，标记为COMPLETED，总产品数: {}",
                    workerId, parentLevel2.getCategoryLevel2Name(), completedCount, totalProducts);
            }

        } catch (Exception e) {
            log.error("Worker-{} 更新父二级分类状态失败: parentLevel2Id={}", workerId, parentLevel2Id, e);
        }
    }
}
