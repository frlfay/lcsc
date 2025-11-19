package com.lcsc.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lcsc.common.Result;
import com.lcsc.entity.Product;
import com.lcsc.entity.Shop;
import com.lcsc.entity.CategoryLevel1Code;
import com.lcsc.entity.CategoryLevel2Code;
import com.lcsc.entity.ImageLink;
import com.lcsc.mapper.ProductMapper;
import com.lcsc.mapper.ShopMapper;
import com.lcsc.mapper.CategoryLevel1CodeMapper;
import com.lcsc.mapper.CategoryLevel2CodeMapper;
import com.lcsc.mapper.ImageLinkMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:5177", "http://127.0.0.1:5177"})
public class SystemController {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private CategoryLevel1CodeMapper categoryLevel1CodeMapper;

    @Autowired
    private CategoryLevel2CodeMapper categoryLevel2CodeMapper;

    @Autowired
    private ImageLinkMapper imageLinkMapper;

    @GetMapping("/health")
    public Result<String> health() {
        // 健康检查接口
        return Result.success("OK");
    }

    @GetMapping("/system/health")
    public Result<String> systemHealth() {
        return Result.success("系统运行正常");
    }

    @GetMapping("/system/statistics")
    public Result<Map<String, Object>> systemStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        
        try {
            // 统计产品总数
            Long totalProducts = productMapper.selectCount(null);
            statistics.put("totalProducts", totalProducts);
            
            // 统计有库存的产品数量（假设库存大于0表示有库存）
            QueryWrapper<Product> stockWrapper = new QueryWrapper<>();
            stockWrapper.gt("total_stock_quantity", 0);
            Long productsWithStock = productMapper.selectCount(stockWrapper);
            statistics.put("productsWithStock", productsWithStock);
            
            // 统计无库存的产品数量
            Long productsWithoutStock = totalProducts - productsWithStock;
            statistics.put("productsWithoutStock", productsWithoutStock);
            
            // 统计一级分类数量
            Long totalLevel1Categories = categoryLevel1CodeMapper.selectCount(null);
            statistics.put("totalLevel1Categories", totalLevel1Categories);
            
            // 统计二级分类数量
            Long totalLevel2Categories = categoryLevel2CodeMapper.selectCount(null);
            statistics.put("totalLevel2Categories", totalLevel2Categories);
            
            // 统计店铺数量
            Long totalShops = shopMapper.selectCount(null);
            statistics.put("totalShops", totalShops);
            
            // 统计图片链接数量
            Long totalImageLinks = imageLinkMapper.selectCount(null);
            statistics.put("totalImageLinks", totalImageLinks);
            
        } catch (Exception e) {
            // 如果查询出错，返回0值，避免前端显示异常
            statistics.put("totalProducts", 0);
            statistics.put("productsWithStock", 0);
            statistics.put("productsWithoutStock", 0);
            statistics.put("totalLevel1Categories", 0);
            statistics.put("totalLevel2Categories", 0);
            statistics.put("totalShops", 0);
            statistics.put("totalImageLinks", 0);
            e.printStackTrace();
        }
        
        return Result.success(statistics);
    }

    @GetMapping("/system/apis")
    public Result<Map<String, Object>> getApiList() {
        Map<String, Object> apis = new HashMap<>();
        apis.put("系统管理", new String[]{
            "GET /api/system/health - 系统健康检查",
            "GET /api/system/statistics - 系统统计信息",
            "GET /api/system/apis - API列表"
        });
        apis.put("产品管理", new String[]{
            "GET /api/products/page - 分页查询产品",
            "GET /api/products/{id} - 根据ID查询产品",
            "POST /api/products - 新增产品",
            "PUT /api/products/{id} - 更新产品",
            "DELETE /api/products/{id} - 删除产品"
        });
        apis.put("店铺管理", new String[]{
            "GET /api/shops/page - 分页查询店铺",
            "GET /api/shops/list - 获取店铺列表",
            "POST /api/shops - 新增店铺",
            "PUT /api/shops/{id} - 更新店铺",
            "DELETE /api/shops/{id} - 删除店铺"
        });
        apis.put("分类管理", new String[]{
            "GET /api/categories/level1/page - 分页查询一级分类",
            "GET /api/categories/level1/list - 获取一级分类列表",
            "GET /api/categories/level2/page - 分页查询二级分类",
            "GET /api/categories/level2/list/{id} - 根据一级分类查询二级分类"
        });
        apis.put("图片管理", new String[]{
            "GET /api/image-links/page - 分页查询图片链接",
            "GET /api/image-links/shop/{id} - 根据店铺查询图片",
            "POST /api/image-links - 新增图片链接",
            "DELETE /api/image-links/{id} - 删除图片链接"
        });
        apis.put("爬虫管理", new String[]{
            "POST /api/crawler/crawl-single - 爬取单个产品",
            "POST /api/crawler/crawl-batch - 批量爬取产品",
            "GET /api/crawler/status - 获取爬虫状态",
            "POST /api/crawler/start - 启动爬虫",
            "POST /api/crawler/stop - 停止爬虫"
        });
        return Result.success(apis);
    }
}
