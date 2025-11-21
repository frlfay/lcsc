package com.lcsc.controller;

import com.lcsc.common.Result;
import com.lcsc.dto.AdvancedExportRequest;
import com.lcsc.service.AdvancedExportService;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 高级导出控制器
 */
@RestController
@RequestMapping("/api/export")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
public class AdvancedExportController {

    @Autowired
    private AdvancedExportService advancedExportService;

    /**
     * 获取导出预览信息
     */
    @PostMapping("/preview")
    public Result<Map<String, Object>> getExportPreview(@RequestBody AdvancedExportRequest request) {
        try {
            Map<String, Object> preview = advancedExportService.getExportPreview(request);
            return Result.success(preview);
        } catch (Exception e) {
            return Result.error("获取预览失败: " + e.getMessage());
        }
    }

    /**
     * 执行高级导出
     */
    @PostMapping("/advanced")
    public ResponseEntity<byte[]> exportAdvanced(@RequestBody AdvancedExportRequest request) {
        try {
            Workbook workbook = advancedExportService.generateExport(request);

            // 转换为字节数组
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            byte[] bytes = outputStream.toByteArray();

            // 生成文件名
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String filename = "产品导出_" + dateStr + ".xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename);
            headers.setContentLength(bytes.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(bytes);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
