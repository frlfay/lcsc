package com.lcsc.service;

import com.lcsc.dto.ImageLinkImportError;
import com.lcsc.dto.ImageLinkImportResult;
import com.lcsc.dto.ImageLinkImportRow;
import com.lcsc.entity.ImageLink;
import com.lcsc.entity.Shop;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片链接Excel导入服务
 */
@Service
public class ImageLinkImportService {

    private static final Logger log = LoggerFactory.getLogger(ImageLinkImportService.class);

    @Autowired
    private ShopService shopService;

    @Autowired
    private ImageLinkService imageLinkService;

    /**
     * 导入Excel文件
     *
     * @param file Excel文件
     * @return 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ImageLinkImportResult importFromExcel(MultipartFile file) {
        ImageLinkImportResult result = new ImageLinkImportResult();
        List<ImageLinkImportRow> validRows = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            // 跳过标题行，从第二行开始处理
            int totalRows = sheet.getPhysicalNumberOfRows();
            for (int i = 1; i < totalRows; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                // 解析行数据
                ImageLinkImportRow importRow = parseRow(row, i);

                // 验证行数据
                ImageLinkImportError error = validateRow(importRow);
                if (error.hasErrors()) {
                    result.addError(error);
                    result.setFailureCount(result.getFailureCount() + 1);
                } else {
                    validRows.add(importRow);
                }
            }

            // 批量保存有效数据
            if (!validRows.isEmpty()) {
                int successCount = saveBatch(validRows, result);
                result.setSuccessCount(successCount);
            }

        } catch (IOException e) {
            log.error("解析Excel文件失败", e);
            ImageLinkImportError error = new ImageLinkImportError(0);
            error.addError("解析Excel文件失败: " + e.getMessage());
            result.addError(error);
        }

        return result;
    }

    /**
     * 解析Excel行数据
     */
    private ImageLinkImportRow parseRow(Row row, int rowIndex) {
        ImageLinkImportRow importRow = new ImageLinkImportRow();
        importRow.setRowNumber(rowIndex + 1); // Excel行号从1开始（不包含标题行）

        // 列0: 店铺名称
        Cell shopNameCell = row.getCell(0);
        if (shopNameCell != null) {
            importRow.setShopName(getCellValueAsString(shopNameCell));
        }

        // 列1: 产品编号（可选）
        Cell productCodeCell = row.getCell(1);
        if (productCodeCell != null) {
            importRow.setProductCode(getCellValueAsString(productCodeCell));
        }

        // 列2: 图片名称
        Cell imageNameCell = row.getCell(2);
        if (imageNameCell != null) {
            importRow.setImageName(getCellValueAsString(imageNameCell));
        }

        // 列3: 图片链接
        Cell imageLinkCell = row.getCell(3);
        if (imageLinkCell != null) {
            importRow.setImageLink(getCellValueAsString(imageLinkCell));
        }

        return importRow;
    }

    /**
     * 获取单元格的字符串值
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // 处理数字，避免科学计数法
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == (long) numericValue) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (IllegalStateException e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BLANK:
                return null;
            default:
                return null;
        }
    }

    /**
     * 验证行数据
     */
    private ImageLinkImportError validateRow(ImageLinkImportRow row) {
        ImageLinkImportError error = new ImageLinkImportError(row.getRowNumber());

        // 1. 验证店铺名称（必填）
        if (row.getShopName() == null || row.getShopName().isBlank()) {
            error.addError("店铺名称不能为空");
        }

        // 2. 验证图片名称（必填）
        if (row.getImageName() == null || row.getImageName().isBlank()) {
            error.addError("图片名称不能为空");
        }

        // 3. 验证图片链接（必填）
        if (row.getImageLink() == null || row.getImageLink().isBlank()) {
            error.addError("图片链接不能为空");
        } else if (!isValidUrl(row.getImageLink())) {
            error.addError("图片链接格式不正确");
        }

        return error;
    }

    /**
     * 验证URL格式
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /**
     * 批量保存数据
     */
    private int saveBatch(List<ImageLinkImportRow> rows, ImageLinkImportResult result) {
        int successCount = 0;

        // 缓存店铺信息，避免重复查询
        Map<String, Shop> shopCache = new HashMap<>();

        List<ImageLink> imageLinksToSave = new ArrayList<>();

        for (ImageLinkImportRow row : rows) {
            try {
                // 1. 查找店铺ID
                Shop shop = shopCache.get(row.getShopName());
                if (shop == null) {
                    shop = shopService.getByShopName(row.getShopName());
                    if (shop == null) {
                        ImageLinkImportError error = new ImageLinkImportError(row.getRowNumber());
                        error.addError("店铺不存在: " + row.getShopName());
                        result.addError(error);
                        result.setFailureCount(result.getFailureCount() + 1);
                        continue;
                    }
                    shopCache.put(row.getShopName(), shop);
                }

                // 2. 构建ImageLink对象
                ImageLink imageLink = new ImageLink();
                imageLink.setImageName(row.getImageName());
                imageLink.setShopId(shop.getId());
                imageLink.setImageLink(row.getImageLink());

                imageLinksToSave.add(imageLink);

            } catch (Exception e) {
                log.error("处理第{}行数据失败", row.getRowNumber(), e);
                ImageLinkImportError error = new ImageLinkImportError(row.getRowNumber());
                error.addError("处理失败: " + e.getMessage());
                result.addError(error);
                result.setFailureCount(result.getFailureCount() + 1);
            }
        }

        // 3. 批量保存
        if (!imageLinksToSave.isEmpty()) {
            try {
                // 使用UPSERT模式保存，如果已存在则更新
                for (ImageLink imageLink : imageLinksToSave) {
                    ImageLink existing = imageLinkService.getByImageNameAndShopId(
                        imageLink.getImageName(),
                        imageLink.getShopId()
                    );

                    if (existing != null) {
                        // 更新现有记录
                        imageLink.setId(existing.getId());
                    }

                    imageLinkService.saveOrUpdate(imageLink);
                    successCount++;
                }
            } catch (Exception e) {
                log.error("批量保存图片链接失败", e);
                throw new RuntimeException("批量保存失败: " + e.getMessage());
            }
        }

        return successCount;
    }

    /**
     * 生成导入模板
     */
    public Workbook generateTemplate() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("图片链接导入模板");

        // 创建标题行样式
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        // 创建标题行
        Row headerRow = sheet.createRow(0);
        String[] headers = {"店铺名称", "产品编号", "图片名称", "图片链接"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 创建示例数据行
        Row exampleRow = sheet.createRow(1);
        exampleRow.createCell(0).setCellValue("示例店铺");
        exampleRow.createCell(1).setCellValue("C123456");
        exampleRow.createCell(2).setCellValue("C123456_front.jpg");
        exampleRow.createCell(3).setCellValue("https://example.com/image.jpg");

        // 设置列宽
        sheet.setColumnWidth(0, 20 * 256); // 店铺名称
        sheet.setColumnWidth(1, 15 * 256); // 产品编号
        sheet.setColumnWidth(2, 25 * 256); // 图片名称
        sheet.setColumnWidth(3, 50 * 256); // 图片链接

        return workbook;
    }
}
