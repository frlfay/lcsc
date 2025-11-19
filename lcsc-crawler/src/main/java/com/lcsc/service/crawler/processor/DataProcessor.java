package com.lcsc.service.crawler.processor;

import com.lcsc.entity.Product;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据处理器
 * 负责产品数据的清洗、格式化和统计分析
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Component
public class DataProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DataProcessor.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 收集所有产品的参数信息，用于Excel列设计
     * 
     * @param products 产品列表
     * @return 参数信息映射（英文名->中文名）
     */
    public Map<String, String> collectParameterInfo(List<Product> products) {
        logger.info("开始收集产品参数信息，产品数量: {}", products.size());
        
        Map<String, String> parameterInfo = new LinkedHashMap<>();
        
        for (Product product : products) {
            try {
                String detailedParams = product.getDetailedParameters();
                if (detailedParams == null || detailedParams.isEmpty() || "{}".equals(detailedParams)) {
                    continue;
                }
                
                Map<String, Map<String, String>> paramMap = 
                    objectMapper.readValue(detailedParams, new TypeReference<Map<String, Map<String, String>>>() {});
                
                for (Map.Entry<String, Map<String, String>> entry : paramMap.entrySet()) {
                    String paramNameEn = entry.getKey();
                    Map<String, String> paramInfo = entry.getValue();
                    String paramNameCn = paramInfo.get("nameCn");
                    
                    if (paramNameCn != null && !paramNameCn.isEmpty()) {
                        parameterInfo.put(paramNameEn, paramNameCn);
                    }
                }
                
            } catch (Exception e) {
                logger.debug("解析产品参数失败: {}", product.getProductCode(), e);
            }
        }
        
        logger.info("收集参数信息完成，参数种类数量: {}", parameterInfo.size());
        return parameterInfo;
    }

    /**
     * 收集所有产品的价格阶梯信息
     * 
     * @param products 产品列表
     * @return 价格阶梯集合
     */
    public Set<Integer> collectLadderSteps(List<Product> products) {
        logger.info("开始收集价格阶梯信息，产品数量: {}", products.size());
        
        Set<Integer> ladderSteps = new TreeSet<>();
        
        for (Product product : products) {
            try {
                String tierPrices = product.getTierPrices();
                if (tierPrices == null || tierPrices.isEmpty() || "[]".equals(tierPrices)) {
                    continue;
                }
                
                List<Map<String, Object>> priceList = 
                    objectMapper.readValue(tierPrices, new TypeReference<List<Map<String, Object>>>() {});
                
                for (Map<String, Object> price : priceList) {
                    Object ladder = price.get("ladder");
                    if (ladder instanceof Number) {
                        ladderSteps.add(((Number) ladder).intValue());
                    }
                }
                
            } catch (Exception e) {
                logger.debug("解析产品价格失败: {}", product.getProductCode(), e);
            }
        }
        
        logger.info("收集价格阶梯完成，阶梯种类数量: {}", ladderSteps.size());
        return ladderSteps;
    }

    /**
     * 处理单个产品数据，转换为Excel行数据
     * 
     * @param product 产品对象
     * @param parameterInfo 参数信息映射
     * @param ladderSteps 价格阶梯集合
     * @return Excel行数据
     */
    public Map<String, Object> processProductForExcel(Product product, 
                                                     Map<String, String> parameterInfo, 
                                                     Set<Integer> ladderSteps) {
        Map<String, Object> rowData = new LinkedHashMap<>();
        
        try {
            // 基础固定列
            rowData.put("产品型号", safeString(product.getModel()));
            rowData.put("产品编码", safeString(product.getProductCode()));
            rowData.put("品牌", safeString(product.getBrand()));
            rowData.put("封装规格", safeString(product.getPackageName()));
            rowData.put("产品介绍", safeString(product.getBriefDescription()));
            rowData.put("PDF链接", safeString(product.getPdfFilename()));
            rowData.put("产品重量", ""); // 暂时留空
            rowData.put("最小购买量", ""); // 暂时留空
            rowData.put("最大购买量", ""); // 暂时留空
            rowData.put("包装方式", ""); // 暂时留空
            rowData.put("包装数量", ""); // 暂时留空
            rowData.put("包装单位", ""); // 暂时留空
            rowData.put("产品单位", ""); // 暂时留空
            rowData.put("湿敏等级", ""); // 暂时留空
            rowData.put("库存", safeInteger(product.getTotalStockQuantity()));
            rowData.put("全球数量", safeInteger(product.getTotalStockQuantity()));
            rowData.put("标签", "");
            
            // 处理参数列（中英文交替）
            processParametersForExcel(product, parameterInfo, rowData);
            
            // 处理价格阶梯列
            processPricesForExcel(product, ladderSteps, rowData);
            
            // 处理图片列
            rowData.put("图片", safeString(product.getImageName()));
            rowData.put("产品图片", ""); // 暂时留空，可以从详细参数中提取
            
        } catch (Exception e) {
            logger.error("处理产品Excel数据失败: {}", product.getProductCode(), e);
        }
        
        return rowData;
    }

    /**
     * 处理产品参数数据
     */
    private void processParametersForExcel(Product product, Map<String, String> parameterInfo, 
                                         Map<String, Object> rowData) {
        try {
            Map<String, Map<String, String>> productParams = new HashMap<>();
            
            String detailedParams = product.getDetailedParameters();
            if (detailedParams != null && !detailedParams.isEmpty() && !"{}".equals(detailedParams)) {
                productParams = objectMapper.readValue(detailedParams, 
                    new TypeReference<Map<String, Map<String, String>>>() {});
            }
            
            // 按参数英文名排序，确保列顺序一致
            for (Map.Entry<String, String> paramEntry : parameterInfo.entrySet()) {
                String paramNameEn = paramEntry.getKey();
                String paramNameCn = paramEntry.getValue();
                
                // 中文列
                String valueCn = "";
                String valueEn = "";
                
                if (productParams.containsKey(paramNameEn)) {
                    Map<String, String> paramData = productParams.get(paramNameEn);
                    valueCn = safeString(paramData.get("valueCn"));
                    valueEn = safeString(paramData.get("valueEn"));
                }
                
                rowData.put(paramNameCn, valueCn);
                rowData.put(paramNameEn, valueEn);
            }
            
        } catch (Exception e) {
            logger.error("处理产品参数失败: {}", product.getProductCode(), e);
        }
    }

    /**
     * 处理产品价格数据
     */
    private void processPricesForExcel(Product product, Set<Integer> ladderSteps, 
                                     Map<String, Object> rowData) {
        try {
            Map<Integer, String> priceMap = new HashMap<>();
            
            String tierPrices = product.getTierPrices();
            if (tierPrices != null && !tierPrices.isEmpty() && !"[]".equals(tierPrices)) {
                List<Map<String, Object>> priceList = objectMapper.readValue(tierPrices, 
                    new TypeReference<List<Map<String, Object>>>() {});
                
                for (Map<String, Object> price : priceList) {
                    Object ladder = price.get("ladder");
                    Object priceValue = price.get("price");
                    
                    if (ladder instanceof Number && priceValue != null) {
                        priceMap.put(((Number) ladder).intValue(), priceValue.toString());
                    }
                }
            }
            
            // 按阶梯顺序添加价格列
            int index = 1;
            for (Integer ladder : ladderSteps) {
                String quantity = ladder.toString();
                String price = priceMap.getOrDefault(ladder, "");
                
                rowData.put("数" + index, quantity);
                rowData.put("价" + index, price);
                index++;
            }
            
        } catch (Exception e) {
            logger.error("处理产品价格失败: {}", product.getProductCode(), e);
        }
    }

    /**
     * 生成Excel列标题
     * 
     * @param parameterInfo 参数信息
     * @param ladderSteps 价格阶梯
     * @return 列标题列表
     */
    public List<String> generateExcelHeaders(Map<String, String> parameterInfo, Set<Integer> ladderSteps) {
        List<String> headers = new ArrayList<>();
        
        // 固定列
        headers.addAll(Arrays.asList(
            "产品型号", "产品编码", "品牌", "封装规格", "产品介绍", "PDF链接",
            "产品重量", "最小购买量", "最大购买量", "包装方式", "包装数量",
            "包装单位", "产品单位", "湿敏等级", "库存", "全球数量", "标签"
        ));
        
        // 参数列（中英文交替）
        for (Map.Entry<String, String> entry : parameterInfo.entrySet()) {
            headers.add(entry.getValue()); // 中文名
            headers.add(entry.getKey());   // 英文名
        }
        
        // 价格阶梯列
        int index = 1;
        for (Integer ladder : ladderSteps) {
            headers.add("数" + index);
            headers.add("价" + index);
            index++;
        }
        
        // 图片列
        headers.add("图片");
        headers.add("产品图片");
        
        return headers;
    }

    /**
     * 统计产品数据
     * 
     * @param products 产品列表
     * @return 统计信息
     */
    public Map<String, Object> generateStatistics(List<Product> products) {
        logger.info("开始生成产品统计信息");
        
        Map<String, Object> statistics = new HashMap<>();
        
        // 基础统计
        statistics.put("totalProducts", products.size());
        statistics.put("generatedAt", new Date());
        
        // 品牌统计
        Map<String, Long> brandCount = products.stream()
            .collect(Collectors.groupingBy(
                p -> safeString(p.getBrand()),
                Collectors.counting()
            ));
        statistics.put("brandStatistics", brandCount);
        
        // 封装统计
        Map<String, Long> packageCount = products.stream()
            .collect(Collectors.groupingBy(
                p -> safeString(p.getPackageName()),
                Collectors.counting()
            ));
        statistics.put("packageStatistics", packageCount);
        
        // 库存统计
        int totalStock = products.stream()
            .mapToInt(p -> p.getTotalStockQuantity() != null ? p.getTotalStockQuantity() : 0)
            .sum();
        statistics.put("totalStock", totalStock);
        
        // 有库存产品数量
        long inStockCount = products.stream()
            .filter(p -> p.getTotalStockQuantity() != null && p.getTotalStockQuantity() > 0)
            .count();
        statistics.put("inStockProducts", inStockCount);
        
        logger.info("产品统计完成: 总数={}, 品牌数={}, 封装数={}, 总库存={}", 
            products.size(), brandCount.size(), packageCount.size(), totalStock);
        
        return statistics;
    }

    // 辅助方法：安全获取字符串
    private String safeString(String value) {
        return value != null ? value : "";
    }

    // 辅助方法：安全获取整数
    private String safeInteger(Integer value) {
        return value != null ? value.toString() : "";
    }
}
