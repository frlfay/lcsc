package com.lcsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lcsc.entity.Shop;
import com.lcsc.mapper.ShopMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 店铺服务层
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Service
public class ShopService extends ServiceImpl<ShopMapper, Shop> {

    /**
     * 分页查询店铺
     */
    public IPage<Shop> getShopPage(int current, int size, String shopName, String shippingTemplateId) {
        Page<Shop> page = new Page<>(current, size);
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        
        if (shopName != null && !shopName.trim().isEmpty()) {
            wrapper.like(Shop::getShopName, shopName);
        }
        if (shippingTemplateId != null && !shippingTemplateId.trim().isEmpty()) {
            wrapper.like(Shop::getShippingTemplateId, shippingTemplateId);
        }
        
        wrapper.orderByAsc(Shop::getShopName);
        
        return page(page, wrapper);
    }

    /**
     * 获取所有店铺列表
     */
    public List<Shop> getAllShopList() {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Shop::getShopName);
        return list(wrapper);
    }

    /**
     * 根据店铺名称查询店铺
     */
    public Shop getByShopName(String shopName) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Shop::getShopName, shopName);
        return getOne(wrapper);
    }

    /**
     * 根据运费模板ID查询店铺
     */
    public Shop getByShippingTemplateId(String shippingTemplateId) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Shop::getShippingTemplateId, shippingTemplateId);
        return getOne(wrapper);
    }

    /**
     * 检查店铺名称是否存在
     */
    public boolean existsByShopName(String shopName) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Shop::getShopName, shopName);
        return count(wrapper) > 0;
    }

    /**
     * 检查店铺名称是否存在（排除指定ID）
     */
    public boolean existsByShopName(String shopName, Integer excludeId) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Shop::getShopName, shopName);
        if (excludeId != null) {
            wrapper.ne(Shop::getId, excludeId);
        }
        return count(wrapper) > 0;
    }

    /**
     * 检查运费模板ID是否存在
     */
    public boolean existsByShippingTemplateId(String shippingTemplateId) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Shop::getShippingTemplateId, shippingTemplateId);
        return count(wrapper) > 0;
    }

    /**
     * 检查运费模板ID是否存在（排除指定ID）
     */
    public boolean existsByShippingTemplateId(String shippingTemplateId, Integer excludeId) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Shop::getShippingTemplateId, shippingTemplateId);
        if (excludeId != null) {
            wrapper.ne(Shop::getId, excludeId);
        }
        return count(wrapper) > 0;
    }

    /**
     * 保存或更新店铺
     */
    public boolean saveOrUpdateShop(Shop shop) {
        if (shop.getId() != null) {
            // 更新时检查名称和运费模板ID是否重复
            if (existsByShopName(shop.getShopName(), shop.getId())) {
                throw new RuntimeException("店铺名称已存在");
            }
            if (existsByShippingTemplateId(shop.getShippingTemplateId(), shop.getId())) {
                throw new RuntimeException("运费模板ID已存在");
            }
        } else {
            // 新增时检查名称和运费模板ID是否重复
            if (existsByShopName(shop.getShopName())) {
                throw new RuntimeException("店铺名称已存在");
            }
            if (existsByShippingTemplateId(shop.getShippingTemplateId())) {
                throw new RuntimeException("运费模板ID已存在");
            }
        }
        return saveOrUpdate(shop);
    }

    /**
     * 删除店铺
     */
    public boolean deleteShopById(Integer id) {
        // TODO: 检查是否有关联的图片链接，如果有则不允许删除
        return removeById(id);
    }

    /**
     * 批量删除店铺
     */
    public boolean deleteShopBatch(List<Integer> ids) {
        // TODO: 检查是否有关联的图片链接，如果有则不允许删除
        return removeByIds(ids);
    }

    /**
     * 根据关键词搜索店铺
     */
    public List<Shop> searchShops(String keyword) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(Shop::getShopName, keyword)
                            .or()
                            .like(Shop::getShippingTemplateId, keyword));
        }
        wrapper.orderByAsc(Shop::getShopName);
        return list(wrapper);
    }

    /**
     * 批量保存或更新店铺
     */
    public boolean saveOrUpdateBatch(List<Shop> shops) {
        // 验证每个店铺的唯一性
        for (Shop shop : shops) {
            if (shop.getId() != null) {
                if (existsByShopName(shop.getShopName(), shop.getId())) {
                    throw new RuntimeException("店铺名称已存在: " + shop.getShopName());
                }
                if (existsByShippingTemplateId(shop.getShippingTemplateId(), shop.getId())) {
                    throw new RuntimeException("运费模板ID已存在: " + shop.getShippingTemplateId());
                }
            } else {
                if (existsByShopName(shop.getShopName())) {
                    throw new RuntimeException("店铺名称已存在: " + shop.getShopName());
                }
                if (existsByShippingTemplateId(shop.getShippingTemplateId())) {
                    throw new RuntimeException("运费模板ID已存在: " + shop.getShippingTemplateId());
                }
            }
        }
        return saveOrUpdateBatch(shops);
    }

    /**
     * 获取店铺统计信息
     */
    public long getTotalShopCount() {
        return count();
    }

    /**
     * 根据店铺名称模糊查询
     */
    public List<Shop> getShopsByNameLike(String shopName) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        if (shopName != null && !shopName.trim().isEmpty()) {
            wrapper.like(Shop::getShopName, shopName);
        }
        wrapper.orderByAsc(Shop::getShopName);
        return list(wrapper);
    }
}