package com.lcsc.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lcsc.common.Result;
import com.lcsc.entity.Product;
import com.lcsc.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 产品管理控制器
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:5177", "http://127.0.0.1:5177"})
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private com.lcsc.service.crawler.DataExportService dataExportService;

    @Value("${crawler.storage.base-path}")
    private String storageBasePath;

    // --- 全新的资源浏览器 API ---

    @GetMapping("/resources/folders")
    public Result<Map<String, Object>> getResourceFolders(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            Path basePath = Paths.get(storageBasePath);
            if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
                return Result.page(new ArrayList<>(), 0L, (long)current, (long)size);
            }

            List<Map<String, Object>> folderInfoList;
            try (Stream<Path> stream = Files.list(basePath)) {
                folderInfoList = stream
                    .filter(Files::isDirectory)
                    .map(productDir -> {
                        String productCode = productDir.getFileName().toString();
                        Map<String, Object> info = new HashMap<>();
                        info.put("productCode", productCode);
                        try {
                            info.put("imageCount", countFiles(productDir.resolve("images")));
                            info.put("pdfCount", countFiles(productDir.resolve("pdfs")));
                            info.put("lastModified", Files.getLastModifiedTime(productDir).toMillis());
                        } catch (IOException e) {
                            info.put("imageCount", 0);
                            info.put("pdfCount", 0);
                            info.put("lastModified", 0);
                        }
                        return info;
                    })
                    .sorted(Comparator.comparing(m -> (long)m.get("lastModified"), Comparator.reverseOrder()))
                    .collect(Collectors.toList());
            }

            long total = folderInfoList.size();
            int fromIndex = (current - 1) * size;
            int toIndex = (int)Math.min(fromIndex + size, total);

            if (fromIndex >= total) {
                return Result.page(new ArrayList<>(), total, (long)current, (long)size);
            }

            List<Map<String, Object>> paginatedList = folderInfoList.subList(fromIndex, toIndex);

            return Result.page(paginatedList, total, (long)current, (long)size);

        } catch (IOException e) {
            return Result.error("获取资源文件夹失败: " + e.getMessage());
        }
    }

    private long countFiles(Path directory) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile).count();
        }
    }


    // --- 产品管理 API ---

    @GetMapping("/products/page")
    public Result<Map<String, Object>> getProductPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String packageName,
            @RequestParam(required = false) Integer categoryLevel1Id,
            @RequestParam(required = false) Integer categoryLevel2Id,
            @RequestParam(required = false) Boolean hasStock
    ) {
        IPage<Product> result = productService.getProductPage(current, size, productCode, brand, 
                                                            model, packageName, categoryLevel1Id, 
                                                            categoryLevel2Id, hasStock);
        return Result.page(result.getRecords(), result.getTotal(), current.longValue(), size.longValue());
    }

    @GetMapping("/products/{id}")
    public Result<Product> getProductById(@PathVariable Long id) {
        Product product = productService.getById(id);
        if (product == null) {
            return Result.notFound("产品不存在");
        }
        return Result.success(product);
    }

    @GetMapping("/products/code/{productCode}")
    public Result<Product> getProductByCode(@PathVariable String productCode) {
        Product product = productService.getByProductCode(productCode);
        if (product == null) {
            return Result.notFound("产品不存在");
        }
        return Result.success(product);
    }

    @PostMapping("/products")
    public Result<String> addProduct(@RequestBody Product product) {
        boolean success = productService.save(product);
        if (success) {
            return Result.success("产品添加成功");
        } else {
            return Result.error("产品添加失败");
        }
    }

    @PutMapping("/products/{id}")
    public Result<String> updateProduct(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id.intValue());
        boolean success = productService.updateById(product);
        if (success) {
            return Result.success("产品更新成功");
        } else {
            return Result.error("产品更新失败");
        }
    }

    @DeleteMapping("/products/{id}")
    public Result<String> deleteProduct(@PathVariable Long id) {
        boolean success = productService.removeById(id);
        if (success) {
            return Result.success("产品删除成功");
        } else {
            return Result.error("产品删除失败");
        }
    }

    @DeleteMapping("/products/batch")
    public Result<String> deleteProductBatch(@RequestBody List<Long> ids) {
        boolean success = productService.removeByIds(ids);
        if (success) {
            return Result.success("批量删除成功，共删除" + ids.size() + "条记录");
        } else {
            return Result.error("批量删除失败");
        }
    }

    @GetMapping("/products/brand/{brand}")
    public Result<List<Product>> getProductListByBrand(@PathVariable String brand) {
        IPage<Product> result = productService.getProductPage(1, 1000, null, brand);
        return Result.success(result.getRecords());
    }

    @GetMapping("/products/category")
    public Result<List<Product>> getProductListByCategory(
            @RequestParam(required = false) Integer categoryLevel1Id,
            @RequestParam(required = false) Integer categoryLevel2Id
    ) {
        List<Product> products = productService.getProductListByCategory(categoryLevel1Id, categoryLevel2Id);
        return Result.success(products);
    }

    @GetMapping("/products/statistics")
    public Result<Map<String, Object>> getProductStatistics() {
        Map<String, Object> statistics = productService.getProductStatistics();
        return Result.success(statistics);
    }

    @GetMapping("/products/{productCode}/resources")
    public Result<Map<String, Object>> getProductResources(@PathVariable String productCode) {
        try {
            Map<String, Object> resources = new HashMap<>();
            List<Map<String, Object>> allFiles = new ArrayList<>();
            Path productPath = Paths.get(storageBasePath, productCode);

            addFilesToList(allFiles, productPath.resolve("images"), "image");
            addFilesToList(allFiles, productPath.resolve("pdfs"), "pdf");

            Map<String, List<Map<String, Object>>> groupedFiles = allFiles.stream()
                .collect(Collectors.groupingBy(file -> (String) file.get("category")));

            resources.put("all", allFiles);
            resources.put("images", groupedFiles.getOrDefault("image", new ArrayList<>()));
            resources.put("pdfs", groupedFiles.getOrDefault("pdf", new ArrayList<>()));
            resources.put("total", allFiles.size());

            return Result.success(resources);

        } catch (Exception e) {
            return Result.error("获取产品资源失败: " + e.getMessage());
        }
    }

    private void addFilesToList(List<Map<String, Object>> allFiles, Path directory, String category) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    String filename = file.getFileName().toString();
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("filename", filename);
                    fileInfo.put("size", Files.size(file));
                    fileInfo.put("lastModified", Files.getLastModifiedTime(file).toMillis());
                    fileInfo.put("url", "/api/resources/" + file.getParent().getParent().getFileName().toString() + "/" + category + "/" + filename);
                    fileInfo.put("category", category);
                    fileInfo.put("type", category.equals("image") ? "image" : "datasheet");
                    allFiles.add(fileInfo);
                } catch (IOException e) {
                    // Ignore individual file errors
                }
            });
        }
    }

    @GetMapping("/resources/{productCode}/{type}/{filename}")
    public ResponseEntity<Resource> getProductResource(@PathVariable String productCode, @PathVariable String type, @PathVariable String filename) {
        try {
            if (filename.contains("..") || productCode.contains("..")) {
                return ResponseEntity.badRequest().build();
            }

            // 将单数形式转换为复数形式，匹配实际目录结构
            String directoryType = type.equals("image") ? "images" : "pdfs";
            Path filePath = Paths.get(storageBasePath, productCode, directoryType, filename);
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath.toFile());
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // --- 产品导出 API ---

    /**
     * 导出所有产品到Excel
     */
    @GetMapping("/products/export/excel/all")
    public CompletableFuture<Result<Map<String, Object>>> exportAllProductsToExcel() {
        return dataExportService.exportAllProductsToExcel()
            .thenApply(result -> {
                if (result.isSuccess()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("filename", Paths.get(result.getFilePath()).getFileName().toString());
                    data.put("recordCount", result.getRecordCount());
                    data.put("message", result.getMessage());
                    return Result.success(data);
                } else {
                    return Result.error(result.getMessage());
                }
            });
    }

    /**
     * 根据搜索条件导出产品到Excel
     */
    @PostMapping("/products/export/excel")
    public CompletableFuture<Result<Map<String, Object>>> exportProductsToExcel(
            @RequestBody Map<String, Object> params) {

        Integer categoryLevel1Id = params.get("categoryLevel1Id") != null ?
                                    Integer.valueOf(params.get("categoryLevel1Id").toString()) : null;
        Integer categoryLevel2Id = params.get("categoryLevel2Id") != null ?
                                    Integer.valueOf(params.get("categoryLevel2Id").toString()) : null;
        String brand = (String) params.get("brand");
        String productCode = (String) params.get("productCode");
        String model = (String) params.get("model");
        Boolean hasStock = params.get("hasStock") != null ? Boolean.valueOf(params.get("hasStock").toString()) : null;

        return dataExportService.exportProductsToExcel(categoryLevel1Id, categoryLevel2Id, brand, productCode, model, hasStock)
            .thenApply(result -> {
                if (result.isSuccess()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("filename", Paths.get(result.getFilePath()).getFileName().toString());
                    data.put("recordCount", result.getRecordCount());
                    data.put("message", result.getMessage());
                    return Result.success(data);
                } else {
                    return Result.error(result.getMessage());
                }
            });
    }

    /**
     * 导出产品到CSV
     */
    @PostMapping("/products/export/csv")
    public CompletableFuture<Result<Map<String, Object>>> exportProductsToCSV(
            @RequestBody Map<String, Object> params) {

        Integer categoryLevel1Id = params.get("categoryLevel1Id") != null ?
                                    Integer.valueOf(params.get("categoryLevel1Id").toString()) : null;
        Integer categoryLevel2Id = params.get("categoryLevel2Id") != null ?
                                    Integer.valueOf(params.get("categoryLevel2Id").toString()) : null;
        String brand = (String) params.get("brand");
        String productCode = (String) params.get("productCode");
        String model = (String) params.get("model");
        Boolean hasStock = params.get("hasStock") != null ? Boolean.valueOf(params.get("hasStock").toString()) : null;

        return dataExportService.exportProductsToCSV(categoryLevel1Id, categoryLevel2Id, brand, productCode, model, hasStock)
            .thenApply(result -> {
                if (result.isSuccess()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("filename", Paths.get(result.getFilePath()).getFileName().toString());
                    data.put("recordCount", result.getRecordCount());
                    data.put("message", result.getMessage());
                    return Result.success(data);
                } else {
                    return Result.error(result.getMessage());
                }
            });
    }

    /**
     * 下载导出的文件
     */
    @GetMapping("/products/export/download/{filename}")
    public ResponseEntity<Resource> downloadExportFile(@PathVariable String filename) {
        try {
            // 安全检查：防止路径穿越
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }

            Path filePath = Paths.get(storageBasePath).getParent().resolve("exports").resolve(filename);

            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath.toFile());
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8) + "\"")
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 按分类ID列表导出产品
     */
    @PostMapping("/products/export/by-categories")
    public CompletableFuture<Result<Map<String, Object>>> exportProductsByCategories(
            @RequestBody Map<String, Object> params) {

        List<Integer> categoryIds = (List<Integer>) params.get("categoryIds");
        String format = (String) params.getOrDefault("format", "excel");

        if (categoryIds == null || categoryIds.isEmpty()) {
            return CompletableFuture.completedFuture(
                Result.error("分类ID列表不能为空")
            );
        }

        return dataExportService.exportProductsByCategories(categoryIds, format)
            .thenApply(result -> {
                if (result.isSuccess()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("filename", Paths.get(result.getFilePath()).getFileName().toString());
                    data.put("recordCount", result.getRecordCount());
                    data.put("message", result.getMessage());
                    return Result.success(data);
                } else {
                    return Result.error(result.getMessage());
                }
            });
    }
}
