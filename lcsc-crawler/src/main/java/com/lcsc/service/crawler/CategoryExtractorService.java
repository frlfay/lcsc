package com.lcsc.service.crawler;

import com.lcsc.entity.Product;
import com.lcsc.service.ProductService;
import com.lcsc.service.crawler.CategoryCrawlerService.CategoryPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 分类提取服务
 * 从现有产品数据中提取分类信息，用于补丁式处理
 * 
 * @author lcsc-crawler
 * @since 2025-09-09
 */
@Service
public class CategoryExtractorService {
    
    private static final Logger logger = LoggerFactory.getLogger(CategoryExtractorService.class);
    
    @Autowired
    private ProductService productService;
    
    // 常见的电子元器件分类模式
    private static final Map<String, List<String>> CATEGORY_PATTERNS = new HashMap<>();
    
    static {
        // 初始化分类模式
        CATEGORY_PATTERNS.put("电阻", Arrays.asList("resistor", "电阻", "阻值", "精密电阻", "贴片电阻", "插件电阻", "功率电阻"));
        CATEGORY_PATTERNS.put("电容", Arrays.asList("capacitor", "电容", "陶瓷电容", "电解电容", "钽电容", "贴片电容", "薄膜电容"));
        CATEGORY_PATTERNS.put("电感", Arrays.asList("inductor", "电感", "磁珠", "共模电感", "功率电感", "贴片电感"));
        CATEGORY_PATTERNS.put("二极管", Arrays.asList("diode", "二极管", "整流二极管", "肖特基", "稳压二极管", "发光二极管", "LED"));
        CATEGORY_PATTERNS.put("三极管", Arrays.asList("transistor", "三极管", "晶体管", "MOSFET", "场效应管", "BJT"));
        CATEGORY_PATTERNS.put("集成电路", Arrays.asList("IC", "芯片", "单片机", "微控制器", "运放", "电源管理", "数字电路", "模拟电路"));
        CATEGORY_PATTERNS.put("连接器", Arrays.asList("connector", "连接器", "接插件", "端子", "插座", "插头"));
        CATEGORY_PATTERNS.put("晶振", Arrays.asList("crystal", "晶振", "振荡器", "谐振器"));
        CATEGORY_PATTERNS.put("开关", Arrays.asList("switch", "开关", "按键", "拨码开关", "旋转开关"));
        CATEGORY_PATTERNS.put("传感器", Arrays.asList("sensor", "传感器", "温度传感器", "压力传感器", "光敏传感器"));
    }
    
    /**
     * 从产品数据中提取分类信息
     * 
     * @param batchSize 批次大小
     * @param offset 偏移量
     * @return 提取的分类对列表
     */
    public CompletableFuture<List<CategoryPair>> extractCategoriesFromProducts(int batchSize, int offset) {
        logger.info("开始从产品数据中提取分类信息，批次大小: {}, 偏移: {}", batchSize, offset);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 获取产品数据
                List<Product> products = getProductBatch(batchSize, offset);
                if (products.isEmpty()) {
                    logger.info("没有找到产品数据");
                    return new ArrayList<>();
                }
                
                logger.info("获取到 {} 个产品数据", products.size());
                
                // 提取分类信息
                Set<CategoryPair> categoryPairs = new HashSet<>();
                
                for (Product product : products) {
                    try {
                        CategoryPair pair = extractCategoryFromProduct(product);
                        if (pair != null && pair.getLevel1Name() != null && pair.getLevel2Name() != null) {
                            categoryPairs.add(pair);
                        }
                    } catch (Exception e) {
                        logger.debug("从产品 {} 提取分类失败: {}", product.getProductCode(), e.getMessage());
                    }
                }
                
                List<CategoryPair> result = new ArrayList<>(categoryPairs);
                logger.info("从 {} 个产品中提取出 {} 个唯一分类组合", products.size(), result.size());
                
                return result;
                
            } catch (Exception e) {
                logger.error("提取分类信息时出错", e);
                return new ArrayList<>();
            }
        });
    }
    
    /**
     * 从单个产品中提取分类信息
     */
    private CategoryPair extractCategoryFromProduct(Product product) {
        String level1Name = null;
        String level2Name = null;
        
        // 方式1: 从产品描述中提取
        String description = product.getBriefDescription();
        if (description != null && !description.trim().isEmpty()) {
            CategoryPair fromDesc = extractFromDescription(description);
            if (fromDesc != null) {
                level1Name = fromDesc.getLevel1Name();
                level2Name = fromDesc.getLevel2Name();
            }
        }
        
        // 方式2: 从品牌和型号组合推断
        if (level1Name == null) {
            CategoryPair fromModel = extractFromBrandModel(product.getBrand(), product.getModel());
            if (fromModel != null) {
                level1Name = fromModel.getLevel1Name();
                level2Name = fromModel.getLevel2Name();
            }
        }
        
        // 方式3: 从产品编号模式推断
        if (level1Name == null) {
            CategoryPair fromCode = extractFromProductCode(product.getProductCode());
            if (fromCode != null) {
                level1Name = fromCode.getLevel1Name();
                level2Name = fromCode.getLevel2Name();
            }
        }
        
        if (level1Name != null && level2Name != null) {
            return new CategoryPair(level1Name, level2Name);
        }
        
        return null;
    }
    
    /**
     * 从产品描述中提取分类
     */
    private CategoryPair extractFromDescription(String description) {
        String cleanDesc = description.toLowerCase();
        
        for (Map.Entry<String, List<String>> entry : CATEGORY_PATTERNS.entrySet()) {
            String category = entry.getKey();
            List<String> patterns = entry.getValue();
            
            for (String pattern : patterns) {
                if (cleanDesc.contains(pattern.toLowerCase())) {
                    // 找到匹配的一级分类，尝试确定二级分类
                    String subCategory = determineSubCategory(category, cleanDesc);
                    if (subCategory != null) {
                        return new CategoryPair(category, subCategory);
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 从品牌和型号推断分类
     */
    private CategoryPair extractFromBrandModel(String brand, String model) {
        if (brand == null || model == null) {
            return null;
        }
        
        String combined = (brand + " " + model).toLowerCase();
        
        // 根据品牌特征推断
        if (brand.toLowerCase().contains("ti") || brand.toLowerCase().contains("texas")) {
            // 德州仪器主要做IC
            return new CategoryPair("集成电路", "模拟电路");
        } else if (brand.toLowerCase().contains("murata")) {
            // 村田主要做被动器件
            if (combined.contains("cap")) {
                return new CategoryPair("电容", "陶瓷电容");
            } else if (combined.contains("inductor") || combined.contains("bead")) {
                return new CategoryPair("电感", "磁珠");
            }
        } else if (brand.toLowerCase().contains("yageo")) {
            // 国巨主要做电阻电容
            if (combined.contains("rc") || combined.contains("resistor")) {
                return new CategoryPair("电阻", "贴片电阻");
            } else if (combined.contains("cc") || combined.contains("capacitor")) {
                return new CategoryPair("电容", "陶瓷电容");
            }
        }
        
        return null;
    }
    
    /**
     * 从产品编号推断分类
     */
    private CategoryPair extractFromProductCode(String productCode) {
        if (productCode == null || !productCode.startsWith("C")) {
            return null;
        }
        
        // 根据立创商城的编号规律推断
        // 这里可以根据实际的编号规律来实现
        // 暂时返回null，需要根据实际数据分析编号规律
        
        return null;
    }
    
    /**
     * 根据一级分类和描述确定二级分类
     */
    private String determineSubCategory(String level1Category, String description) {
        switch (level1Category) {
            case "电阻":
                if (description.contains("贴片") || description.contains("smd")) {
                    return "贴片电阻";
                } else if (description.contains("插件") || description.contains("through hole")) {
                    return "插件电阻";
                } else if (description.contains("精密")) {
                    return "精密电阻";
                } else if (description.contains("功率")) {
                    return "功率电阻";
                }
                return "贴片电阻"; // 默认
                
            case "电容":
                if (description.contains("陶瓷") || description.contains("ceramic")) {
                    return "陶瓷电容";
                } else if (description.contains("电解") || description.contains("electrolytic")) {
                    return "电解电容";
                } else if (description.contains("钽") || description.contains("tantalum")) {
                    return "钽电容";
                } else if (description.contains("薄膜") || description.contains("film")) {
                    return "薄膜电容";
                }
                return "陶瓷电容"; // 默认
                
            case "集成电路":
                if (description.contains("运放") || description.contains("amplifier")) {
                    return "运算放大器";
                } else if (description.contains("电源") || description.contains("power")) {
                    return "电源管理";
                } else if (description.contains("单片机") || description.contains("mcu")) {
                    return "微控制器";
                } else if (description.contains("数字")) {
                    return "数字电路";
                }
                return "模拟电路"; // 默认
                
            case "二极管":
                if (description.contains("led") || description.contains("发光")) {
                    return "发光二极管";
                } else if (description.contains("肖特基") || description.contains("schottky")) {
                    return "肖特基二极管";
                } else if (description.contains("稳压")) {
                    return "稳压二极管";
                }
                return "整流二极管"; // 默认
                
            default:
                return level1Category + "其他"; // 默认子分类
        }
    }
    
    /**
     * 获取产品批次数据
     */
    private List<Product> getProductBatch(int batchSize, int offset) {
        try {
            logger.info("正在获取产品数据，批次大小: {}, 偏移: {}", batchSize, offset);
            
            // 使用MyBatis-Plus的分页查询功能
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Product> page = 
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(offset / batchSize + 1, batchSize);
            
            com.baomidou.mybatisplus.core.metadata.IPage<Product> pageResult = productService.page(page);
            List<Product> products = pageResult.getRecords();
            
            logger.info("成功获取到 {} 个产品数据", products.size());
            return products;
            
        } catch (Exception e) {
            logger.error("获取产品批次数据失败", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 获取所有需要处理的产品数量
     */
    public CompletableFuture<Long> getTotalProductCount() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long count = productService.count();
                logger.info("获取到产品总数: {}", count);
                return count;
            } catch (Exception e) {
                logger.error("获取产品总数失败", e);
                return 0L;
            }
        });
    }
    
    /**
     * 批量处理所有产品的分类提取
     */
    public CompletableFuture<List<CategoryPair>> extractAllCategories() {
        return CompletableFuture.supplyAsync(() -> {
            List<CategoryPair> allCategories = new ArrayList<>();
            int batchSize = 1000;
            int offset = 0;
            
            try {
                while (true) {
                    List<CategoryPair> batch = extractCategoriesFromProducts(batchSize, offset).get();
                    if (batch.isEmpty()) {
                        break;
                    }
                    
                    allCategories.addAll(batch);
                    offset += batchSize;
                    
                    logger.info("已处理 {} 批次，累计提取 {} 个分类组合", offset / batchSize, allCategories.size());
                    
                    // 避免过度占用资源
                    Thread.sleep(100);
                }
                
                // 去重
                Set<CategoryPair> uniqueCategories = new HashSet<>(allCategories);
                List<CategoryPair> result = new ArrayList<>(uniqueCategories);
                
                logger.info("提取完成，总共 {} 个唯一分类组合", result.size());
                return result;
                
            } catch (Exception e) {
                logger.error("批量提取分类时出错", e);
                return allCategories;
            }
        });
    }
}