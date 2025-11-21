package com.lcsc.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lcsc.common.Result;
import com.lcsc.entity.CategoryLevel1Code;
import com.lcsc.entity.CategoryLevel2Code;
import com.lcsc.entity.CategoryLevel3Code;
import com.lcsc.service.CategoryLevel1CodeService;
import com.lcsc.service.CategoryLevel2CodeService;
import com.lcsc.service.CategoryLevel3CodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 分类管理控制器
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
public class CategoryController {

    @Autowired
    private CategoryLevel1CodeService categoryLevel1CodeService;

    @Autowired
    private CategoryLevel2CodeService categoryLevel2CodeService;

    @Autowired
    private CategoryLevel3CodeService categoryLevel3CodeService;

    // ========== 一级分类 API ==========

    // 分页查询一级分类
    @GetMapping("/level1/page")
    public Result<Map<String, Object>> getCategoryLevel1Page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) String categoryCode
    ) {
        IPage<CategoryLevel1Code> result = categoryLevel1CodeService.getCategoryLevel1Page(current, size, categoryName, categoryCode);
        return Result.page(result.getRecords(), result.getTotal(), current.longValue(), size.longValue());
    }

    // 获取所有一级分类列表
    @GetMapping("/level1/list")
    public Result<List<CategoryLevel1Code>> getCategoryLevel1List() {
        List<CategoryLevel1Code> categories = categoryLevel1CodeService.getAllCategoryLevel1List();
        return Result.success(categories);
    }

    // 根据ID查询一级分类
    @GetMapping("/level1/{id}")
    public Result<CategoryLevel1Code> getCategoryLevel1ById(@PathVariable Integer id) {
        CategoryLevel1Code category = categoryLevel1CodeService.getById(id);
        if (category == null) {
            return Result.notFound("一级分类不存在");
        }
        return Result.success(category);
    }

    // 新增一级分类
    @PostMapping(value = "/level1")
    public Result<String> addCategoryLevel1(@RequestBody CategoryLevel1Code category) {
        try {
            boolean success = categoryLevel1CodeService.saveOrUpdateCategory(category);
            if (success) {
                return Result.success("一级分类添加成功");
            } else {
                return Result.error("一级分类添加失败");
            }
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // 更新一级分类
    @PutMapping(value = "/level1/{id}")
    public Result<String> updateCategoryLevel1(@PathVariable Integer id, @RequestBody CategoryLevel1Code category) {
        category.setId(id);
        try {
            boolean success = categoryLevel1CodeService.saveOrUpdateCategory(category);
            if (success) {
                return Result.success("一级分类更新成功");
            } else {
                return Result.error("一级分类更新失败");
            }
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // 删除一级分类
    @DeleteMapping("/level1/{id}")
    public Result<String> deleteCategoryLevel1(@PathVariable Integer id) {
        // 检查是否有关联的二级分类
        long count = categoryLevel2CodeService.countByLevel1Id(id);
        if (count > 0) {
            return Result.error("该一级分类下还有二级分类，无法删除");
        }
        
        boolean success = categoryLevel1CodeService.deleteCategoryById(id);
        if (success) {
            return Result.success("一级分类删除成功");
        } else {
            return Result.error("一级分类删除失败");
        }
    }

    // 批量删除一级分类
    @DeleteMapping(value = "/level1/batch")
    public Result<String> deleteCategoryLevel1Batch(@RequestBody List<Long> ids) {
        // 检查是否有关联的二级分类
        for (Long id : ids) {
            long count = categoryLevel2CodeService.countByLevel1Id(id.intValue());
            if (count > 0) {
                return Result.error("部分一级分类下还有二级分类，无法删除");
            }
        }
        
        List<Integer> intIds = ids.stream().map(Long::intValue).toList();
        boolean success = categoryLevel1CodeService.deleteCategoryBatch(intIds);
        if (success) {
            return Result.success("批量删除成功，共删除" + ids.size() + "条记录");
        } else {
            return Result.error("批量删除失败");
        }
    }

    // ========== 二级分类 API ==========

    // 分页查询二级分类
    @GetMapping("/level2/page")
    public Result<Map<String, Object>> getCategoryLevel2Page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) Integer categoryLevel1Id
    ) {
        IPage<CategoryLevel2Code> result = categoryLevel2CodeService.getCategoryLevel2Page(current, size, categoryName, categoryLevel1Id);
        return Result.page(result.getRecords(), result.getTotal(), current.longValue(), size.longValue());
    }

    // 根据一级分类ID查询二级分类列表
    @GetMapping("/level2/list/{categoryLevel1Id}")
    public Result<List<CategoryLevel2Code>> getCategoryLevel2ListByLevel1Id(@PathVariable Integer categoryLevel1Id) {
        List<CategoryLevel2Code> categories = categoryLevel2CodeService.getCategoryLevel2ListByLevel1Id(categoryLevel1Id);
        return Result.success(categories);
    }

    // 获取所有二级分类列表
    @GetMapping("/level2/list")
    public Result<List<CategoryLevel2Code>> getAllCategoryLevel2List() {
        List<CategoryLevel2Code> categories = categoryLevel2CodeService.getAllCategoryLevel2List();
        return Result.success(categories);
    }

    // 根据ID查询二级分类
    @GetMapping("/level2/{id}")
    public Result<CategoryLevel2Code> getCategoryLevel2ById(@PathVariable Integer id) {
        CategoryLevel2Code category = categoryLevel2CodeService.getById(id);
        if (category == null) {
            return Result.notFound("二级分类不存在");
        }
        return Result.success(category);
    }

    // 新增二级分类
    @PostMapping(value = "/level2")
    public Result<String> addCategoryLevel2(@RequestBody CategoryLevel2Code category) {
        try {
            boolean success = categoryLevel2CodeService.saveOrUpdateCategory(category);
            if (success) {
                return Result.success("二级分类添加成功");
            } else {
                return Result.error("二级分类添加失败");
            }
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // 更新二级分类
    @PutMapping(value = "/level2/{id}")
    public Result<String> updateCategoryLevel2(@PathVariable Integer id, @RequestBody CategoryLevel2Code category) {
        category.setId(id);
        try {
            boolean success = categoryLevel2CodeService.saveOrUpdateCategory(category);
            if (success) {
                return Result.success("二级分类更新成功");
            } else {
                return Result.error("二级分类更新失败");
            }
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // 删除二级分类
    @DeleteMapping("/level2/{id}")
    public Result<String> deleteCategoryLevel2(@PathVariable Integer id) {
        boolean success = categoryLevel2CodeService.deleteCategoryById(id);
        if (success) {
            return Result.success("二级分类删除成功");
        } else {
            return Result.error("二级分类删除失败");
        }
    }

    // 批量删除二级分类
    @DeleteMapping(value = "/level2/batch")
    public Result<String> deleteCategoryLevel2Batch(@RequestBody List<Long> ids) {
        List<Integer> intIds = ids.stream().map(Long::intValue).toList();
        boolean success = categoryLevel2CodeService.deleteCategoryBatch(intIds);
        if (success) {
            return Result.success("批量删除成功，共删除" + ids.size() + "条记录");
        } else {
            return Result.error("批量删除失败");
        }
    }

    // 获取店铺分类码
    @GetMapping("/level2/{categoryLevel2Id}/shop/{shopId}/code")
    public Result<String> getShopCategoryCode(@PathVariable Integer categoryLevel2Id, @PathVariable Integer shopId) {
        String categoryCode = categoryLevel2CodeService.getShopCategoryCode(categoryLevel2Id, shopId);
        return Result.success(categoryCode);
    }

    // 更新店铺分类码
    @PutMapping(value = "/level2/{categoryLevel2Id}/shop/{shopId}/code")
    public Result<String> updateShopCategoryCode(
            @PathVariable Integer categoryLevel2Id,
            @PathVariable Integer shopId,
            @RequestBody Map<String, String> request
    ) {
        String categoryCode = request.get("categoryCode");
        if (categoryCode == null || categoryCode.trim().isEmpty()) {
            return Result.paramError("分类码不能为空");
        }

        boolean success = categoryLevel2CodeService.updateShopCategoryCode(
                categoryLevel2Id, shopId, categoryCode);
        if (success) {
            return Result.success("店铺分类码更新成功");
        } else {
            return Result.error("店铺分类码更新失败");
        }
    }

    // ========== 三级分类 API ==========

    // 根据二级分类ID查询三级分类列表
    @GetMapping("/level3/list/{categoryLevel2Id}")
    public Result<List<CategoryLevel3Code>> getCategoryLevel3ListByLevel2Id(@PathVariable Integer categoryLevel2Id) {
        List<CategoryLevel3Code> categories = categoryLevel3CodeService.getByLevel2Id(categoryLevel2Id);
        return Result.success(categories);
    }

    // 获取所有三级分类列表
    @GetMapping("/level3/list")
    public Result<List<CategoryLevel3Code>> getAllCategoryLevel3List() {
        List<CategoryLevel3Code> categories = categoryLevel3CodeService.getAllCategoryLevel3List();
        return Result.success(categories);
    }

    // 根据ID查询三级分类
    @GetMapping("/level3/{id}")
    public Result<CategoryLevel3Code> getCategoryLevel3ById(@PathVariable Integer id) {
        CategoryLevel3Code category = categoryLevel3CodeService.getById(id);
        if (category == null) {
            return Result.notFound("三级分类不存在");
        }
        return Result.success(category);
    }

    // 新增三级分类
    @PostMapping(value = "/level3")
    public Result<String> addCategoryLevel3(@RequestBody CategoryLevel3Code category) {
        try {
            boolean success = categoryLevel3CodeService.saveOrUpdate(category);
            if (success) {
                return Result.success("三级分类添加成功");
            } else {
                return Result.error("三级分类添加失败");
            }
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // 更新三级分类
    @PutMapping(value = "/level3/{id}")
    public Result<String> updateCategoryLevel3(@PathVariable Integer id, @RequestBody CategoryLevel3Code category) {
        category.setId(id);
        try {
            boolean success = categoryLevel3CodeService.saveOrUpdate(category);
            if (success) {
                return Result.success("三级分类更新成功");
            } else {
                return Result.error("三级分类更新失败");
            }
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // 删除三级分类
    @DeleteMapping("/level3/{id}")
    public Result<String> deleteCategoryLevel3(@PathVariable Integer id) {
        boolean success = categoryLevel3CodeService.deleteCategoryById(id);
        if (success) {
            return Result.success("三级分类删除成功");
        } else {
            return Result.error("三级分类删除失败");
        }
    }

    // 批量删除三级分类
    @DeleteMapping(value = "/level3/batch")
    public Result<String> deleteCategoryLevel3Batch(@RequestBody List<Long> ids) {
        List<Integer> intIds = ids.stream().map(Long::intValue).toList();
        boolean success = categoryLevel3CodeService.deleteCategoryBatch(intIds);
        if (success) {
            return Result.success("批量删除成功，共删除" + ids.size() + "条记录");
        } else {
            return Result.error("批量删除失败");
        }
    }
}