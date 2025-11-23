package com.lcsc.controller;

import com.lcsc.common.Result;
import com.lcsc.dto.AdvancedExportRequest;
import com.lcsc.dto.ExportTaskItem;
import com.lcsc.service.AdvancedExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 高级导出控制器 - 淘宝CSV格式
 */
@RestController
@RequestMapping("/api/export")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
public class AdvancedExportController {

    @Autowired
    private AdvancedExportService advancedExportService;

    /**
     * 添加产品到任务列表（批量添加模式）
     * @param requestBody 包含筛选条件和当前任务列表
     * @return 更新后的任务列表
     */
    @PostMapping("/add-task")
    public Result<List<ExportTaskItem>> addToTaskList(@RequestBody Map<String, Object> requestBody) {
        try {
            // 解析请求参数
            AdvancedExportRequest request = parseRequest(requestBody);

            // 手动转换 currentTasks (LinkedHashMap -> ExportTaskItem)
            List<ExportTaskItem> currentTasks = parseCurrentTasks(requestBody);

            // 添加到任务列表
            List<ExportTaskItem> updatedTasks = advancedExportService.addToTaskList(request, currentTasks);

            return Result.success(updatedTasks);
        } catch (Exception e) {
            return Result.error("添加任务失败: " + e.getMessage());
        }
    }

    /**
     * 导出任务列表为淘宝CSV格式
     * @param tasks 任务列表
     * @return CSV文件字节流
     */
    @PostMapping("/export-taobao-csv")
    public ResponseEntity<byte[]> exportTaobaoCsv(@RequestBody List<ExportTaskItem> tasks) {
        try {
            // 生成淘宝CSV
            byte[] csvBytes = advancedExportService.generateTaobaoCsv(tasks);

            // 生成文件名
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "淘宝导入_" + dateStr + ".csv";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename);
            headers.setContentLength(csvBytes.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvBytes);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 从Map解析currentTasks列表（处理LinkedHashMap -> ExportTaskItem转换）
     */
    @SuppressWarnings("unchecked")
    private List<ExportTaskItem> parseCurrentTasks(Map<String, Object> requestBody) {
        if (!requestBody.containsKey("currentTasks")) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> taskMaps = (List<Map<String, Object>>) requestBody.get("currentTasks");

        return taskMaps.stream().map(taskMap -> {
            ExportTaskItem task = new ExportTaskItem();

            if (taskMap.containsKey("productCode")) {
                task.setProductCode((String) taskMap.get("productCode"));
            }
            if (taskMap.containsKey("model")) {
                task.setModel((String) taskMap.get("model"));
            }
            if (taskMap.containsKey("brand")) {
                task.setBrand((String) taskMap.get("brand"));
            }
            if (taskMap.containsKey("shopId")) {
                task.setShopId(((Number) taskMap.get("shopId")).intValue());
            }
            if (taskMap.containsKey("shopName")) {
                task.setShopName((String) taskMap.get("shopName"));
            }
            if (taskMap.containsKey("discounts")) {
                List<Number> discountNumbers = (List<Number>) taskMap.get("discounts");
                List<BigDecimal> discounts = discountNumbers.stream()
                        .map(n -> new BigDecimal(n.toString()))
                        .collect(Collectors.toList());
                task.setDiscounts(discounts);
            }
            if (taskMap.containsKey("addedAt")) {
                task.setAddedAt(((Number) taskMap.get("addedAt")).longValue());
            }

            return task;
        }).collect(Collectors.toList());
    }

    /**
     * 从Map解析AdvancedExportRequest对象
     */
    private AdvancedExportRequest parseRequest(Map<String, Object> requestBody) {
        AdvancedExportRequest request = new AdvancedExportRequest();

        if (requestBody.containsKey("shopId")) {
            request.setShopId(((Number) requestBody.get("shopId")).intValue());
        }

        if (requestBody.containsKey("categoryLevel1Id")) {
            request.setCategoryLevel1Id(((Number) requestBody.get("categoryLevel1Id")).intValue());
        }

        if (requestBody.containsKey("categoryLevel2Id")) {
            request.setCategoryLevel2Id(((Number) requestBody.get("categoryLevel2Id")).intValue());
        }

        if (requestBody.containsKey("categoryLevel3Id")) {
            request.setCategoryLevel3Id(((Number) requestBody.get("categoryLevel3Id")).intValue());
        }

        if (requestBody.containsKey("brand")) {
            request.setBrand((String) requestBody.get("brand"));
        }

        if (requestBody.containsKey("hasImage")) {
            request.setHasImage((Boolean) requestBody.get("hasImage"));
        }

        if (requestBody.containsKey("stockMin")) {
            request.setStockMin(((Number) requestBody.get("stockMin")).intValue());
        }

        if (requestBody.containsKey("stockMax")) {
            request.setStockMax(((Number) requestBody.get("stockMax")).intValue());
        }

        if (requestBody.containsKey("discounts")) {
            @SuppressWarnings("unchecked")
            List<Number> discountsList = (List<Number>) requestBody.get("discounts");
            request.setDiscounts(discountsList.stream()
                    .map(n -> new java.math.BigDecimal(n.toString()))
                    .toList());
        }

        return request;
    }
}
