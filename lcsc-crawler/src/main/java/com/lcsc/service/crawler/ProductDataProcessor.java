package com.lcsc.service.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcsc.dto.DownloadTask;
import com.lcsc.entity.Product;
import com.lcsc.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 产品数据处理器
 * 负责将API响应数据转换为数据库实体并保存
 *
 * @author lcsc-crawler
 * @since 2025-09-03
 */
@Service
public class ProductDataProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductDataProcessor.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DownloadQueueService downloadQueueService;

    
    @Value("${crawler.save-images:true}")
    private boolean saveImages;

    /**
     * 同步处理产品列表数据.
     * 将API响应的产品数据转换为Product实体并保存到数据库.
     * 如果启用了图片保存，则创建下载任务并推送到Redis队列.
     *
     * @return 处理成功的产品数量.
     */
    public int processProductList(List<Map<String, Object>> productDataList,
                                  Integer categoryLevel1Id,
                                  Integer categoryLevel2Id) {
        int processedCount = 0;
        for (Map<String, Object> productData : productDataList) {
            try {
                Product product = convertToProductEntity(productData, categoryLevel1Id, categoryLevel2Id);

                Product existingProduct = productService.getByProductCode(product.getProductCode());

                if (existingProduct != null) {
                    updateExistingProduct(existingProduct, product);
                    productService.updateById(existingProduct);
                } else {
                    productService.save(product);
                }

                if (saveImages) {
                    enqueueDownloadTasks(productData, product);
                }

                processedCount++;

            } catch (Exception e) {
                logger.error("处理产品数据失败: {}", productData.get("productCode"), e);
            }
        }
        return processedCount;
    }

    /**
     * 将API数据转换为Product实体
     */
    private Product convertToProductEntity(Map<String, Object> productData, 
                                         Integer categoryLevel1Id, 
                                         Integer categoryLevel2Id) {
        Product product = new Product();
        
        // 基本信息
        product.setProductCode((String) productData.get("productCode"));
        product.setModel((String) productData.get("productModel"));
        product.setBrand((String) productData.get("brandNameEn"));
        product.setPackageName((String) productData.get("encapStandard"));
        
        // 分类信息
        product.setCategoryLevel1Id(categoryLevel1Id);
        product.setCategoryLevel2Id(categoryLevel2Id);

        // 新增：分类名称字段（对应CSV中的分类名称）
        product.setCategoryLevel1Name((String) productData.get("parentCatalogName"));
        product.setCategoryLevel2Name((String) productData.get("catalogName"));

        // 新增：图片和PDF URL字段
        product.setProductImageUrlBig((String) productData.get("productImageUrlBig"));
        product.setPdfUrl((String) productData.get("pdfUrl"));

        // 库存信息
        Object stockNumber = productData.get("stockNumber");
        if (stockNumber != null) {
            product.setTotalStockQuantity(((Number) stockNumber).intValue());
        }
        
        // 简介信息
        String productIntro = (String) productData.get("productIntroEn");
        if (productIntro != null && productIntro.length() > 60) {
            productIntro = productIntro.substring(0, 60);
        }
        product.setBriefDescription(productIntro);
        
        // 处理价格信息
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> priceList = (List<Map<String, Object>>) productData.get("productPriceList");
        if (priceList != null && !priceList.isEmpty()) {
            try {
                String tierPricesJson = objectMapper.writeValueAsString(priceList);
                product.setTierPrices(tierPricesJson);
                product.setTierPricesLastUpdate(LocalDate.now());
                product.setTierPricesManualEdit(false);

                // 新增：将阶梯价格分别存储到对应的字段（对应CSV中的阶梯价字段）
                processLadderPrices(product, priceList);
            } catch (Exception e) {
                logger.error("处理价格信息失败 for product {}", product.getProductCode(), e);
            }
        }
        
        // 处理参数信息
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> paramList = (List<Map<String, Object>>) productData.get("paramVOList");
        if (paramList != null && !paramList.isEmpty()) {
            try {
                String detailedParamsJson = objectMapper.writeValueAsString(paramList);
                product.setDetailedParameters(detailedParamsJson);

                // 新增：将参数转换为文本格式（对应CSV中的"产品参数"字段）
                String parametersText = paramList.stream()
                    .map(param -> param.get("paramName") + ": " + param.get("paramValueEn"))
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
                product.setParametersText(parametersText);
            } catch (Exception e) {
                logger.error("处理参数信息失败 for product {}", product.getProductCode(), e);
            }
        }
        
        // 仅在数据库中记录文件名，实际下载由worker完成
        String imageUrl = (String) productData.get("productImageUrl");
        if (imageUrl != null) {
            product.setImageName(product.getProductCode()); // 记录产品编号作为图片文件夹标识
        }
        
        String pdfUrl = (String) productData.get("pdfUrl");
        if (pdfUrl != null) {
            String pdfFilename = generatePdfFilename(product.getProductCode(), product.getBrand(), product.getModel());
            product.setPdfFilename(pdfFilename);
        }
        
        // 时间戳
        LocalDateTime now = LocalDateTime.now();
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        product.setLastCrawledAt(now);
        
        return product;
    }

    /**
     * 更新现有产品信息
     */
    private void updateExistingProduct(Product existingProduct, Product newProduct) {
        // 更新库存信息
        existingProduct.setTotalStockQuantity(newProduct.getTotalStockQuantity());

        // 更新价格信息（如果价格更新间隔超过180天或者没有人工编辑过）
        if (shouldUpdatePrices(existingProduct)) {
            existingProduct.setTierPrices(newProduct.getTierPrices());
            existingProduct.setTierPricesLastUpdate(LocalDate.now());

            // 更新阶梯价格字段
            updateLadderPrices(existingProduct, newProduct);
        }

        // 更新其他可变信息
        existingProduct.setBriefDescription(newProduct.getBriefDescription());
        existingProduct.setDetailedParameters(newProduct.getDetailedParameters());

        // 新增：更新分类名称字段
        existingProduct.setCategoryLevel1Name(newProduct.getCategoryLevel1Name());
        existingProduct.setCategoryLevel2Name(newProduct.getCategoryLevel2Name());

        // 新增：更新图片和PDF URL字段
        existingProduct.setProductImageUrlBig(newProduct.getProductImageUrlBig());
        existingProduct.setPdfUrl(newProduct.getPdfUrl());

        // 新增：更新产品参数文本字段
        existingProduct.setParametersText(newProduct.getParametersText());

        // 更新时间戳
        existingProduct.setUpdatedAt(LocalDateTime.now());
        existingProduct.setLastCrawledAt(LocalDateTime.now());
    }

    /**
     * 判断是否应该更新价格信息
     */
    private boolean shouldUpdatePrices(Product product) {
        // 如果人工编辑过，不自动更新
        if (Boolean.TRUE.equals(product.getTierPricesManualEdit())) {
            return false;
        }
        
        // 如果没有价格更新日期，则需要更新
        if (product.getTierPricesLastUpdate() == null) {
            return true;
        }
        
        // 如果超过180天，则需要更新
        LocalDate now = LocalDate.now();
        LocalDate lastUpdate = product.getTierPricesLastUpdate();
        return now.minusDays(180).isAfter(lastUpdate);
    }

    /**
     * 从URL提取图片文件名
     */
    private String extractImageNameFromUrl(String imageUrl, String productCode) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }
        
        try {
            // 提取文件扩展名
            String extension = ".jpg";
            if (imageUrl.contains(".png")) {
                extension = ".png";
            } else if (imageUrl.contains(".gif")) {
                extension = ".gif";
            }
            
            return productCode + extension;
            
        } catch (Exception e) {
            return productCode + ".jpg"; // 默认扩展名
        }
    }

    /**
     * 生成PDF文件名
     */
    private String generatePdfFilename(String productCode, String brand, String model) {
        try {
            // 清理文件名中的特殊字符
            String cleanBrand = brand != null ? brand.replaceAll("[^a-zA-Z0-9]", "") : "";
            String cleanModel = model != null ? model.replaceAll("[^a-zA-Z0-9-]", "") : "";
            
            if (!cleanBrand.isEmpty() && !cleanModel.isEmpty()) {
                return productCode + "_" + cleanBrand + "_" + cleanModel + ".pdf";
            } else if (!cleanBrand.isEmpty()) {
                return productCode + "_" + cleanBrand + ".pdf";
            } else {
                return productCode + ".pdf";
            }
            
        } catch (Exception e) {
            return productCode + ".pdf";
        }
    }

    /**
     * 验证产品数据完整性
     */
    public boolean validateProductData(Map<String, Object> productData) {
        // 检查必需字段
        String productCode = (String) productData.get("productCode");
        String productModel = (String) productData.get("productModel");
        String brandNameEn = (String) productData.get("brandNameEn");
        
        return productCode != null && !productCode.isEmpty() &&
               productModel != null && !productModel.isEmpty() &&
               brandNameEn != null && !brandNameEn.isEmpty();
    }

    /**
     * 从产品数据中提取URL，创建下载任务并将其推入Redis队列.
     */
    private void enqueueDownloadTasks(Map<String, Object> productData, Product product) {
        List<DownloadTask> tasks = new ArrayList<>();
        String productCode = product.getProductCode();

        // 1. 缩略图任务
        String thumbnailUrl = (String) productData.get("productImageUrl");
        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            String filename = productCode + "_thumbnail" + getFileExtension(thumbnailUrl);
            tasks.add(new DownloadTask(productCode, thumbnailUrl, filename, DownloadTask.DownloadType.IMAGE_THUMBNAIL));
        }

        // 2. 主图任务
        String mainImageUrl = (String) productData.get("productImageUrlBig");
        if (mainImageUrl != null && !mainImageUrl.isEmpty()) {
            String filename = productCode + "_main" + getFileExtension(mainImageUrl);
            tasks.add(new DownloadTask(productCode, mainImageUrl, filename, DownloadTask.DownloadType.IMAGE_MAIN));
        }

        // 3. 相册图片任务
        @SuppressWarnings("unchecked")
        List<String> galleryUrls = (List<String>) productData.get("productImages");
        if (galleryUrls != null && !galleryUrls.isEmpty()) {
            for (String galleryUrl : galleryUrls) {
                if (galleryUrl != null && !galleryUrl.isEmpty()) {
                    String desc = extractDescriptionFromUrl(galleryUrl);
                    String filename = productCode + "_" + desc + getFileExtension(galleryUrl);
                    tasks.add(new DownloadTask(productCode, galleryUrl, filename, DownloadTask.DownloadType.IMAGE_GALLERY));
                }
            }
        }

        // 4. PDF任务
        String pdfUrl = (String) productData.get("pdfUrl");
        if (pdfUrl != null && !pdfUrl.isEmpty()) {
            tasks.add(new DownloadTask(productCode, pdfUrl, product.getPdfFilename(), DownloadTask.DownloadType.PDF));
        }

        if (!tasks.isEmpty()) {
            downloadQueueService.enqueueTasks(tasks);
        }
    }

    /**
     * 从URL安全地提取文件扩展名.
     */
    private String getFileExtension(String url) {
        if (url == null || url.isEmpty()) return ".jpg";
        try {
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.contains(".png")) return ".png";
            if (lowerUrl.contains(".gif")) return ".gif";
            if (lowerUrl.contains(".webp")) return ".webp";
            return ".jpg";
        } catch (Exception e) {
            return ".jpg";
        }
    }

    /**
     * 从图片URL中提取描述性部分作为文件名的一部分.
     */
    private String extractDescriptionFromUrl(String url) {
        try {
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.contains("_front.")) return "front";
            if (lowerUrl.contains("_back.")) return "back";
            if (lowerUrl.contains("_blank.")) return "blank";
            if (lowerUrl.contains("_side.")) return "side";
            if (lowerUrl.contains("_top.")) return "top";

            String[] parts = url.split("/");
            String filename = parts[parts.length - 1];
            String nameWithoutExt = filename.substring(0, filename.lastIndexOf('.'));
            String[] nameParts = nameWithoutExt.split("_");
            if (nameParts.length > 1) {
                return nameParts[nameParts.length - 1];
            }
            return "gallery_" + System.currentTimeMillis();
        } catch (Exception e) {
            return "gallery_" + System.currentTimeMillis();
        }
    }

    /**
     * 处理阶梯价格信息，分别存储到对应的字段中
     * 对应CSV中的阶梯价1-5字段
     */
    private void processLadderPrices(Product product, List<Map<String, Object>> priceList) {
        try {
            // 清空现有阶梯价格
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

            // 填充阶梯价格（最多5阶）
            for (int i = 0; i < Math.min(priceList.size(), 5); i++) {
                Map<String, Object> priceData = priceList.get(i);

                Object ladder = priceData.get("ladder");
                // 优先使用 currencyPrice（统一币种），回退到 productPrice
                Object price = priceData.get("currencyPrice") != null ? priceData.get("currencyPrice") : priceData.get("productPrice");

                if (ladder != null && price != null) {
                    Integer quantity = ((Number) ladder).intValue();
                    java.math.BigDecimal priceValue = new java.math.BigDecimal(price.toString());

                    switch (i) {
                        case 0:
                            product.setLadderPrice1Quantity(quantity);
                            product.setLadderPrice1Price(priceValue);
                            break;
                        case 1:
                            product.setLadderPrice2Quantity(quantity);
                            product.setLadderPrice2Price(priceValue);
                            break;
                        case 2:
                            product.setLadderPrice3Quantity(quantity);
                            product.setLadderPrice3Price(priceValue);
                            break;
                        case 3:
                            product.setLadderPrice4Quantity(quantity);
                            product.setLadderPrice4Price(priceValue);
                            break;
                        case 4:
                            product.setLadderPrice5Quantity(quantity);
                            product.setLadderPrice5Price(priceValue);
                            break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("处理阶梯价格失败 for product {}", product.getProductCode(), e);
        }
    }

    /**
     * 更新现有产品的阶梯价格字段
     */
    private void updateLadderPrices(Product existingProduct, Product newProduct) {
        existingProduct.setLadderPrice1Quantity(newProduct.getLadderPrice1Quantity());
        existingProduct.setLadderPrice1Price(newProduct.getLadderPrice1Price());
        existingProduct.setLadderPrice2Quantity(newProduct.getLadderPrice2Quantity());
        existingProduct.setLadderPrice2Price(newProduct.getLadderPrice2Price());
        existingProduct.setLadderPrice3Quantity(newProduct.getLadderPrice3Quantity());
        existingProduct.setLadderPrice3Price(newProduct.getLadderPrice3Price());
        existingProduct.setLadderPrice4Quantity(newProduct.getLadderPrice4Quantity());
        existingProduct.setLadderPrice4Price(newProduct.getLadderPrice4Price());
        existingProduct.setLadderPrice5Quantity(newProduct.getLadderPrice5Quantity());
        existingProduct.setLadderPrice5Price(newProduct.getLadderPrice5Price());
    }
}
