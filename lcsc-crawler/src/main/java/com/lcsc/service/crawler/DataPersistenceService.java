package com.lcsc.service.crawler;

import com.lcsc.entity.*;
import com.lcsc.service.*;
import com.lcsc.service.crawler.parser.CatalogParser;
import com.lcsc.service.crawler.FileDownloadService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 数据持久化服务
 * 负责将爬取的数据按正确顺序持久化到数据库
 * 
 * @author lcsc-crawler
 * @since 2025-09-08
 */
@Service
public class DataPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(DataPersistenceService.class);

    @Autowired
    private CategoryLevel1CodeService categoryLevel1Service;

    @Autowired
    private CategoryLevel2CodeService categoryLevel2Service;

    @Autowired
    private ProductService productService;

    @Autowired
    private ImageLinkService imageLinkService;

    @Autowired
    private CatalogParser catalogParser;

    @Autowired
    private FileDownloadService fileDownloadService;

    // 分类ID缓存，避免重复查询
    private final Map<String, Integer> categoryLevel1Cache = new ConcurrentHashMap<>();
    private final Map<String, Integer> categoryLevel2Cache = new ConcurrentHashMap<>();

    // 图片存储路径配置
    private static final String IMAGE_STORAGE_PATH = "/app/data/images/products";

    /**
     * 步骤1：持久化分类数据到数据库
     * 这应该在创建Redis任务之前执行
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> persistCatalogData(Map<String, Object> catalogApiResponse) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            logger.info("开始持久化分类数据到数据库...");
            
            // 解析分类数据
            List<Map<String, Object>> catalogMapping = catalogParser.parseCatalogMapping(catalogApiResponse);
            
            int level1Count = 0;
            int level2Count = 0;
            List<Integer> newCatalogIds = new ArrayList<>();
            
            for (Map<String, Object> catalogInfo : catalogMapping) {
                // 处理一级分类
                Integer level1Id = persistLevel1Category(catalogInfo);
                if (level1Id != null) {
                    level1Count++;
                    newCatalogIds.add(level1Id);
                    
                    // 处理二级分类
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> valueList = (List<Map<String, Object>>) catalogInfo.get("valueList");
                    if (valueList != null) {
                        for (Map<String, Object> subCategory : valueList) {
                            Integer level2Id = persistLevel2Category(subCategory, level1Id);
                            if (level2Id != null) {
                                level2Count++;
                            }
                        }
                    }
                }
            }
            
            result.put("success", true);
            result.put("level1CategoriesCreated", level1Count);
            result.put("level2CategoriesCreated", level2Count);
            result.put("newCatalogIds", newCatalogIds);
            result.put("timestamp", LocalDateTime.now());
            
            logger.info("分类数据持久化完成: 一级分类={}, 二级分类={}", level1Count, level2Count);
            
        } catch (Exception e) {
            logger.error("持久化分类数据失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * 持久化一级分类
     */
    private Integer persistLevel1Category(Map<String, Object> catalogInfo) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> nameInfo = (Map<String, String>) catalogInfo.get("name");
            String categoryName = nameInfo.get("cn");
            Integer catalogId = (Integer) catalogInfo.get("id");
            
            if (categoryName == null || catalogId == null) {
                return null;
            }
            
            // 检查是否已存在
            CategoryLevel1Code existingCategory = categoryLevel1Service.lambdaQuery()
                .eq(CategoryLevel1Code::getCategoryLevel1Name, categoryName)
                .one();
            
            if (existingCategory == null) {
                // 创建新的一级分类
                CategoryLevel1Code newCategory = new CategoryLevel1Code();
                newCategory.setCategoryLevel1Name(categoryName);
                newCategory.setCategoryCode(String.valueOf(catalogId));
                
                boolean saved = categoryLevel1Service.save(newCategory);
                if (saved) {
                    categoryLevel1Cache.put(categoryName, newCategory.getId());
                    logger.debug("创建一级分类: {} (ID: {})", categoryName, newCategory.getId());
                    return newCategory.getId();
                }
            } else {
                categoryLevel1Cache.put(categoryName, existingCategory.getId());
                return existingCategory.getId();
            }
            
        } catch (Exception e) {
            logger.error("持久化一级分类失败", e);
        }
        
        return null;
    }

    /**
     * 持久化二级分类
     */
    private Integer persistLevel2Category(Map<String, Object> subCategoryInfo, Integer parentId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> nameInfo = (Map<String, String>) subCategoryInfo.get("name");
            String categoryName = nameInfo.get("cn");
            
            if (categoryName == null || parentId == null) {
                return null;
            }
            
            // 检查是否已存在
            CategoryLevel2Code existingCategory = categoryLevel2Service.lambdaQuery()
                .eq(CategoryLevel2Code::getCategoryLevel2Name, categoryName)
                .eq(CategoryLevel2Code::getCategoryLevel1Id, parentId)
                .one();
            
            if (existingCategory == null) {
                // 创建新的二级分类
                CategoryLevel2Code newCategory = new CategoryLevel2Code();
                newCategory.setCategoryLevel2Name(categoryName);
                newCategory.setCategoryLevel1Id(parentId);
                
                boolean saved = categoryLevel2Service.save(newCategory);
                if (saved) {
                    String cacheKey = parentId + ":" + categoryName;
                    categoryLevel2Cache.put(cacheKey, newCategory.getId());
                    logger.debug("创建二级分类: {} (父ID: {}, ID: {})", categoryName, parentId, newCategory.getId());
                    return newCategory.getId();
                }
            } else {
                String cacheKey = parentId + ":" + categoryName;
                categoryLevel2Cache.put(cacheKey, existingCategory.getId());
                return existingCategory.getId();
            }
            
        } catch (Exception e) {
            logger.error("持久化二级分类失败", e);
        }
        
        return null;
    }

    /**
     * 步骤2：持久化产品数据（任务完成后调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> persistProductData(List<Map<String, Object>> productDataList) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            logger.info("开始持久化产品数据，数量: {}", productDataList.size());
            
            List<Product> validProducts = new ArrayList<>();
            int invalidCount = 0;
            
            for (Map<String, Object> productData : productDataList) {
                Product product = convertToProduct(productData);
                if (product != null) {
                    validProducts.add(product);
                } else {
                    invalidCount++;
                }
            }
            
            // 批量保存产品
            int savedCount = 0;
            for (Product product : validProducts) {
                try {
                    boolean saved = productService.saveOrUpdateProduct(product);
                    if (saved) {
                        savedCount++;
                        
                        // 异步下载和处理图片
                        processProductImages(product);
                    }
                } catch (Exception e) {
                    logger.error("保存产品失败: {}", product.getProductCode(), e);
                }
            }
            
            result.put("success", true);
            result.put("totalReceived", productDataList.size());
            result.put("validProducts", validProducts.size());
            result.put("savedProducts", savedCount);
            result.put("invalidProducts", invalidCount);
            result.put("timestamp", LocalDateTime.now());
            
            logger.info("产品数据持久化完成: 总数={}, 有效={}, 已保存={}, 无效={}", 
                productDataList.size(), validProducts.size(), savedCount, invalidCount);
            
        } catch (Exception e) {
            logger.error("持久化产品数据失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * 转换API数据为Product实体
     */
    private Product convertToProduct(Map<String, Object> productData) {
        try {
            Product product = new Product();
            
            // 基础信息
            product.setProductCode(getStringValue(productData, "productCode"));
            product.setBrand(getStringValue(productData, "brand"));
            product.setModel(getStringValue(productData, "model"));
            product.setPackageName(getStringValue(productData, "packageName"));
            product.setBriefDescription(getStringValue(productData, "briefDescription"));
            
            // 库存信息
            Integer stock = getIntegerValue(productData, "totalStockQuantity");
            product.setTotalStockQuantity(stock != null ? stock : 0);
            
            // 分类信息 - 这里需要根据分类名称查找ID
            Integer level1Id = getCategoryLevel1Id(getStringValue(productData, "categoryLevel1Name"));
            Integer level2Id = getCategoryLevel2Id(getStringValue(productData, "categoryLevel2Name"), level1Id);
            
            if (level1Id == null || level2Id == null) {
                logger.warn("产品分类ID无效: {}, L1={}, L2={}", 
                    product.getProductCode(), level1Id, level2Id);
                return null; // 没有有效分类ID的产品不保存
            }
            
            product.setCategoryLevel1Id(level1Id);
            product.setCategoryLevel2Id(level2Id);
            
            // 价格信息
            Object tierPrices = productData.get("tierPrices");
            if (tierPrices != null) {
                // 这里应该将价格数据转换为JSON格式
                // 具体实现取决于API返回的价格数据格式
            }
            
            // 图片信息（暂时只存储URL，实际文件下载在后面处理）
            product.setImageName(getStringValue(productData, "imageName"));
            
            // 时间信息
            product.setLastCrawledAt(LocalDateTime.now());
            
            return product;
            
        } catch (Exception e) {
            logger.error("转换产品数据失败", e);
            return null;
        }
    }

    /**
     * 步骤3：处理产品图片下载和存储（支持多图下载）
     */
    private void processProductImages(Product product) {
        try {
            // 优先处理多图下载
            if (product.getProductImagesInfo() != null && !product.getProductImagesInfo().isEmpty()) {
                processMultipleProductImages(product);
            } else {
                // 降级处理单图下载
                processSingleProductImage(product);
            }
        } catch (Exception e) {
            logger.error("处理产品图片失败: {}", product.getProductCode(), e);
        }
    }

    /**
     * 处理多张产品图片下载
     */
    private void processMultipleProductImages(Product product) {
        try {
            List<Map<String, Object>> imagesInfo = parseImagesInfo(product.getProductImagesInfo());
            if (imagesInfo.isEmpty()) {
                return;
            }

            // 获取存储基础路径
            String storageBasePath = System.getProperty("user.dir") + "/data";
            String productImageDir = storageBasePath + "/" + product.getProductCode() + "/images";

            for (Map<String, Object> imageInfo : imagesInfo) {
                String imageUrl = (String) imageInfo.get("url");
                String filename = (String) imageInfo.get("filename");
                String localPath = productImageDir + "/" + filename;

                // 提交下载任务到队列
                fileDownloadService.submitDownloadTask(imageUrl, localPath, "image");

                logger.debug("提交多图下载任务: {} -> {}", filename, imageUrl);
            }

            // 设置第一张图片为主图
            if (!imagesInfo.isEmpty()) {
                Map<String, Object> firstImage = imagesInfo.get(0);
                product.setImageLocalPath(productImageDir + "/" + firstImage.get("filename"));
                product.setMainImageLocalPath(productImageDir + "/" + firstImage.get("filename"));
                productService.updateById(product);
            }

        } catch (Exception e) {
            logger.error("处理多图下载失败: {}", product.getProductCode(), e);
        }
    }

    /**
     * 处理单张产品图片下载（原有逻辑）
     */
    private void processSingleProductImage(Product product) {
        String imageUrl = getImageUrlForProduct(product);
        if (imageUrl != null) {
            String storageBasePath = System.getProperty("user.dir") + "/data";
            String localPath = storageBasePath + "/" + product.getProductCode() + "/images/" + product.getImageName();

            fileDownloadService.submitDownloadTask(imageUrl, localPath, "image");

            logger.debug("提交单图下载任务: {} -> {}", product.getProductCode(), imageUrl);
        }
    }

    /**
     * 解析图片信息JSON
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseImagesInfo(String imagesInfoJson) {
        try {
            if (imagesInfoJson == null || imagesInfoJson.isEmpty()) {
                return new ArrayList<>();
            }

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(imagesInfoJson, List.class);

        } catch (Exception e) {
            logger.error("解析图片信息JSON失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 下载产品图片到本地
     */
    private String downloadProductImage(String productCode, String imageUrl) {
        try {
            // 创建存储目录
            Path storageDir = Paths.get(IMAGE_STORAGE_PATH);
            Files.createDirectories(storageDir);
            
            // 生成文件名
            String fileName = productCode + "_" + System.currentTimeMillis() + ".jpg";
            Path targetPath = storageDir.resolve(fileName);
            
            // 下载文件
            URL url = new URL(imageUrl);
            Files.copy(url.openStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            return targetPath.toString();
            
        } catch (IOException e) {
            logger.error("下载图片失败: {} -> {}", productCode, imageUrl, e);
            return null;
        }
    }

    // 工具方法
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getIntegerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer getCategoryLevel1Id(String categoryName) {
        if (categoryName == null) return null;
        
        Integer cachedId = categoryLevel1Cache.get(categoryName);
        if (cachedId != null) {
            return cachedId;
        }
        
        CategoryLevel1Code category = categoryLevel1Service.lambdaQuery()
            .eq(CategoryLevel1Code::getCategoryLevel1Name, categoryName)
            .one();
        
        if (category != null) {
            categoryLevel1Cache.put(categoryName, category.getId());
            return category.getId();
        }
        
        return null;
    }

    private Integer getCategoryLevel2Id(String categoryName, Integer parentId) {
        if (categoryName == null || parentId == null) return null;
        
        String cacheKey = parentId + ":" + categoryName;
        Integer cachedId = categoryLevel2Cache.get(cacheKey);
        if (cachedId != null) {
            return cachedId;
        }
        
        CategoryLevel2Code category = categoryLevel2Service.lambdaQuery()
            .eq(CategoryLevel2Code::getCategoryLevel2Name, categoryName)
            .eq(CategoryLevel2Code::getCategoryLevel1Id, parentId)
            .one();
        
        if (category != null) {
            categoryLevel2Cache.put(cacheKey, category.getId());
            return category.getId();
        }
        
        return null;
    }

    private String getImageUrlForProduct(Product product) {
        // 这里应该根据产品信息构造图片URL
        // 具体实现取决于立创商城的图片URL规则
        if (product.getImageName() != null) {
            return "https://image.lcsc.com/photos/" + product.getImageName();
        }
        return null;
    }
}