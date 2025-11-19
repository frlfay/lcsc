package com.lcsc.service.crawler.parser;

import com.lcsc.entity.Product;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;

/**
 * 产品解析器
 * 负责解析立创商城的产品数据
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Component
public class ProductParser {

    private static final Logger logger = LoggerFactory.getLogger(ProductParser.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析产品列表响应
     * 
     * @param responseData API响应数据
     * @return 产品数据列表和分页信息
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseProductListResponse(Map<String, Object> responseData) {
        logger.debug("解析产品列表响应");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            Map<String, Object> resultData = (Map<String, Object>) responseData.get("result");
            if (resultData == null) {
                logger.warn("产品列表响应中没有找到result字段");
                result.put("dataList", new ArrayList<>());
                result.put("totalPage", 0);
                result.put("currentPage", 1);
                result.put("totalCount", 0);
                return result;
            }
            
            // 解析产品数据列表
            List<Map<String, Object>> dataList = 
                (List<Map<String, Object>>) resultData.get("dataList");
            
            if (dataList == null) {
                dataList = new ArrayList<>();
            }
            
            // 解析分页信息
            int totalPage = getIntegerValue(resultData, "totalPage", 0);
            int currentPage = getIntegerValue(resultData, "currentPage", 1);
            int totalCount = getIntegerValue(resultData, "totalCount", 0);
            
            result.put("dataList", dataList);
            result.put("totalPage", totalPage);
            result.put("currentPage", currentPage);
            result.put("totalCount", totalCount);
            
            logger.debug("解析产品列表完成: 当前页{}/{}, 产品数量{}, 总数量{}", 
                currentPage, totalPage, dataList.size(), totalCount);
            
            return result;
            
        } catch (Exception e) {
            logger.error("解析产品列表响应时出错", e);
            throw new RuntimeException("解析产品列表失败", e);
        }
    }

    /**
     * 将原始产品数据转换为Product实体
     * 
     * @param productData 原始产品数据
     * @param categoryLevel1Id 一级分类ID
     * @param categoryLevel2Id 二级分类ID
     * @return Product实体
     */
    @SuppressWarnings("unchecked")
    public Product convertToProduct(Map<String, Object> productData, 
                                  Integer categoryLevel1Id, Integer categoryLevel2Id) {
        try {
            Product product = new Product();
            
            // 基础信息
            product.setProductCode(getStringValue(productData, "productCode", ""));
            product.setCategoryLevel1Id(categoryLevel1Id);
            product.setCategoryLevel2Id(categoryLevel2Id);
            product.setBrand(cleanBrandName(getStringValue(productData, "brandNameEn", "")));
            product.setModel(getStringValue(productData, "productModel", ""));
            product.setPackageName(cleanPackageName(getStringValue(productData, "encapStandard", "")));
            
            // PDF信息
            String pdfUrl = getStringValue(productData, "pdfUrl", "");
            if (!pdfUrl.isEmpty()) {
                product.setPdfFilename(generatePdfFilename(product.getProductCode(),
                    product.getBrand(), product.getModel()));
                product.setPdfLocalPath(""); // 下载时设置
            }
            
            // 图片信息 - 优先下载高清多图
            List<String> productImages = (List<String>) productData.get("productImages");
            if (productImages != null && !productImages.isEmpty()) {
                // 使用 productImages 数组（高清图片）
                product.setProductImagesInfo(parseProductImages(productImages));
                // 设置主图信息（使用第一张图片作为主图）
                product.setImageName(generateImageName(product.getProductCode(), 1));
                product.setImageLocalPath(""); // 暂时留空，下载时设置
            } else {
                // 降级使用单张图片
                String productImage = getStringValue(productData, "productImage", "");
                if (!productImage.isEmpty()) {
                    product.setImageName(generateImageName(product.getProductCode(), 1));
                    product.setImageLocalPath(""); // 暂时留空，下载时设置
                }
            }
            
            // 库存信息
            product.setTotalStockQuantity(getIntegerValue(productData, "stockNumber", 0));
            
            // 生成简介
            product.setBriefDescription(generateBriefDescription(product));
            
            // 处理阶梯价格
            List<Map<String, Object>> priceList = 
                (List<Map<String, Object>>) productData.get("productPriceList");
            product.setTierPrices(parseTierPrices(priceList));
            product.setTierPricesLastUpdate(LocalDate.now());
            product.setTierPricesManualEdit(false);
            
            // 处理详细参数
            List<Map<String, Object>> paramList = 
                (List<Map<String, Object>>) productData.get("paramVOList");
            product.setDetailedParameters(parseDetailedParameters(paramList));
            
            // 时间字段
            product.setCreatedAt(LocalDateTime.now());
            product.setUpdatedAt(LocalDateTime.now());
            product.setLastCrawledAt(LocalDateTime.now());
            
            return product;
            
        } catch (Exception e) {
            logger.error("转换产品数据时出错: {}", productData.get("productCode"), e);
            throw new RuntimeException("转换产品数据失败", e);
        }
    }

    /**
     * 解析阶梯价格
     * 
     * @param priceList 价格列表
     * @return JSON格式的价格数据
     */
    private String parseTierPrices(List<Map<String, Object>> priceList) {
        if (priceList == null || priceList.isEmpty()) {
            return "[]";
        }
        
        try {
            List<Map<String, Object>> tierPrices = new ArrayList<>();
            
            for (Map<String, Object> price : priceList) {
                Map<String, Object> tierPrice = new HashMap<>();
                tierPrice.put("ladder", getIntegerValue(price, "ladder", 0));
                tierPrice.put("price", getStringValue(price, "usdPrice", ""));
                tierPrice.put("currency", "USD");
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
            logger.error("解析阶梯价格时出错", e);
            return "[]";
        }
    }

    /**
     * 解析详细参数
     * 
     * @param paramList 参数列表
     * @return JSON格式的参数数据
     */
    private String parseDetailedParameters(List<Map<String, Object>> paramList) {
        if (paramList == null || paramList.isEmpty()) {
            return "{}";
        }
        
        try {
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
            logger.error("解析详细参数时出错", e);
            return "{}";
        }
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
     * 清理封装名称（删除-)
     */
    private String cleanPackageName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "";
        }
        return packageName.replace("-", "").trim();
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

    /**
     * 生成PDF文件名
     */
    private String generatePdfFilename(String productCode, String brand, String model) {
        String cleanBrand = cleanBrandName(brand);
        String cleanModel = model.replace("-", "");
        return String.format("%s_%s_%s.pdf", productCode, cleanBrand, cleanModel);
    }

    /**
     * 解析产品图片信息
     */
    private String parseProductImages(List<String> productImages) {
        try {
            List<Map<String, Object>> imagesInfo = new ArrayList<>();

            for (int i = 0; i < productImages.size(); i++) {
                String imageUrl = productImages.get(i);
                Map<String, Object> imageInfo = new HashMap<>();
                imageInfo.put("index", i + 1);
                imageInfo.put("url", imageUrl);
                imageInfo.put("filename", generateImageName(extractProductCodeFromUrl(imageUrl), i + 1));
                imageInfo.put("downloaded", false);
                imageInfo.put("localPath", "");
                imagesInfo.add(imageInfo);
            }

            return objectMapper.writeValueAsString(imagesInfo);

        } catch (Exception e) {
            logger.error("解析产品图片信息时出错", e);
            return "[]";
        }
    }

    /**
     * 从图片URL中提取产品代码
     */
    private String extractProductCodeFromUrl(String imageUrl) {
        try {
            // 从URL中提取产品代码，例如：https://assets.lcsc.com/images/lcsc/900x900/20250911_Huaneng-TMB12A05_C96093_front.jpg
            // 提取 C96093
            String[] parts = imageUrl.split("_");
            for (String part : parts) {
                if (part.startsWith("C") && part.length() >= 7) {
                    return part.split("\\.")[0]; // 去掉文件扩展名
                }
            }
        } catch (Exception e) {
            logger.debug("无法从URL提取产品代码: {}", imageUrl);
        }
        return "";
    }

    /**
     * 生成图片名称（支持多图）
     */
    private String generateImageName(String productCode, int imageIndex) {
        return String.format("%s_%d.jpg", productCode, imageIndex);
    }

    /**
     * 生成简介（型号+封装+分类信息，限制60字节）
     */
    private String generateBriefDescription(Product product) {
        StringBuilder sb = new StringBuilder();
        
        if (product.getModel() != null && !product.getModel().isEmpty()) {
            sb.append(product.getModel());
        }
        
        if (product.getPackageName() != null && !product.getPackageName().isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(product.getPackageName());
        }
        
        // 这里可以添加分类信息，但需要从其他地方获取分类名称
        // 暂时只使用型号和封装
        
        String description = sb.toString();
        
        // 限制60字节（约20个中文字符）
        if (description.getBytes().length > 60) {
            // 简单截断，实际应用中可能需要更智能的截断
            while (description.getBytes().length > 60 && description.length() > 0) {
                description = description.substring(0, description.length() - 1);
            }
        }
        
        return description;
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
}
