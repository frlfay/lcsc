package com.lcsc.service.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.lcsc.service.crawler.network.DynamicRateLimiter;
import com.lcsc.service.crawler.error.SmartRetryHandler;
import com.lcsc.config.HttpDebugConfig;

/**
 * 立创商城API调用服务
 * 支持新旧API接口调用，推荐使用新API
 *
 * @author lcsc-crawler
 * @since 2025-09-03
 * @updated 2025-09-05
 */
@Service
public class LcscApiService {

    private static final Logger log = LoggerFactory.getLogger(LcscApiService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService executorService;
    private final DynamicRateLimiter rateLimiter;
    private final SmartRetryHandler retryHandler;
    private final HttpDebugConfig debugConfig;

    // 文件写入服务
    private final ProductResultFileWriter productResultFileWriter;

    @Autowired
    public LcscApiService(ObjectMapper objectMapper,
                         RestTemplate restTemplate,
                         DynamicRateLimiter rateLimiter,
                         SmartRetryHandler retryHandler,
                         HttpDebugConfig debugConfig,
                         ProductResultFileWriter productResultFileWriter) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.rateLimiter = rateLimiter;
        this.retryHandler = retryHandler;
        this.debugConfig = debugConfig;
        this.productResultFileWriter = productResultFileWriter;
        this.executorService = Executors.newScheduledThreadPool(5);
    }

    // ======= 新API方法（推荐使用） =======
    
    /**
     * 1. 获取所有一二级分类目录（新API）
     * POST /catalog/list
     */
    public CompletableFuture<Map<String, Object>> getCatalogList() {
        return retryHandler.executeWithSmartRetry(
            () -> {
                if (debugConfig.isPrintApiCallDetails()) {
                    log.info("=== CATALOG LIST API CALL DEBUG ===");
                    log.info("URL: {}", LcscApiEndpoints.Urls.CATALOG_LIST);
                    log.info("Method: POST");
                    log.info("Request Body: {}");
                    if (debugConfig.isPrintHeaders()) {
                        log.info("Headers: {}", createHeaders());
                    }
                    log.info("=== START API CALL ===");
                }

                HttpHeaders headers = createHeaders();
                HttpEntity<String> entity = new HttpEntity<>("{}", headers);

                ResponseEntity<String> response = restTemplate.exchange(
                    LcscApiEndpoints.Urls.CATALOG_LIST,
                    HttpMethod.POST,
                    entity,
                    String.class
                );

                if (debugConfig.isPrintApiCallDetails()) {
                    log.info("=== API RESPONSE RECEIVED ===");
                    log.info("Status Code: {}", response.getStatusCode());
                    if (debugConfig.isPrintHeaders()) {
                        log.info("Response Headers: {}", response.getHeaders());
                    }
                    log.info("=== END API CALL DEBUG ===");
                }

                return parseCatalogListResponse(response.getBody());
            },
            "获取分类目录",
            SmartRetryHandler.createApiContext()
        );
    }
    
    /**
     * 2. 获取筛选条件（新API）
     * POST /query/param/group
     */
    public CompletableFuture<Map<String, Object>> getQueryParamGroup(Map<String, Object> filterParams) {
        return retryHandler.executeWithSmartRetry(
            () -> {
                // 构建请求体，设置默认值
                Map<String, Object> requestBody = buildFilterParams(filterParams);

                log.info("=== QUERY PARAM GROUP API CALL DEBUG ===");
                log.info("URL: {}", LcscApiEndpoints.Urls.QUERY_PARAM_GROUP);
                log.info("Method: POST");
                log.info("Request Body: {}", requestBody);
                log.info("=== START API CALL ===");

                HttpHeaders headers = createHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<String> response = restTemplate.exchange(
                    LcscApiEndpoints.Urls.QUERY_PARAM_GROUP,
                    HttpMethod.POST,
                    entity,
                    String.class
                );

                log.info("=== API RESPONSE RECEIVED ===");
                log.info("Status Code: {}", response.getStatusCode());
                log.info("=== END API CALL DEBUG ===");

                return parseQueryParamGroupResponse(response.getBody());
            },
            "获取筛选条件",
            SmartRetryHandler.createApiContext()
        );
    }
    
    /**
     * 3. 获取产品列表（新API）
     * POST /query/list
     */
    public CompletableFuture<Map<String, Object>> getQueryList(Map<String, Object> filterParams) {
        return retryHandler.executeWithSmartRetry(
            () -> {
                // 构建请求体，设置默认值
                Map<String, Object> requestBody = buildFilterParams(filterParams);

                // 确保包含分页信息
                if (!requestBody.containsKey(LcscApiEndpoints.FilterParams.CURRENT_PAGE)) {
                    requestBody.put(LcscApiEndpoints.FilterParams.CURRENT_PAGE, LcscApiEndpoints.Config.DEFAULT_CURRENT_PAGE);
                }
                if (!requestBody.containsKey(LcscApiEndpoints.FilterParams.PAGE_SIZE)) {
                    requestBody.put(LcscApiEndpoints.FilterParams.PAGE_SIZE, LcscApiEndpoints.Config.DEFAULT_PAGE_SIZE);
                }

                log.info("=== QUERY LIST API CALL DEBUG ===");
                log.info("URL: {}", LcscApiEndpoints.Urls.QUERY_LIST);
                log.info("Method: POST");
                log.info("Request Body: {}", requestBody);
                log.info("=== START API CALL ===");

                HttpHeaders headers = createHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<String> response = restTemplate.exchange(
                    LcscApiEndpoints.Urls.QUERY_LIST,
                    HttpMethod.POST,
                    entity,
                    String.class
                );

                log.info("=== API RESPONSE RECEIVED ===");
                log.info("Status Code: {}", response.getStatusCode());
                String responseBody = response.getBody();
                log.info("Response Body Length: {}", responseBody != null ? responseBody.length() : 0);
                if (responseBody != null && responseBody.length() > 0) {
                    log.info("Response Body (first 1000 chars): {}",
                        responseBody.substring(0, Math.min(1000, responseBody.length())));
                    if (responseBody.length() > 1000) {
                        log.info("... (truncated, total length: {})", responseBody.length());
                    }
                }
                log.info("=== END API CALL DEBUG ===");

                return parseQueryListResponse(responseBody);
            },
            "获取产品列表",
            SmartRetryHandler.createApiContext()
        );
    }
    

    /**
     * 创建HTTP请求头
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", LcscApiEndpoints.Headers.USER_AGENT);
        headers.set("Referer", LcscApiEndpoints.Headers.REFERER);
        headers.set("Origin", LcscApiEndpoints.Headers.ORIGIN);
        headers.set("Accept", LcscApiEndpoints.Headers.ACCEPT);
        headers.set("Accept-Language", LcscApiEndpoints.Headers.ACCEPT_LANGUAGE);
        headers.set("Accept-Encoding", LcscApiEndpoints.Headers.ACCEPT_ENCODING);
        headers.set("Cache-Control", LcscApiEndpoints.Headers.CACHE_CONTROL);
        headers.set("Pragma", LcscApiEndpoints.Headers.PRAGMA);
        headers.set("X-Requested-With", LcscApiEndpoints.Headers.X_REQUESTED_WITH);
//        headers.set("Cookie", "wmsc_cart_key=242C1D8ECBDD83AA1308BE0FC006ABCDF5422AEAB7826FB031B716A4D7B351CAE765F64172DBD3B2; LCSC_LOCALE=en; currencyCode=CNY");
        headers.set("Cookie", "LCSC_LOCALE=en; currencyCode=CNY");

        // 添加现代浏览器安全头
        headers.set("sec-ch-ua", LcscApiEndpoints.Headers.SEC_CH_UA);
        headers.set("sec-ch-ua-mobile", LcscApiEndpoints.Headers.SEC_CH_UA_MOBILE);
        headers.set("sec-ch-ua-platform", LcscApiEndpoints.Headers.SEC_CH_UA_PLATFORM);
        headers.set("sec-fetch-dest", LcscApiEndpoints.Headers.SEC_FETCH_DEST);
        headers.set("sec-fetch-mode", LcscApiEndpoints.Headers.SEC_FETCH_MODE);
        headers.set("sec-fetch-site", LcscApiEndpoints.Headers.SEC_FETCH_SITE);
        headers.set("priority", LcscApiEndpoints.Headers.PRIORITY);

        // 注意：Content-Type 在具体方法中设置，避免重复设置
        return headers;
    }



    /**
     * 处理图片URL - 确保有协议前缀
     */
    private String processImageUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        if (!url.startsWith("http")) {
            return "https:" + url;
        }
        
        return url;
    }
    
    // ======= 新API辅助方法 =======
    
    /**
     * 构建筛选参数，设置默认值
     */
    private Map<String, Object> buildFilterParams(Map<String, Object> filterParams) {
        Map<String, Object> requestBody = new HashMap<>();
        
        // 设置默认值
        requestBody.put(LcscApiEndpoints.FilterParams.KEYWORD, 
            filterParams.getOrDefault(LcscApiEndpoints.FilterParams.KEYWORD, LcscApiEndpoints.FilterParams.DEFAULT_KEYWORD));
        requestBody.put(LcscApiEndpoints.FilterParams.IS_STOCK, 
            filterParams.getOrDefault(LcscApiEndpoints.FilterParams.IS_STOCK, LcscApiEndpoints.FilterParams.DEFAULT_IS_STOCK));
        requestBody.put(LcscApiEndpoints.FilterParams.IS_OTHER_SUPPLIERS, 
            filterParams.getOrDefault(LcscApiEndpoints.FilterParams.IS_OTHER_SUPPLIERS, LcscApiEndpoints.FilterParams.DEFAULT_IS_OTHER_SUPPLIERS));
        requestBody.put(LcscApiEndpoints.FilterParams.IS_ASIAN_BRAND, 
            filterParams.getOrDefault(LcscApiEndpoints.FilterParams.IS_ASIAN_BRAND, LcscApiEndpoints.FilterParams.DEFAULT_IS_ASIAN_BRAND));
        requestBody.put(LcscApiEndpoints.FilterParams.IS_DEALS, 
            filterParams.getOrDefault(LcscApiEndpoints.FilterParams.IS_DEALS, LcscApiEndpoints.FilterParams.DEFAULT_IS_DEALS));
        requestBody.put(LcscApiEndpoints.FilterParams.IS_ENVIRONMENT, 
            filterParams.getOrDefault(LcscApiEndpoints.FilterParams.IS_ENVIRONMENT, LcscApiEndpoints.FilterParams.DEFAULT_IS_ENVIRONMENT));
        
        // 设置列表类型参数，确保非空
        requestBody.put(LcscApiEndpoints.FilterParams.CATALOG_ID_LIST, 
            filterParams.getOrDefault(LcscApiEndpoints.FilterParams.CATALOG_ID_LIST, new ArrayList<>()));
        requestBody.put(LcscApiEndpoints.FilterParams.BRAND_ID_LIST, 
            filterParams.getOrDefault(LcscApiEndpoints.FilterParams.BRAND_ID_LIST, new ArrayList<>()));
        requestBody.put(LcscApiEndpoints.FilterParams.ENCAP_VALUE_LIST, 
            filterParams.getOrDefault(LcscApiEndpoints.FilterParams.ENCAP_VALUE_LIST, new ArrayList<>()));
        requestBody.put(LcscApiEndpoints.FilterParams.PRODUCT_ARRANGE_LIST, 
            filterParams.getOrDefault(LcscApiEndpoints.FilterParams.PRODUCT_ARRANGE_LIST, new ArrayList<>()));
        
        // 设置复杂参数
        requestBody.put(LcscApiEndpoints.FilterParams.PARAM_NAME_VALUE_MAP,
            filterParams.getOrDefault(LcscApiEndpoints.FilterParams.PARAM_NAME_VALUE_MAP, new HashMap<>()));

        // 价格区间参数（实验性）
        if (filterParams.containsKey(LcscApiEndpoints.FilterParams.PRICE_FROM)) {
            requestBody.put(LcscApiEndpoints.FilterParams.PRICE_FROM,
                filterParams.get(LcscApiEndpoints.FilterParams.PRICE_FROM));
        }
        if (filterParams.containsKey(LcscApiEndpoints.FilterParams.PRICE_TO)) {
            requestBody.put(LcscApiEndpoints.FilterParams.PRICE_TO,
                filterParams.get(LcscApiEndpoints.FilterParams.PRICE_TO));
        }

        // 复制其他参数
        for (Map.Entry<String, Object> entry : filterParams.entrySet()) {
            if (!requestBody.containsKey(entry.getKey())) {
                requestBody.put(entry.getKey(), entry.getValue());
            }
        }
        
        return requestBody;
    }
    
    /**
     * 解析新catalog/list接口响应数据
     */
    private Map<String, Object> parseCatalogListResponse(String response) {
        try {
            log.debug("=== DEBUG: 原始响应内容 ===");
            log.debug("Response length: " + (response != null ? response.length() : "null"));
            if (response != null && response.length() > 0) {
                log.debug("First 500 chars: " + response.substring(0, Math.min(500, response.length())));
            }
            log.debug("=== DEBUG: 开始解析JSON ===");
            
            JsonNode jsonNode = objectMapper.readTree(response);
            log.debug("JSON root keys: " + jsonNode.fieldNames());
            
            JsonNode result = jsonNode.get("result");
            log.debug("Result node: " + (result != null ? "exists" : "null"));
            if (result != null) {
                log.debug("Result keys: " + result.fieldNames());
            }
            
            Map<String, Object> catalogResult = new HashMap<>();
            
            // 处理分类列表
            JsonNode catalogList = result.get("catalogList");
            List<Map<String, Object>> catalogs = new ArrayList<>();
            
            if (catalogList != null && catalogList.isArray()) {
                for (JsonNode catalog : catalogList) {
                    log.debug("=== DEBUG: Processing catalog node: " + catalog.toString());
                    Map<String, Object> catalogMap = new HashMap<>();
                    catalogMap.put("catalogId", catalog.get("catalogId").asInt());
                    
                    JsonNode parentId = catalog.get("parentId");
                    catalogMap.put("parentId", parentId.isNull() ? null : parentId.asInt());
                    
                    JsonNode catalogName = catalog.get("catalogName");
                    catalogMap.put("catalogName", (catalogName != null && !catalogName.isNull()) ? catalogName.asText() : null);
                    catalogMap.put("catalogNameEn", catalog.get("catalogNameEn").asText());
                    catalogMap.put("productNum", catalog.get("productNum").asInt());
                    
                    // 处理子分类（递归解析支持三级分类）
                    JsonNode childCatelogs = catalog.get("childCatelogs");
                    if (childCatelogs != null && childCatelogs.isArray()) {
                        List<Map<String, Object>> children = parseChildCategories(childCatelogs);
                        catalogMap.put("childCatelogs", children);
                    }
                    
                    catalogs.add(catalogMap);
                }
            }
            catalogResult.put("catalogList", catalogs);
            
            // 处理品牌列表（新API特有）
            JsonNode brandList = result.get("brandList");
            List<Map<String, Object>> brands = new ArrayList<>();
            
            if (brandList != null && brandList.isArray()) {
                for (JsonNode brand : brandList) {
                    Map<String, Object> brandMap = new HashMap<>();
                    brandMap.put("brandId", brand.get("brandId").asText());
                    brandMap.put("brandName", brand.get("brandName").asText());
                    brandMap.put("productNum", brand.get("productNum").asInt());
                    brands.add(brandMap);
                }
            }
            catalogResult.put("brandList", brands);
            
            return catalogResult;
            
        } catch (Exception e) {
            throw new RuntimeException("解析分类目录响应失败", e);
        }
    }

    /**
     * 递归解析子分类（支持三级及更多层级）
     *
     * @param childCategories 子分类JsonNode数组
     * @return 解析后的子分类列表
     */
    private List<Map<String, Object>> parseChildCategories(JsonNode childCategories) {
        List<Map<String, Object>> children = new ArrayList<>();

        for (JsonNode child : childCategories) {
            Map<String, Object> childMap = new HashMap<>();
            childMap.put("catalogId", child.get("catalogId").asInt());

            JsonNode childParentId = child.get("parentId");
            childMap.put("parentId", childParentId.isNull() ? null : childParentId.asInt());

            JsonNode childCatalogName = child.get("catalogName");
            childMap.put("catalogName", (childCatalogName != null && !childCatalogName.isNull()) ? childCatalogName.asText() : null);
            childMap.put("catalogNameEn", child.get("catalogNameEn").asText());
            childMap.put("productNum", child.get("productNum").asInt());

            // 🔑 关键：递归处理更深层级的子分类（三级、四级等）
            JsonNode grandChildCatelogs = child.get("childCatelogs");
            if (grandChildCatelogs != null && grandChildCatelogs.isArray() && grandChildCatelogs.size() > 0) {
                log.info("发现三级分类: {} ({}) 下有 {} 个子分类",
                    childMap.get("catalogName"),
                    childMap.get("catalogId"),
                    grandChildCatelogs.size());
                // 递归调用自己，解析更深层级
                List<Map<String, Object>> grandChildren = parseChildCategories(grandChildCatelogs);
                childMap.put("childCatelogs", grandChildren);
            }

            children.add(childMap);
        }

        return children;
    }

    /**
     * 解析新query/param/group接口响应数据
     */
    private Map<String, Object> parseQueryParamGroupResponse(String response) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode result = jsonNode.get("result");
            
            Map<String, Object> paramResult = new HashMap<>();
            
            // 解析所有筛选组（Brand, Package, 各种参数等）
            Iterator<String> fieldNames = result.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode fieldValue = result.get(fieldName);
                
                if (fieldValue.isArray()) {
                    List<Map<String, Object>> itemList = new ArrayList<>();
                    for (JsonNode item : fieldValue) {
                        Map<String, Object> itemMap = new HashMap<>();
                        Iterator<String> itemFields = item.fieldNames();
                        while (itemFields.hasNext()) {
                            String itemField = itemFields.next();
                            JsonNode itemValue = item.get(itemField);
                            if (itemValue.isTextual()) {
                                itemMap.put(itemField, itemValue.asText());
                            } else if (itemValue.isNumber()) {
                                itemMap.put(itemField, itemValue.asInt());
                            } else {
                                itemMap.put(itemField, itemValue);
                            }
                        }
                        itemList.add(itemMap);
                    }
                    paramResult.put(fieldName, itemList);
                }
            }
            
            return paramResult;
            
        } catch (Exception e) {
            throw new RuntimeException("解析筛选参数响应失败", e);
        }
    }
    
    /**
     * 解析新query/list接口响应数据
     */
    private Map<String, Object> parseQueryListResponse(String response) {
        try {
            log.info("=== PARSING QUERY LIST RESPONSE ===");
            log.info("Response length: {}", response != null ? response.length() : "null");

            JsonNode jsonNode = objectMapper.readTree(response);
            log.info("JSON root keys: {}", jsonNode.fieldNames());

            JsonNode result = jsonNode.get("result");
            log.info("Result node exists: {}", result != null);
            if (result != null) {
                log.info("Result keys: {}", result.fieldNames());
            }

            Map<String, Object> queryResult = new HashMap<>();

            // 分页信息
            JsonNode currPage = result.get("currPage");
            JsonNode pageRow = result.get("pageRow");
            JsonNode totalPage = result.get("totalPage");
            JsonNode totalRow = result.get("totalRow");

            log.info("Pagination info - currPage: {}, pageRow: {}, totalPage: {}, totalRow: {}",
                currPage != null ? currPage.asInt() : "null",
                pageRow != null ? pageRow.asInt() : "null",
                totalPage != null ? totalPage.asInt() : "null",
                totalRow != null ? totalRow.asInt() : "null");

            queryResult.put("currentPage", currPage != null ? currPage.asInt() : 0);
            queryResult.put("pageSize", pageRow != null ? pageRow.asInt() : 0);
            queryResult.put("totalPages", totalPage != null ? totalPage.asInt() : 0);
            queryResult.put("totalRows", totalRow != null ? totalRow.asInt() : 0);
            
            // 产品数据列表
            JsonNode dataList = result.get("dataList");
            List<Map<String, Object>> products = new ArrayList<>();

            log.info("DataList node exists: {}, is array: {}", dataList != null, dataList != null && dataList.isArray());
            if (dataList != null && dataList.isArray()) {
                log.info("DataList size: {}", dataList.size());
                for (int i = 0; i < dataList.size(); i++) {
                    JsonNode product = dataList.get(i);
                    log.info("Processing product {} of {}", i + 1, dataList.size());
                    Map<String, Object> productMap = parseProductData(product);
                    products.add(productMap);
                }
            } else {
                log.warn("DataList is null or not an array");
            }

            log.info("Total products parsed: {}", products.size());
            queryResult.put("dataList", products);

            // 添加完整的原始响应用于调试和完整存储
            queryResult.put("rawResponse", response);
            queryResult.put("fullApiResult", result);

            // 尝试从 result 节点获取原始JSON字符串（如果 result 是对象节点）
            String rawJsonResponse = "";
            if (result != null && !result.isNull()) {
                try {
                    rawJsonResponse = objectMapper.writeValueAsString(result);
                    log.info("API响应的完整JSON数据长度: {}", rawJsonResponse != null ? rawJsonResponse.length() : 0);
                } catch (Exception e) {
                    log.error("将API响应转换为JSON字符串失败: {}", e.getMessage(), e);
                    rawJsonResponse = response; // 使用原始响应作为备选
                }
            }

            // 将每个产品信息单独保存到product-data.jsonl
            JsonNode productDataList = result.get("dataList");
            if (productDataList != null && productDataList.isArray()) {
                log.info("开始保存 {} 个产品到 product-data.jsonl", productDataList.size());

                for (int i = 0; i < productDataList.size(); i++) {
                    JsonNode product = productDataList.get(i);
                    try {
                        // 为每个产品创建包含category信息的完整数据
                        com.fasterxml.jackson.databind.node.ObjectNode productData = objectMapper.createObjectNode();

                        // 复制所有产品字段
                        Iterator<String> fieldNames = product.fieldNames();
                        while (fieldNames.hasNext()) {
                            String fieldName = fieldNames.next();
                            productData.put(fieldName, product.get(fieldName));
                        }

                        // 添加分类信息
                        Integer cat1Id = extractCatalogIdFromResult(result);
                        Integer cat2Id = null; // 这里可以根据需要从请求参数中提取
                        Integer currentPage = currPage != null ? currPage.asInt() : null;

                        if (cat1Id != null) {
                            productData.put("categoryLevel1Id", cat1Id);
                        }
                        if (cat2Id != null) {
                            productData.put("categoryLevel2Id", cat2Id);
                        }
                        if (currentPage != null) {
                            productData.put("currentPage", currentPage);
                        }
                        productData.put("fetchedAt", java.time.OffsetDateTime.now().toString());

                        // 数据将直接存储到数据库中
                        log.debug("产品 {} 数据已准备处理 (数据将直接存储到数据库)",
                            product.get("productCode") != null ? product.get("productCode").asText() : "unknown");

                    } catch (Exception e) {
                        log.error("处理产品 {} 数据失败: {}",
                            product.get("productCode") != null ? product.get("productCode").asText() : "unknown",
                            e.getMessage(), e);
                    }
                }

                log.info("完成 {} 个产品保存到 product-data.jsonl", productDataList.size());
            } else {
                log.warn("dataList 为空或不是数组格式，无法保存产品信息");
            }

            // 保存完整的API结果到独立文件
            try {
                Integer cat1Id = extractCatalogIdFromResult(result);
                Integer cat2Id = null; // 这里可以根据需要从请求参数中提取
                Integer currentPage = currPage != null ? currPage.asInt() : null;

                Path savedFile = productResultFileWriter.saveResultToFile(
                    result, cat1Id, cat2Id, currentPage);

                if (savedFile != null) {
                    log.info("完整API结果已保存到独立文件: {}", savedFile);
                }
            } catch (Exception e) {
                log.error("保存完整API结果到文件失败: {}", e.getMessage(), e);
            }

            log.info("=== FINISHED PARSING QUERY LIST RESPONSE ===");
            return queryResult;
            
        } catch (Exception e) {
            throw new RuntimeException("解析产品查询响应失败", e);
        }
    }
    
    /**
     * 解析单个产品数据（提取所有可用字段）
     */
    private Map<String, Object> parseProductData(JsonNode product) {
        Map<String, Object> productMap = new HashMap<>();

        // === 基本信息 ===
        productMap.put("productId", product.get("productId").asInt());
        productMap.put("productCode", product.get("productCode").asText());
        productMap.put("productModel", product.get("productModel").asText());
        productMap.put("brandId", product.get("brandId").asInt());
        productMap.put("brandNameEn", product.get("brandNameEn").asText());
        productMap.put("catalogId", product.get("catalogId").asInt());
        productMap.put("encapStandard", product.get("encapStandard").asText());
        productMap.put("productIntroEn", product.get("productIntroEn").asText());
        productMap.put("stockNumber", product.get("stockNumber").asInt());

        // === 重量信息 ===
        productMap.put("productWeight", getDoubleValue(product, "productWeight", 0.0));
        productMap.put("foreignWeight", getDoubleValue(product, "foreignWeight", 0.0));
        productMap.put("weight", getIntegerValue(product, "weight", 0));

        // === 销售相关信息 ===
        productMap.put("dollarLadderPrice", getDoubleValue(product, "dollarLadderPrice", 0.0));
        productMap.put("isForeignOnsale", getBooleanValue(product, "isForeignOnsale", false));
        productMap.put("minBuyNumber", getIntegerValue(product, "minBuyNumber", 0));
        productMap.put("maxBuyNumber", getIntegerValue(product, "maxBuyNumber", 0));
        productMap.put("isNotOverstock", getBooleanValue(product, "isNotOverstock", false));
        productMap.put("productCycle", getStringValue(product, "productCycle", ""));
        productMap.put("minPacketUnit", getStringValue(product, "minPacketUnit", ""));
        productMap.put("productUnit", getStringValue(product, "productUnit", ""));
        productMap.put("productArrange", getStringValue(product, "productArrange", ""));
        productMap.put("minPacketNumber", getIntegerValue(product, "minPacketNumber", 0));

        // === 分类层级信息 ===
        productMap.put("parentCatalogId", getIntegerValue(product, "parentCatalogId", 0));
        productMap.put("parentCatalogName", getStringValue(product, "parentCatalogName", ""));
        productMap.put("catalogName", getStringValue(product, "catalogName", ""));
        productMap.put("productDescEn", getStringValue(product, "productDescEn", ""));

        // === 产品状态标志 ===
        productMap.put("isHasBattery", getBooleanValue(product, "isHasBattery", false));
        productMap.put("isForbid", getBooleanValue(product, "isForbid", false));
        productMap.put("isDiscount", getBooleanValue(product, "isDiscount", false));
        productMap.put("isHot", getBooleanValue(product, "isHot", false));
        productMap.put("isEnvironment", getBooleanValue(product, "isEnvironment", false));
        productMap.put("isPreSale", getBooleanValue(product, "isPreSale", false));

        // === 价格相关 ===
        productMap.put("productLadderPrice", getDoubleValue(product, "productLadderPrice", 0.0));
        productMap.put("ladderDiscountRate", getDoubleValue(product, "ladderDiscountRate", 0.0));

        // 价格列表信息
        JsonNode priceList = product.get("productPriceList");
        if (priceList != null && priceList.isArray()) {
            List<Map<String, Object>> prices = new ArrayList<>();
            for (JsonNode price : priceList) {
                Map<String, Object> priceMap = new HashMap<>();
                priceMap.put("ladder", price.get("ladder").asInt());
                priceMap.put("productPrice", price.get("productPrice").asText());
                // 统一提供 currencyPrice（当前通过 Cookie 使用 CNY），及其符号便于前端展示
                JsonNode currencyPriceNode = price.get("currencyPrice");
                if (currencyPriceNode != null && !currencyPriceNode.isNull()) {
                    if (currencyPriceNode.isNumber()) {
                        priceMap.put("currencyPrice", currencyPriceNode.asDouble());
                    } else {
                        priceMap.put("currencyPrice", currencyPriceNode.asText());
                    }
                }
                JsonNode currencySymbolNode = price.get("currencySymbol");
                if (currencySymbolNode != null && !currencySymbolNode.isNull()) {
                    priceMap.put("currencySymbol", currencySymbolNode.asText());
                }
                // 保留USD价格，作为备用
                JsonNode usdPriceNode = price.get("usdPrice");
                if (usdPriceNode != null && !usdPriceNode.isNull()) {
                    priceMap.put("usdPrice", usdPriceNode.asDouble());
                }
                prices.add(priceMap);
            }
            productMap.put("productPriceList", prices);
        }

        // === 图片信息 ===
        productMap.put("productImageUrl", processImageUrl(product.get("productImageUrl").asText()));
        JsonNode images = product.get("productImages");
        if (images != null && images.isArray()) {
            List<String> imageUrls = new ArrayList<>();
            for (JsonNode image : images) {
                imageUrls.add(processImageUrl(image.asText()));
            }
            productMap.put("productImages", imageUrls);
        }

        // === PDF链接 ===
        JsonNode pdfUrl = product.get("pdfUrl");
        if (pdfUrl != null && !pdfUrl.isNull()) {
            productMap.put("pdfUrl", pdfUrl.asText());
        }

        // === 参数信息 ===
        JsonNode paramList = product.get("paramVOList");
        if (paramList != null && paramList.isArray()) {
            List<Map<String, Object>> params = new ArrayList<>();
            for (JsonNode param : paramList) {
                Map<String, Object> paramMap = new HashMap<>();
                paramMap.put("paramCode", param.get("paramCode").asText());
                paramMap.put("paramName", param.get("paramName").asText());
                paramMap.put("paramNameEn", param.get("paramNameEn").asText());
                paramMap.put("paramValue", param.get("paramValue").asText());
                paramMap.put("paramValueEn", param.get("paramValueEn").asText());
                params.add(paramMap);
            }
            productMap.put("paramVOList", params);
        }

        return productMap;
    }

    // ==================== 辅助方法：安全获取值 ====================

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

    // 辅助方法：安全获取Double值
    private Double getDoubleValue(Map<String, Object> map, String key, Double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                log.debug("无法解析Double数值: {} = {}", key, value);
            }
        }
        return defaultValue;
    }

    // 辅助方法：安全获取Boolean值
    private Boolean getBooleanValue(Map<String, Object> map, String key, Boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            try {
                return Boolean.parseBoolean((String) value);
            } catch (Exception e) {
                log.debug("无法解析Boolean数值: {} = {}", key, value);
            }
        }
        return defaultValue;
    }

    // 从JsonNode安全获取值的方法
    private Integer getIntegerValue(JsonNode node, String key, Integer defaultValue) {
        JsonNode valueNode = node.get(key);
        if (valueNode == null || valueNode.isNull()) {
            return defaultValue;
        }
        if (valueNode.isNumber()) {
            return valueNode.asInt();
        }
        if (valueNode.isTextual()) {
            try {
                return Integer.parseInt(valueNode.asText());
            } catch (NumberFormatException e) {
                log.debug("无法解析整数值: {} = {}", key, valueNode.asText());
            }
        }
        return defaultValue;
    }

    private Double getDoubleValue(JsonNode node, String key, Double defaultValue) {
        JsonNode valueNode = node.get(key);
        if (valueNode == null || valueNode.isNull()) {
            return defaultValue;
        }
        if (valueNode.isNumber()) {
            return valueNode.asDouble();
        }
        if (valueNode.isTextual()) {
            try {
                return Double.parseDouble(valueNode.asText());
            } catch (NumberFormatException e) {
                log.debug("无法解析Double数值: {} = {}", key, valueNode.asText());
            }
        }
        return defaultValue;
    }

    private Boolean getBooleanValue(JsonNode node, String key, Boolean defaultValue) {
        JsonNode valueNode = node.get(key);
        if (valueNode == null || valueNode.isNull()) {
            return defaultValue;
        }
        if (valueNode.isBoolean()) {
            return valueNode.asBoolean();
        }
        if (valueNode.isTextual()) {
            try {
                return Boolean.parseBoolean(valueNode.asText());
            } catch (Exception e) {
                log.debug("无法解析Boolean数值: {} = {}", key, valueNode.asText());
            }
        }
        return defaultValue;
    }

    private String getStringValue(JsonNode node, String key, String defaultValue) {
        JsonNode valueNode = node.get(key);
        if (valueNode == null || valueNode.isNull()) {
            return defaultValue;
        }
        return valueNode.asText();
    }

    /**
     * 从API结果中提取分类ID
     */
    private Integer extractCatalogIdFromResult(JsonNode result) {
        try {
            // 尝试从dataList中第一个产品获取catalogId
            JsonNode dataList = result.get("dataList");
            if (dataList != null && dataList.isArray() && dataList.size() > 0) {
                JsonNode firstProduct = dataList.get(0);
                JsonNode catalogId = firstProduct.get("catalogId");
                if (catalogId != null && !catalogId.isNull()) {
                    return catalogId.asInt();
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("从结果中提取catalogId失败: {}", e.getMessage());
            return null;
        }
    }
}
