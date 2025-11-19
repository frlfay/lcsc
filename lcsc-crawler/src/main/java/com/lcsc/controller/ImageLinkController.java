package com.lcsc.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lcsc.common.Result;
import com.lcsc.entity.ImageLink;
import com.lcsc.service.ImageLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 图片链接管理控制器
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api/image-links")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
public class ImageLinkController {

    @Autowired
    private ImageLinkService imageLinkService;

    // 分页查询图片链接
    @GetMapping("/page")
    public Result<Map<String, Object>> getImageLinkPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String imageName,
            @RequestParam(required = false) Integer shopId
    ) {
        IPage<ImageLink> result = imageLinkService.getImageLinkPage(current, size, imageName, shopId);
        return Result.page(result.getRecords(), result.getTotal(), current.longValue(), size.longValue());
    }

    // 根据店铺ID查询图片链接列表
    @GetMapping("/shop/{shopId}")
    public Result<List<ImageLink>> getImageLinkListByShopId(@PathVariable Long shopId) {
        List<ImageLink> imageLinks = imageLinkService.getImageLinksByShopId(shopId.intValue());
        return Result.success(imageLinks);
    }

    // 根据图片名称查询所有店铺的链接
    @GetMapping("/image/{imageName}")
    public Result<List<ImageLink>> getImageLinkListByImageName(@PathVariable String imageName) {
        List<ImageLink> imageLinks = imageLinkService.getImageLinksByImageName(imageName);
        return Result.success(imageLinks);
    }

    // 根据ID查询图片链接
    @GetMapping("/{id}")
    public Result<ImageLink> getImageLinkById(@PathVariable Long id) {
        ImageLink imageLink = imageLinkService.getById(id);
        if (imageLink == null) {
            return Result.notFound("图片链接不存在");
        }
        return Result.success(imageLink);
    }

    // 根据图片名称和店铺ID查询图片链接
    @GetMapping("/image/{imageName}/shop/{shopId}")
    public Result<ImageLink> getImageLinkByImageNameAndShopId(
            @PathVariable String imageName, 
            @PathVariable Long shopId
    ) {
        ImageLink imageLink = imageLinkService.getByImageNameAndShopId(imageName, shopId.intValue());
        if (imageLink == null) {
            return Result.notFound("图片链接不存在");
        }
        return Result.success(imageLink);
    }

    // 新增图片链接
    @PostMapping(consumes = "application/json", produces = "application/json")
    public Result<String> addImageLink(@RequestBody ImageLink imageLink) {
        try {
            boolean success = imageLinkService.saveOrUpdateImageLink(imageLink);
            if (success) {
                return Result.success("图片链接添加成功");
            } else {
                return Result.error("图片链接添加失败");
            }
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // 更新图片链接
    @PutMapping(value = "/{id}")
    public Result<String> updateImageLink(@PathVariable Long id, @RequestBody ImageLink imageLink) {
        imageLink.setId(id.intValue());
        try {
            boolean success = imageLinkService.saveOrUpdateImageLink(imageLink);
            if (success) {
                return Result.success("图片链接更新成功");
            } else {
                return Result.error("图片链接更新失败");
            }
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // 删除图片链接
    @DeleteMapping("/{id}")
    public Result<String> deleteImageLink(@PathVariable Long id) {
        boolean success = imageLinkService.deleteImageLinkById(id.intValue());
        if (success) {
            return Result.success("图片链接删除成功");
        } else {
            return Result.error("图片链接删除失败");
        }
    }

    // 批量删除图片链接
    @DeleteMapping(value = "/batch")
    public Result<String> deleteImageLinkBatch(@RequestBody List<Long> ids) {
        List<Integer> intIds = ids.stream().map(Long::intValue).toList();
        boolean success = imageLinkService.deleteImageLinkBatch(intIds);
        if (success) {
            return Result.success("批量删除成功，共删除" + ids.size() + "条记录");
        } else {
            return Result.error("批量删除失败");
        }
    }

    // 批量保存或更新图片链接
    @PostMapping(value = "/batch")
    public Result<String> saveOrUpdateImageLinkBatch(@RequestBody List<ImageLink> imageLinks) {
        try {
            boolean success = imageLinkService.saveOrUpdateBatch(imageLinks);
            if (success) {
                return Result.success("批量保存成功，共处理" + imageLinks.size() + "条记录");
            } else {
                return Result.error("批量保存失败");
            }
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // 统计指定店铺的图片数量
    @GetMapping("/shop/{shopId}/count")
    public Result<Long> countImageLinkByShopId(@PathVariable Long shopId) {
        long count = imageLinkService.countByShopId(shopId.intValue());
        return Result.success(count);
    }

    // 删除指定店铺的所有图片链接
    @DeleteMapping("/shop/{shopId}")
    public Result<String> deleteImageLinkByShopId(@PathVariable Long shopId) {
        boolean success = imageLinkService.deleteByShopId(shopId.intValue());
        if (success) {
            return Result.success("店铺图片链接删除成功");
        } else {
            return Result.error("店铺图片链接删除失败");
        }
    }

    // 删除指定图片名称的所有链接
    @DeleteMapping("/image/{imageName}")
    public Result<String> deleteImageLinkByImageName(@PathVariable String imageName) {
        boolean success = imageLinkService.deleteByImageName(imageName);
        if (success) {
            return Result.success("图片链接删除成功");
        } else {
            return Result.error("图片链接删除失败");
        }
    }

    // 获取所有图片链接列表
    @GetMapping("/list")
    public Result<List<ImageLink>> getAllImageLinks() {
        List<ImageLink> imageLinks = imageLinkService.getAllImageLinks();
        return Result.success(imageLinks);
    }

    // 根据关键词搜索图片链接
    @GetMapping("/search")
    public Result<List<ImageLink>> searchImageLinks(@RequestParam String keyword) {
        List<ImageLink> imageLinks = imageLinkService.searchImageLinks(keyword);
        return Result.success(imageLinks);
    }

    // 根据图片名称前缀查询
    @GetMapping("/prefix/{prefix}")
    public Result<List<ImageLink>> getImageLinksByPrefix(@PathVariable String prefix) {
        List<ImageLink> imageLinks = imageLinkService.getImageLinksByImageNamePrefix(prefix);
        return Result.success(imageLinks);
    }

    // 获取图片链接统计信息
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getImageLinkStatistics() {
        long totalCount = imageLinkService.getTotalImageLinkCount();
        return Result.success(Map.of("totalImageLinks", totalCount));
    }
}