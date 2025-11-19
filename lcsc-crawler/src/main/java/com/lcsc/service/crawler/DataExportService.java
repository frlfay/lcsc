package com.lcsc.service.crawler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lcsc.entity.CategoryLevel1Code;
import com.lcsc.entity.CategoryLevel2Code;
import com.lcsc.entity.Product;
import com.lcsc.service.CategoryLevel1CodeService;
import com.lcsc.service.CategoryLevel2CodeService;
import com.lcsc.service.ProductService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

/**
 * 数据导出服务
 * 支持Excel和CSV格式的产品数据导出
 *
 * @author lcsc-crawler
 * @since 2025-09-03
 */
@Service
public class DataExportService {

    @Autowired
    private ProductService productService;

    @Value("${crawler.storage.export-dir:exports}")
    private String exportDir;

    @Value("${crawler.storage.base-path}")
    private String storageBasePath;

    @Autowired
    private CategoryLevel1CodeService categoryLevel1CodeService;

    @Autowired
    private CategoryLevel2CodeService categoryLevel2CodeService;

    // Excel列标题 - 优化后仅包含产品信息字段
    private static final String[] EXCEL_HEADERS = {
        "产品编号", "型号", "品牌", "封装", "简介",
        "库存数量", "一级分类名称", "二级分类名称",
        "主图URL", "PDF URL",
        "阶梯价1_数量", "阶梯价1_价格", "阶梯价2_数量", "阶梯价2_价格",
        "阶梯价3_数量", "阶梯价3_价格", "阶梯价4_数量", "阶梯价4_价格",
        "阶梯价5_数量", "阶梯价5_价格",
        "额外参数"
    };

    /**
     * 导出所有产品到Excel
     */
    public CompletableFuture<ExportResult> exportAllProductsToExcel() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Product> products = productService.list();
                String fileName = generateFileName("all_products", "xlsx");
                return exportProductsToExcel(products, fileName, "所有产品数据");
            } catch (Exception e) {
                return new ExportResult(false, "导出失败: " + e.getMessage(), null, 0);
            }
        });
    }

    /**
     * 根据条件导出产品到Excel
     */
    public CompletableFuture<ExportResult> exportProductsToExcel(
            Integer categoryLevel1Id,
            Integer categoryLevel2Id,
            String brand,
            String productCode,
            String model,
            Boolean hasStock) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 构建查询条件
                List<Product> products = getProductsByCondition(categoryLevel1Id, categoryLevel2Id, brand, productCode, model, hasStock);
                
                String fileName = generateFileName("filtered_products", "xlsx");
                String sheetName = buildSheetName(categoryLevel1Id, categoryLevel2Id, brand);
                
                return exportProductsToExcel(products, fileName, sheetName);
            } catch (Exception e) {
                return new ExportResult(false, "导出失败: " + e.getMessage(), null, 0);
            }
        });
    }

    /**
     * 导出产品数据到Excel
     */
    private ExportResult exportProductsToExcel(List<Product> products, String fileName, String sheetName) {
        try {
            // 创建导出目录（与下载接口路径保持一致）
            Path exportDirectory = resolveExportDir();
            Files.createDirectories(exportDirectory);
            Path filePath = exportDirectory.resolve(fileName);

            // 创建工作簿
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet(sheetName);

                // 创建标题样式
                CellStyle headerStyle = createHeaderStyle(workbook);
                CellStyle dataStyle = createDataStyle(workbook);

                // 创建标题行
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < EXCEL_HEADERS.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(EXCEL_HEADERS[i]);
                    cell.setCellStyle(headerStyle);
                }

                // 填充数据
                int rowNum = 1;
                for (Product product : products) {
                    Row row = sheet.createRow(rowNum++);
                    fillProductRow(row, product, dataStyle);
                }

                // 自动调整列宽
                for (int i = 0; i < EXCEL_HEADERS.length; i++) {
                    sheet.autoSizeColumn(i);
                    // 设置最大列宽
                    if (sheet.getColumnWidth(i) > 15000) {
                        sheet.setColumnWidth(i, 15000);
                    }
                }

                // 写入文件
                try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                    workbook.write(fos);
                }
            }

            long fileSize = Files.size(filePath);
            System.out.println("Excel导出成功: " + fileName + " (" + formatFileSize(fileSize) + ")");
            
            return new ExportResult(true, "导出成功", filePath.toString(), products.size());

        } catch (Exception e) {
            System.err.println("Excel导出失败: " + e.getMessage());
            return new ExportResult(false, "导出失败: " + e.getMessage(), null, 0);
        }
    }

    /**
     * 导出产品数据到CSV
     */
    public CompletableFuture<ExportResult> exportProductsToCSV(
            Integer categoryLevel1Id,
            Integer categoryLevel2Id,
            String brand,
            String productCode,
            String model,
            Boolean hasStock) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Product> products = getProductsByCondition(categoryLevel1Id, categoryLevel2Id, brand, productCode, model, hasStock);
                String fileName = generateFileName("products", "csv");
                
                // 创建导出目录
                Path exportDirectory = resolveExportDir();
                Files.createDirectories(exportDirectory);
                Path filePath = exportDirectory.resolve(fileName);

                // 写入CSV文件
                try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                    // 写入BOM，确保Excel正确显示中文
                    writer.write('\ufeff');
                    
                    // 写入标题行
                    writer.write(String.join(",", EXCEL_HEADERS));
                    writer.newLine();

                    // 写入数据
                    for (Product product : products) {
                        writer.write(formatProductToCsv(product));
                        writer.newLine();
                    }
                }

                long fileSize = Files.size(filePath);
                System.out.println("CSV导出成功: " + fileName + " (" + formatFileSize(fileSize) + ")");
                
                return new ExportResult(true, "导出成功", filePath.toString(), products.size());

            } catch (Exception e) {
                System.err.println("CSV导出失败: " + e.getMessage());
                return new ExportResult(false, "导出失败: " + e.getMessage(), null, 0);
            }
        });
    }

    /**
     * 根据条件查询产品
     */
    private List<Product> getProductsByCondition(Integer categoryLevel1Id,
                                                 Integer categoryLevel2Id,
                                                 String brand,
                                                 String productCode,
                                                 String model,
                                                 Boolean hasStock) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (productCode != null && !productCode.trim().isEmpty()) {
            wrapper.like(Product::getProductCode, productCode.trim());
        }
        if (brand != null && !brand.trim().isEmpty()) {
            wrapper.like(Product::getBrand, brand.trim());
        }
        if (model != null && !model.trim().isEmpty()) {
            wrapper.like(Product::getModel, model.trim());
        }
        if (categoryLevel1Id != null) {
            wrapper.eq(Product::getCategoryLevel1Id, categoryLevel1Id);
        }
        if (categoryLevel2Id != null) {
            wrapper.eq(Product::getCategoryLevel2Id, categoryLevel2Id);
        }
        if (hasStock != null) {
            if (hasStock) {
                wrapper.gt(Product::getTotalStockQuantity, 0);
            } else {
                wrapper.le(Product::getTotalStockQuantity, 0);
            }
        }
        wrapper.orderByDesc(Product::getLastCrawledAt);

        List<Product> list = productService.list(wrapper);
        enrichCategoryNames(list);
        return list;
    }

    /**
     * 填充产品数据到Excel行 - 仅包含产品信息字段
     */
    private void fillProductRow(Row row, Product product, CellStyle dataStyle) {
        int cellIndex = 0;

        // 基础信息（5列）
        createCell(row, cellIndex++, product.getProductCode(), dataStyle);
        createCell(row, cellIndex++, product.getModel(), dataStyle);
        createCell(row, cellIndex++, product.getBrand(), dataStyle);
        createCell(row, cellIndex++, product.getPackageName(), dataStyle);
        createCell(row, cellIndex++, product.getBriefDescription(), dataStyle);

        // 库存信息（1列）
        createCell(row, cellIndex++, product.getTotalStockQuantity(), dataStyle);

        // 分类信息（2列 - 仅名称，无ID）
        createCell(row, cellIndex++, product.getCategoryLevel1Name(), dataStyle);
        createCell(row, cellIndex++, product.getCategoryLevel2Name(), dataStyle);

        // URL信息（2列 - 仅URL，无文件名）
        createCell(row, cellIndex++, product.getProductImageUrlBig(), dataStyle);
        createCell(row, cellIndex++, product.getPdfUrl(), dataStyle);

        // 阶梯价格（10列）
        createCell(row, cellIndex++, product.getLadderPrice1Quantity(), dataStyle);
        createCell(row, cellIndex++, product.getLadderPrice1Price(), dataStyle);
        createCell(row, cellIndex++, product.getLadderPrice2Quantity(), dataStyle);
        createCell(row, cellIndex++, product.getLadderPrice2Price(), dataStyle);
        createCell(row, cellIndex++, product.getLadderPrice3Quantity(), dataStyle);
        createCell(row, cellIndex++, product.getLadderPrice3Price(), dataStyle);
        createCell(row, cellIndex++, product.getLadderPrice4Quantity(), dataStyle);
        createCell(row, cellIndex++, product.getLadderPrice4Price(), dataStyle);
        createCell(row, cellIndex++, product.getLadderPrice5Quantity(), dataStyle);
        createCell(row, cellIndex++, product.getLadderPrice5Price(), dataStyle);

        // 额外参数（1列 - 最后一列，键值对换行格式）
        String formattedParams = formatParametersTextWithLineBreaks(product.getParametersText());
        createCell(row, cellIndex++, formattedParams, dataStyle);
    }

    /**
     * 创建Excel单元格
     */
    private void createCell(Row row, int columnIndex, Object value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        if (value != null) {
            if (value instanceof String) {
                cell.setCellValue((String) value);
            } else if (value instanceof Integer) {
                cell.setCellValue((Integer) value);
            } else if (value instanceof Long) {
                cell.setCellValue((Long) value);
            } else {
                cell.setCellValue(value.toString());
            }
        }
        cell.setCellStyle(style);
    }

    /**
     * 格式化产品数据为CSV行 - 仅包含产品信息字段
     */
    private String formatProductToCsv(Product product) {
        return String.join(",",
            // 基础信息
            csvEscape(product.getProductCode()),
            csvEscape(product.getModel()),
            csvEscape(product.getBrand()),
            csvEscape(product.getPackageName()),
            csvEscape(product.getBriefDescription()),
            // 库存信息
            csvEscape(product.getTotalStockQuantity()),
            // 分类名称（仅名称）
            csvEscape(product.getCategoryLevel1Name()),
            csvEscape(product.getCategoryLevel2Name()),
            // URL（仅URL）
            csvEscape(product.getProductImageUrlBig()),
            csvEscape(product.getPdfUrl()),
            // 阶梯价格
            csvEscape(product.getLadderPrice1Quantity()),
            csvEscape(product.getLadderPrice1Price()),
            csvEscape(product.getLadderPrice2Quantity()),
            csvEscape(product.getLadderPrice2Price()),
            csvEscape(product.getLadderPrice3Quantity()),
            csvEscape(product.getLadderPrice3Price()),
            csvEscape(product.getLadderPrice4Quantity()),
            csvEscape(product.getLadderPrice4Price()),
            csvEscape(product.getLadderPrice5Quantity()),
            csvEscape(product.getLadderPrice5Price()),
            // 额外参数
            csvEscape(formatParametersTextWithLineBreaks(product.getParametersText()))
        );
    }

    /**
     * CSV字段转义
     */
    private String csvEscape(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    /**
     * 创建Excel标题样式
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    /**
     * 创建Excel数据样式
     */
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    /**
     * 生成文件名
     */
    private String generateFileName(String prefix, String extension) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return prefix + "_" + timestamp + "." + extension;
    }

    /**
     * 构建工作表名称
     */
    private String buildSheetName(Integer categoryLevel1Id, Integer categoryLevel2Id, String brand) {
        StringBuilder sb = new StringBuilder("产品数据");
        if (categoryLevel1Id != null) {
            sb.append("_分类").append(categoryLevel1Id);
        }
        if (categoryLevel2Id != null) {
            sb.append("_子分类").append(categoryLevel2Id);
        }
        if (brand != null && !brand.isEmpty()) {
            sb.append("_").append(brand);
        }
        return sb.toString();
    }

    /**
     * 格式化产品参数文本为键值对换行格式
     * 输入格式：参数1:值1 参数2:值2 参数3:值3
     * 输出格式：
     * 参数1: 值1
     * 参数2: 值2
     * 参数3: 值3
     */
    private String formatParametersTextWithLineBreaks(String parametersText) {
        if (parametersText == null || parametersText.trim().isEmpty()) {
            return "";
        }

        // 假设parametersText格式为 "参数名:参数值 参数名:参数值"
        // 将空格分隔的键值对转换为换行分隔
        StringBuilder formatted = new StringBuilder();
        String[] params = parametersText.split("\\s+");

        for (String param : params) {
            if (param.contains(":")) {
                String[] parts = param.split(":", 2);
                if (parts.length == 2) {
                    formatted.append(parts[0].trim())
                             .append(": ")
                             .append(parts[1].trim())
                             .append("\n");
                }
            }
        }

        // 移除最后一个换行符
        if (formatted.length() > 0 && formatted.charAt(formatted.length() - 1) == '\n') {
            formatted.setLength(formatted.length() - 1);
        }

        return formatted.toString();
    }

    /**
     * 格式化日期时间
     */
    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "";
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * 按分类ID列表导出产品
     */
    public CompletableFuture<ExportResult> exportProductsByCategories(
            List<Integer> categoryIds, String format) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 查询指定分类的所有产品
                QueryWrapper<Product> queryWrapper = new QueryWrapper<>();
                queryWrapper.in("category_level2_id", categoryIds);
                List<Product> products = productService.list(queryWrapper);

                if (products.isEmpty()) {
                    return new ExportResult(false, "所选分类没有产品数据", null, 0);
                }

                String fileName = generateFileName("products_by_categories",
                    format.equals("csv") ? "csv" : "xlsx");

                if (format.equals("csv")) {
                    // CSV导出逻辑
                    return exportProductsToCSVFile(products, fileName);
                } else {
                    // Excel导出逻辑
                    return exportProductsToExcel(products, fileName, "按分类导出");
                }

            } catch (Exception e) {
                return new ExportResult(false, "导出失败: " + e.getMessage(), null, 0);
            }
        });
    }

    /**
     * 私有方法：CSV导出到文件
     */
    private ExportResult exportProductsToCSVFile(List<Product> products, String fileName) {
        try {
            // 创建导出目录
            Path exportDirectory = resolveExportDir();
            Files.createDirectories(exportDirectory);
            Path filePath = exportDirectory.resolve(fileName);

            // 写入CSV文件
            try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                // 写入BOM，确保Excel正确显示中文
                writer.write('\ufeff');

                // 写入标题行
                writer.write(String.join(",", EXCEL_HEADERS));
                writer.newLine();

                // 写入数据
                for (Product product : products) {
                    writer.write(formatProductToCsv(product));
                    writer.newLine();
                }
            }

            long fileSize = Files.size(filePath);
            System.out.println("CSV导出成功: " + fileName + " (" + formatFileSize(fileSize) + ")");

            return new ExportResult(true, "导出成功", filePath.toString(), products.size());

        } catch (Exception e) {
            System.err.println("CSV导出失败: " + e.getMessage());
            return new ExportResult(false, "导出失败: " + e.getMessage(), null, 0);
        }
    }

    // 统一导出位置：和下载接口一致，使用 storageBasePath 的父目录下的 exportDir 目录
    private Path resolveExportDir() {
        try {
            Path base = Paths.get(storageBasePath);
            Path parent = base.getParent() != null ? base.getParent() : base;
            return parent.resolve(exportDir);
        } catch (Exception e) {
            // 兜底：当前工作目录
            return Paths.get(exportDir);
        }
    }

    private void enrichCategoryNames(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        Set<Integer> l1Ids = products.stream().map(Product::getCategoryLevel1Id).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Integer> l2Ids = products.stream().map(Product::getCategoryLevel2Id).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Integer, String> l1Map = l1Ids.isEmpty() ? Collections.emptyMap() :
                categoryLevel1CodeService.listByIds(l1Ids).stream().collect(Collectors.toMap(CategoryLevel1Code::getId, CategoryLevel1Code::getCategoryLevel1Name));
        Map<Integer, String> l2Map = l2Ids.isEmpty() ? Collections.emptyMap() :
                categoryLevel2CodeService.listByIds(l2Ids).stream().collect(Collectors.toMap(CategoryLevel2Code::getId, CategoryLevel2Code::getCategoryLevel2Name));

        for (Product p : products) {
            if (p == null) continue;
            if ((p.getCategoryLevel1Name() == null || p.getCategoryLevel1Name().isEmpty()) && p.getCategoryLevel1Id() != null) {
                p.setCategoryLevel1Name(l1Map.get(p.getCategoryLevel1Id()));
            }
            if ((p.getCategoryLevel2Name() == null || p.getCategoryLevel2Name().isEmpty()) && p.getCategoryLevel2Id() != null) {
                p.setCategoryLevel2Name(l2Map.get(p.getCategoryLevel2Id()));
            }
        }
    }

    /**
     * 导出结果类
     */
    public static class ExportResult {
        private boolean success;
        private String message;
        private String filePath;
        private int recordCount;

        public ExportResult(boolean success, String message, String filePath, int recordCount) {
            this.success = success;
            this.message = message;
            this.filePath = filePath;
            this.recordCount = recordCount;
        }

        // Getters and Setters
        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public int getRecordCount() {
            return recordCount;
        }

        public void setRecordCount(int recordCount) {
            this.recordCount = recordCount;
        }
    }
}
