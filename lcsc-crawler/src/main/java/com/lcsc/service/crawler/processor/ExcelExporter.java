package com.lcsc.service.crawler.processor;

import com.lcsc.entity.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Excel导出器
 * 负责将产品数据导出为Excel格式
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Component
public class ExcelExporter {

    private static final Logger logger = LoggerFactory.getLogger(ExcelExporter.class);

    @Autowired
    private DataProcessor dataProcessor;

    /**
     * 导出产品数据为Excel文件
     * 
     * @param products 产品列表
     * @param outputPath 输出文件路径
     * @return 导出是否成功
     */
    public boolean exportToExcel(List<Product> products, String outputPath) {
        logger.info("开始导出Excel文件: {}, 产品数量: {}", outputPath, products.size());
        
        if (products.isEmpty()) {
            logger.warn("产品列表为空，跳过Excel导出");
            return false;
        }
        
        try {
            // 确保输出目录存在
            Path outputFile = Paths.get(outputPath);
            Path parentDir = outputFile.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                logger.info("创建输出目录: {}", parentDir);
            }
            
            // 收集参数信息和价格阶梯
            Map<String, String> parameterInfo = dataProcessor.collectParameterInfo(products);
            Set<Integer> ladderSteps = dataProcessor.collectLadderSteps(products);
            
            // 创建工作簿
            try (Workbook workbook = new XSSFWorkbook()) {
                
                // 创建产品数据工作表
                Sheet productSheet = workbook.createSheet("产品数据");
                createProductSheet(productSheet, products, parameterInfo, ladderSteps);
                
                // 创建统计信息工作表
                Sheet statisticsSheet = workbook.createSheet("统计信息");
                createStatisticsSheet(statisticsSheet, products);
                
                // 写入文件
                try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                    workbook.write(fos);
                }
            }
            
            logger.info("Excel文件导出成功: {}", outputPath);
            return true;
            
        } catch (Exception e) {
            logger.error("导出Excel文件失败: {}", outputPath, e);
            return false;
        }
    }

    /**
     * 创建产品数据工作表
     */
    private void createProductSheet(Sheet sheet, List<Product> products, 
                                  Map<String, String> parameterInfo, Set<Integer> ladderSteps) {
        logger.debug("创建产品数据工作表");
        
        // 生成列标题
        List<String> headers = dataProcessor.generateExcelHeaders(parameterInfo, ladderSteps);
        
        // 创建样式
        Workbook workbook = sheet.getWorkbook();
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        
        // 创建标题行
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }
        
        // 创建数据行
        int rowIndex = 1;
        for (Product product : products) {
            try {
                Row dataRow = sheet.createRow(rowIndex++);
                Map<String, Object> rowData = dataProcessor.processProductForExcel(
                    product, parameterInfo, ladderSteps);
                
                int cellIndex = 0;
                for (String header : headers) {
                    Cell cell = dataRow.createCell(cellIndex++);
                    Object value = rowData.get(header);
                    setCellValue(cell, value);
                    cell.setCellStyle(dataStyle);
                }
                
                // 每1000行记录一次进度
                if (rowIndex % 1000 == 0) {
                    logger.debug("已处理 {} 行数据", rowIndex - 1);
                }
                
            } catch (Exception e) {
                logger.error("处理产品行数据失败: {}", product.getProductCode(), e);
            }
        }
        
        // 自动调整列宽（仅对前20列，避免性能问题）
        int maxColumns = Math.min(headers.size(), 20);
        for (int i = 0; i < maxColumns; i++) {
            try {
                sheet.autoSizeColumn(i);
                // 设置最大列宽，避免过宽
                int maxWidth = 256 * 50; // 50个字符宽度
                if (sheet.getColumnWidth(i) > maxWidth) {
                    sheet.setColumnWidth(i, maxWidth);
                }
            } catch (Exception e) {
                logger.debug("调整列宽失败: 列{}", i);
            }
        }
        
        logger.debug("产品数据工作表创建完成，行数: {}, 列数: {}", rowIndex, headers.size());
    }

    /**
     * 创建统计信息工作表
     */
    private void createStatisticsSheet(Sheet sheet, List<Product> products) {
        logger.debug("创建统计信息工作表");
        
        try {
            Map<String, Object> statistics = dataProcessor.generateStatistics(products);
            
            Workbook workbook = sheet.getWorkbook();
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            
            int rowIndex = 0;
            
            // 基础统计信息
            rowIndex = addStatisticSection(sheet, "基础统计", rowIndex, headerStyle, dataStyle,
                Arrays.asList(
                    new AbstractMap.SimpleEntry<>("总产品数量", statistics.get("totalProducts")),
                    new AbstractMap.SimpleEntry<>("总库存数量", statistics.get("totalStock")),
                    new AbstractMap.SimpleEntry<>("有库存产品数", statistics.get("inStockProducts")),
                    new AbstractMap.SimpleEntry<>("生成时间", statistics.get("generatedAt"))
                ));
            
            rowIndex++; // 空行
            
            // 品牌统计
            @SuppressWarnings("unchecked")
            Map<String, Long> brandStats = (Map<String, Long>) statistics.get("brandStatistics");
            if (brandStats != null && !brandStats.isEmpty()) {
                List<Map.Entry<String, Object>> brandEntries = new ArrayList<>();
                brandStats.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(20) // 只显示前20个品牌
                    .forEach(entry -> brandEntries.add(
                        new AbstractMap.SimpleEntry<>(entry.getKey(), (Object)entry.getValue())));
                
                rowIndex = addStatisticSection(sheet, "品牌统计（前20）", rowIndex, headerStyle, dataStyle, brandEntries);
            }
            
            rowIndex++; // 空行
            
            // 封装统计
            @SuppressWarnings("unchecked")
            Map<String, Long> packageStats = (Map<String, Long>) statistics.get("packageStatistics");
            if (packageStats != null && !packageStats.isEmpty()) {
                List<Map.Entry<String, Object>> packageEntries = new ArrayList<>();
                packageStats.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(20) // 只显示前20个封装
                    .forEach(entry -> packageEntries.add(
                        new AbstractMap.SimpleEntry<>(entry.getKey(), (Object)entry.getValue())));
                
                rowIndex = addStatisticSection(sheet, "封装统计（前20）", rowIndex, headerStyle, dataStyle, packageEntries);
            }
            
            // 自动调整列宽
            for (int i = 0; i < 3; i++) {
                sheet.autoSizeColumn(i);
            }
            
        } catch (Exception e) {
            logger.error("创建统计信息工作表失败", e);
        }
        
        logger.debug("统计信息工作表创建完成");
    }

    /**
     * 添加统计信息段落
     */
    private int addStatisticSection(Sheet sheet, String sectionTitle, int startRow,
                                  CellStyle headerStyle, CellStyle dataStyle,
                                  List<Map.Entry<String, Object>> data) {
        int rowIndex = startRow;
        
        // 段落标题
        Row titleRow = sheet.createRow(rowIndex++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(sectionTitle);
        titleCell.setCellStyle(headerStyle);
        
        // 表头
        Row headerRow = sheet.createRow(rowIndex++);
        Cell headerCell1 = headerRow.createCell(0);
        headerCell1.setCellValue("项目");
        headerCell1.setCellStyle(headerStyle);
        
        Cell headerCell2 = headerRow.createCell(1);
        headerCell2.setCellValue("数值");
        headerCell2.setCellStyle(headerStyle);
        
        // 数据行
        for (Map.Entry<String, Object> entry : data) {
            Row dataRow = sheet.createRow(rowIndex++);
            
            Cell keyCell = dataRow.createCell(0);
            keyCell.setCellValue(entry.getKey());
            keyCell.setCellStyle(dataStyle);
            
            Cell valueCell = dataRow.createCell(1);
            setCellValue(valueCell, entry.getValue());
            valueCell.setCellStyle(dataStyle);
        }
        
        return rowIndex;
    }

    /**
     * 创建标题样式
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        // 背景色
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // 边框
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // 对齐
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // 字体
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        
        return style;
    }

    /**
     * 创建数据样式
     */
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        // 边框
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // 对齐
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // 字体
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 9);
        style.setFont(font);
        
        return style;
    }

    /**
     * 设置单元格值
     */
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }
}
