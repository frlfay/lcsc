package com.lcsc.service.crawler.parser;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 目录解析器
 * 负责解析立创商城的目录结构数据
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Component
public class CatalogParser {

    private static final Logger logger = LoggerFactory.getLogger(CatalogParser.class);

    /**
     * 解析目录响应数据
     * 
     * @param responseData API响应数据
     * @return 解析后的目录映射列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseCatalogMapping(Map<String, Object> responseData) {
        logger.info("开始解析目录映射数据");
        
        List<Map<String, Object>> catalogMapping = new ArrayList<>();
        
        try {
            // 获取result对象
            Map<String, Object> result = (Map<String, Object>) responseData.get("result");
            if (result == null) {
                logger.warn("目录响应中没有找到result字段");
                return catalogMapping;
            }
            
            // 获取catalogList数组
            List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("catalogList");
            if (results == null) {
                logger.warn("result对象中没有找到catalogList字段");
                return catalogMapping;
            }
            
            for (Map<String, Object> category : results) {
                Map<String, Object> catalogInfo = parseSingleCategory(category);
                if (catalogInfo != null) {
                    catalogMapping.add(catalogInfo);
                }
            }
            
            logger.info("成功解析目录映射，包含 {} 个一级分类", catalogMapping.size());
            
        } catch (Exception e) {
            logger.error("解析目录映射时出错", e);
            throw new RuntimeException("解析目录映射失败", e);
        }
        
        return catalogMapping;
    }

    /**
     * 解析单个分类
     * 
     * @param category 分类数据
     * @return 解析后的分类信息
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSingleCategory(Map<String, Object> category) {
        try {
            List<Map<String, Object>> childCatalogs = 
                (List<Map<String, Object>>) category.get("childCatelogs");
            
            if (childCatalogs == null) {
                childCatalogs = new ArrayList<>();
            }
            
            Map<String, Object> catalogInfo = new HashMap<>();
            catalogInfo.put("key", getStringValue(category, "catalogNameEn", "Unknown"));
            
            // 名称信息
            Map<String, String> nameInfo = new HashMap<>();
            nameInfo.put("en", getStringValue(category, "catalogNameEn", "Unknown"));
            nameInfo.put("cn", getStringValue(category, "catalogName", "Unknown"));
            catalogInfo.put("name", nameInfo);
            
            // 基本信息
            catalogInfo.put("id", getIntegerValue(category, "catalogId", 0));
            catalogInfo.put("description", getStringValue(category, "description", ""));
            catalogInfo.put("level", getIntegerValue(category, "level", 1));
            catalogInfo.put("hasChild", getBooleanValue(category, "hasChild", false));
            catalogInfo.put("childCount", childCatalogs.size());
            catalogInfo.put("updateTime", getStringValue(category, "updateTime", ""));
            catalogInfo.put("createTime", getStringValue(category, "createTime", ""));
            catalogInfo.put("status", getIntegerValue(category, "status", 1));
            catalogInfo.put("productNum", getIntegerValue(category, "productNum", 0));
            
            // 解析子分类
            List<Map<String, Object>> valueList = new ArrayList<>();
            for (Map<String, Object> subCategory : childCatalogs) {
                Map<String, Object> subInfo = parseSubCategory(subCategory);
                if (subInfo != null) {
                    valueList.add(subInfo);
                }
            }
            catalogInfo.put("value", valueList);
            
            return catalogInfo;
            
        } catch (Exception e) {
            logger.error("解析单个分类时出错: {}", category.get("catalogName"), e);
            return null;
        }
    }

    /**
     * 解析子分类
     * 
     * @param subCategory 子分类数据
     * @return 解析后的子分类信息
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSubCategory(Map<String, Object> subCategory) {
        try {
            Map<String, Object> subInfo = new HashMap<>();
            
            subInfo.put("catalogId", getIntegerValue(subCategory, "catalogId", 0));
            subInfo.put("catalogNameEn", getStringValue(subCategory, "catalogNameEn", "Unknown"));
            subInfo.put("catalogName", getStringValue(subCategory, "catalogName", "Unknown"));
            subInfo.put("description", getStringValue(subCategory, "description", ""));
            subInfo.put("level", getIntegerValue(subCategory, "level", 2));
            subInfo.put("hasChild", getBooleanValue(subCategory, "hasChild", false));
            subInfo.put("parentId", getStringValue(subCategory, "parentId", ""));
            subInfo.put("updateTime", getStringValue(subCategory, "updateTime", ""));
            subInfo.put("createTime", getStringValue(subCategory, "createTime", ""));
            subInfo.put("status", getIntegerValue(subCategory, "status", 1));
            subInfo.put("productCount", getIntegerValue(subCategory, "productCount", 0));
            subInfo.put("productNum", getIntegerValue(subCategory, "productNum", 0));
            subInfo.put("icon", getStringValue(subCategory, "icon", ""));
            
            // 解析路径
            Object pathObj = subCategory.get("path");
            List<String> path = new ArrayList<>();
            if (pathObj instanceof List) {
                List<?> pathList = (List<?>) pathObj;
                for (Object item : pathList) {
                    if (item != null) {
                        path.add(item.toString());
                    }
                }
            }
            subInfo.put("path", path);
            
            return subInfo;
            
        } catch (Exception e) {
            logger.error("解析子分类时出错: {}", subCategory.get("catalogName"), e);
            return null;
        }
    }

    /**
     * 解析制造商/品牌列表
     * 
     * @param responseData API响应数据
     * @return 制造商列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseManufacturers(Map<String, Object> responseData) {
        logger.debug("解析制造商列表");
        
        try {
            Map<String, Object> result = (Map<String, Object>) responseData.get("result");
            if (result == null) {
                logger.warn("制造商响应中没有找到result字段");
                return new ArrayList<>();
            }
            
            List<Map<String, Object>> manufacturers = 
                (List<Map<String, Object>>) result.get("Manufacturer");
            
            if (manufacturers == null) {
                logger.warn("result中没有找到Manufacturer字段");
                return new ArrayList<>();
            }
            
            logger.debug("解析到 {} 个制造商", manufacturers.size());
            return manufacturers;
            
        } catch (Exception e) {
            logger.error("解析制造商列表时出错", e);
            return new ArrayList<>();
        }
    }

    /**
     * 解析封装列表
     * 
     * @param responseData API响应数据
     * @return 封装列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parsePackages(Map<String, Object> responseData) {
        logger.debug("解析封装列表");
        
        try {
            Map<String, Object> result = (Map<String, Object>) responseData.get("result");
            if (result == null) {
                logger.warn("封装响应中没有找到result字段");
                return new ArrayList<>();
            }
            
            List<Map<String, Object>> packages = 
                (List<Map<String, Object>>) result.get("Package");
            
            if (packages == null) {
                logger.warn("result中没有找到Package字段");
                return new ArrayList<>();
            }
            
            logger.debug("解析到 {} 个封装", packages.size());
            return packages;
            
        } catch (Exception e) {
            logger.error("解析封装列表时出错", e);
            return new ArrayList<>();
        }
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

    // 辅助方法：安全获取布尔值
    private Boolean getBooleanValue(Map<String, Object> map, String key, Boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
}
