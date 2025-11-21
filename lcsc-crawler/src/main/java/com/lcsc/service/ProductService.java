package com.lcsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lcsc.entity.CategoryLevel1Code;
import com.lcsc.entity.CategoryLevel2Code;
import com.lcsc.entity.CategoryLevel3Code;
import com.lcsc.entity.Product;
import com.lcsc.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 产品服务层
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Service
public class ProductService extends ServiceImpl<ProductMapper, Product> {

    @Autowired
    private CategoryLevel1CodeService categoryLevel1CodeService;

    @Autowired
    private CategoryLevel2CodeService categoryLevel2CodeService;

    @Autowired
    private CategoryLevel3CodeService categoryLevel3CodeService;

    /**
     * 分页查询产品
     */
    public IPage<Product> getProductPage(int current, int size, String productCode, String brand,
                                       String model, String packageName, Integer categoryLevel1Id,
                                       Integer categoryLevel2Id, Integer categoryLevel3Id, Boolean hasStock) {
        Page<Product> page = new Page<>(current, size);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        if (productCode != null && !productCode.trim().isEmpty()) {
            wrapper.like(Product::getProductCode, productCode);
        }
        if (brand != null && !brand.trim().isEmpty()) {
            wrapper.like(Product::getBrand, brand);
        }
        if (model != null && !model.trim().isEmpty()) {
            wrapper.like(Product::getModel, model);
        }
        if (packageName != null && !packageName.trim().isEmpty()) {
            wrapper.like(Product::getPackageName, packageName);
        }
        if (categoryLevel1Id != null) {
            // 直接使用categoryLevel1Id查询，因为Product表中的categoryLevel1Id已经存储的是正确的ID
            wrapper.eq(Product::getCategoryLevel1Id, categoryLevel1Id);
        }
        if (categoryLevel2Id != null) {
            wrapper.eq(Product::getCategoryLevel2Id, categoryLevel2Id);
        }
        if (categoryLevel3Id != null) {
            wrapper.eq(Product::getCategoryLevel3Id, categoryLevel3Id);
        }
        if (hasStock != null) {
            if (hasStock) {
                wrapper.gt(Product::getTotalStockQuantity, 0);
            } else {
                wrapper.le(Product::getTotalStockQuantity, 0);
            }
        }

        wrapper.orderByDesc(Product::getLastCrawledAt);

        IPage<Product> result = page(page, wrapper);

        // 填充分类名称，避免前端只拿到ID无法展示
        enrichCategoryNames(result.getRecords());

        return result;
    }

    /**
     * 分页查询产品（向后兼容方法）
     */
    public IPage<Product> getProductPage(int current, int size, String productCode, String brand) {
        return getProductPage(current, size, productCode, brand, null, null, null, null, null, null);
    }

    /**
     * 根据产品编号查询产品
     */
    public Product getByProductCode(String productCode) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductCode, productCode);
        Product product = getOne(wrapper);
        enrichCategoryNames(product);
        return product;
    }

    /**
     * 保存或更新产品
     */
    public boolean saveOrUpdateProduct(Product product) {
        Product existingProduct = getByProductCode(product.getProductCode());
        if (existingProduct != null) {
            product.setId(existingProduct.getId());
        }
        return saveOrUpdate(product);
    }

    /**
     * 根据分类查询产品列表
     */
    public List<Product> getProductListByCategory(Integer categoryLevel1Id, Integer categoryLevel2Id) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        
        if (categoryLevel1Id != null) {
            wrapper.eq(Product::getCategoryLevel1Id, categoryLevel1Id);
        }
        if (categoryLevel2Id != null) {
            wrapper.eq(Product::getCategoryLevel2Id, categoryLevel2Id);
        }
        
        wrapper.orderByDesc(Product::getLastCrawledAt);
        
        List<Product> products = list(wrapper);
        enrichCategoryNames(products);
        return products;
    }

    /**
     * 获取产品统计信息
     */
    public Map<String, Object> getProductStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        
        // 总产品数
        long totalProducts = count();
        statistics.put("totalProducts", totalProducts);
        
        // 有库存的产品数
        LambdaQueryWrapper<Product> hasStockWrapper = new LambdaQueryWrapper<>();
        hasStockWrapper.gt(Product::getTotalStockQuantity, 0);
        long productsWithStock = count(hasStockWrapper);
        statistics.put("productsWithStock", productsWithStock);
        
        // 无库存的产品数
        long productsWithoutStock = totalProducts - productsWithStock;
        statistics.put("productsWithoutStock", productsWithoutStock);
        
        // 品牌数量
        // 注意：这需要数据库支持 DISTINCT COUNT，简化实现
        statistics.put("totalBrands", 0); // 暂时设为0，需要复杂查询
        
        return statistics;
    }

    // ========== 私有辅助方法 ==========
    /**
     * 为产品列表填充分类名称字段，避免前端根据ID再次查询
     */
    private void enrichCategoryNames(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }

        // 批量收集ID，避免N+1查询
        Set<Integer> level1Ids = products.stream()
                .map(Product::getCategoryLevel1Id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Integer> level2Ids = products.stream()
                .map(Product::getCategoryLevel2Id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Integer> level3Ids = products.stream()
                .map(Product::getCategoryLevel3Id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Integer, String> level1NameMap = level1Ids.isEmpty() ? new HashMap<>() :
                categoryLevel1CodeService.listByIds(level1Ids).stream()
                        .collect(Collectors.toMap(CategoryLevel1Code::getId, CategoryLevel1Code::getCategoryLevel1Name));

        Map<Integer, String> level2NameMap = level2Ids.isEmpty() ? new HashMap<>() :
                categoryLevel2CodeService.listByIds(level2Ids).stream()
                        .collect(Collectors.toMap(CategoryLevel2Code::getId, CategoryLevel2Code::getCategoryLevel2Name));

        Map<Integer, String> level3NameMap = level3Ids.isEmpty() ? new HashMap<>() :
                categoryLevel3CodeService.listByIds(level3Ids).stream()
                        .collect(Collectors.toMap(CategoryLevel3Code::getId, CategoryLevel3Code::getCategoryLevel3Name));

        for (Product p : products) {
            if (p == null) continue;
            if (p.getCategoryLevel1Id() != null) {
                p.setCategoryLevel1Name(level1NameMap.get(p.getCategoryLevel1Id()));
            }
            if (p.getCategoryLevel2Id() != null) {
                p.setCategoryLevel2Name(level2NameMap.get(p.getCategoryLevel2Id()));
            }
            if (p.getCategoryLevel3Id() != null) {
                p.setCategoryLevel3Name(level3NameMap.get(p.getCategoryLevel3Id()));
            }
        }
    }

    /**
     * 为单个产品填充分类名称
     */
    private void enrichCategoryNames(Product product) {
        if (product == null) {
            return;
        }
        if (product.getCategoryLevel1Id() != null) {
            CategoryLevel1Code l1 = categoryLevel1CodeService.getById(product.getCategoryLevel1Id());
            if (l1 != null) {
                product.setCategoryLevel1Name(l1.getCategoryLevel1Name());
            }
        }
        if (product.getCategoryLevel2Id() != null) {
            CategoryLevel2Code l2 = categoryLevel2CodeService.getById(product.getCategoryLevel2Id());
            if (l2 != null) {
                product.setCategoryLevel2Name(l2.getCategoryLevel2Name());
            }
        }
        if (product.getCategoryLevel3Id() != null) {
            CategoryLevel3Code l3 = categoryLevel3CodeService.getById(product.getCategoryLevel3Id());
            if (l3 != null) {
                product.setCategoryLevel3Name(l3.getCategoryLevel3Name());
            }
        }
    }
}
