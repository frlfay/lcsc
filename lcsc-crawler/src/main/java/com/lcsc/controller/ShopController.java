package com.lcsc.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lcsc.common.Result;
import com.lcsc.entity.Shop;
import com.lcsc.service.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 店铺管理控制器
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api/shops")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
public class ShopController {

    @Autowired
    private ShopService shopService;

    // 分页查询店铺
    @GetMapping("/page")
    public Result<Map<String, Object>> getShopPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String shopName,
            @RequestParam(required = false) String shippingTemplateId
    ) {
        IPage<Shop> result = shopService.getShopPage(current, size, shopName, shippingTemplateId);
        return Result.page(result.getRecords(), result.getTotal(), current.longValue(), size.longValue());
    }

    // 获取所有店铺列表
    @GetMapping("/list")
    public Result<List<Shop>> getShopList() {
        List<Shop> shops = shopService.getAllShopList();
        return Result.success(shops);
    }

    // 根据ID查询店铺
    @GetMapping("/{id}")
    public Result<Shop> getShopById(@PathVariable Long id) {
        Shop shop = shopService.getById(id);
        if (shop == null) {
            return Result.notFound("店铺不存在");
        }
        return Result.success(shop);
    }

    // 根据店铺名称查询店铺
    @GetMapping("/name/{shopName}")
    public Result<Shop> getShopByName(@PathVariable String shopName) {
        Shop shop = shopService.getByShopName(shopName);
        if (shop == null) {
            return Result.notFound("店铺不存在");
        }
        return Result.success(shop);
    }

    // 根据运费模板ID查询店铺
    @GetMapping("/template/{shippingTemplateId}")
    public Result<Shop> getShopByTemplateId(@PathVariable String shippingTemplateId) {
        Shop shop = shopService.getByShippingTemplateId(shippingTemplateId);
        if (shop == null) {
            return Result.notFound("店铺不存在");
        }
        return Result.success(shop);
    }

    // 新增店铺
    @PostMapping
    public Result<String> addShop(@RequestBody Shop shop) {
        try {
            boolean success = shopService.saveOrUpdateShop(shop);
            if (success) {
                return Result.success("店铺添加成功");
            } else {
                return Result.error("店铺添加失败");
            }
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // 更新店铺
    @PutMapping(value = "/{id}")
    public Result<String> updateShop(@PathVariable Long id, @RequestBody Shop shop) {
        shop.setId(id.intValue());
        try {
            boolean success = shopService.saveOrUpdateShop(shop);
            if (success) {
                return Result.success("店铺更新成功");
            } else {
                return Result.error("店铺更新失败");
            }
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // 删除店铺
    @DeleteMapping("/{id}")
    public Result<String> deleteShop(@PathVariable Long id) {
        boolean success = shopService.deleteShopById(id.intValue());
        if (success) {
            return Result.success("店铺删除成功");
        } else {
            return Result.error("店铺删除失败");
        }
    }

    // 批量删除店铺
    @DeleteMapping(value = "/batch")
    public Result<String> deleteShopBatch(@RequestBody List<Long> ids) {
        List<Integer> intIds = ids.stream().map(Long::intValue).toList();
        boolean success = shopService.deleteShopBatch(intIds);
        if (success) {
            return Result.success("批量删除成功，共删除" + ids.size() + "条记录");
        } else {
            return Result.error("批量删除失败");
        }
    }
}
