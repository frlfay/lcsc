package com.lcsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lcsc.dto.AdvancedExportRequest;
import com.lcsc.entity.ImageLink;
import com.lcsc.entity.Product;
import com.lcsc.entity.Shop;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 高级导出服务
 */
@Service
public class AdvancedExportService {

    private static final Logger log = LoggerFactory.getLogger(AdvancedExportService.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private ShopService shopService;

    @Autowired
    private ImageLinkService imageLinkService;

    @Autowired
    private CategoryLevel1CodeService categoryLevel1CodeService;

    @Autowired
    private CategoryLevel2CodeService categoryLevel2CodeService;

    /**
     * 生成高级导出Excel
     */
    public Workbook generateExport(AdvancedExportRequest request) {
        log.info("开始生成高级导出Excel, 请求参数: shopIds={}, categoryIds={}, brands={}",
                request.getShopIds(), request.getCategoryIds(), request.getBrands());

        // 1. 查询符合条件的产品
        List<Product> products = queryProducts(request);
        log.info("查询到 {} 个产品", products.size());

        if (products.isEmpty()) {
            return createEmptyWorkbook();
        }

        // 2. 查询选中的店铺
        List<Shop> shops = queryShops(request.getShopIds());
        log.info("查询到 {} 个店铺", shops.size());

        // 3. 查询图片链接（按产品图片名称关联）
        Map<String, Map<Integer, String>> imageLinkMap = new HashMap<>();
        if (Boolean.TRUE.equals(request.getIncludeImageLinks())) {
            imageLinkMap = queryImageLinks(products, request.getShopIds());
        }

        // 4. 生成Excel
        return createWorkbook(products, shops, imageLinkMap, request);
    }

    /**
     * 查询符合条件的产品
     */
    private List<Product> queryProducts(AdvancedExportRequest request) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        // 分类筛选
        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            wrapper.in(Product::getCategoryLevel2Id, request.getCategoryIds());
        }

        // 品牌筛选
        if (request.getBrands() != null && !request.getBrands().isEmpty()) {
            wrapper.in(Product::getBrand, request.getBrands());
        }

        // 产品编号筛选
        if (request.getProductCodes() != null && !request.getProductCodes().isEmpty()) {
            wrapper.in(Product::getProductCode, request.getProductCodes());
        }

        // 关键词搜索
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            String keyword = request.getKeyword().trim();
            wrapper.and(w -> w.like(Product::getModel, keyword)
                    .or().like(Product::getBrand, keyword)
                    .or().like(Product::getProductCode, keyword));
        }

        wrapper.orderByAsc(Product::getCategoryLevel1Id, Product::getCategoryLevel2Id, Product::getProductCode);

        return productService.list(wrapper);
    }

    /**
     * 查询选中的店铺
     */
    private List<Shop> queryShops(List<Integer> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return shopService.getAllShopList();
        }
        return shopService.listByIds(shopIds);
    }

    /**
     * 查询图片链接
     * @return Map<imageName, Map<shopId, imageLink>>
     */
    private Map<String, Map<Integer, String>> queryImageLinks(List<Product> products, List<Integer> shopIds) {
        Set<String> imageNames = products.stream()
                .map(Product::getImageName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (imageNames.isEmpty()) {
            return new HashMap<>();
        }

        LambdaQueryWrapper<ImageLink> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ImageLink::getImageName, imageNames);
        if (shopIds != null && !shopIds.isEmpty()) {
            wrapper.in(ImageLink::getShopId, shopIds);
        }

        List<ImageLink> imageLinks = imageLinkService.list(wrapper);

        // 转换为Map结构
        Map<String, Map<Integer, String>> result = new HashMap<>();
        for (ImageLink link : imageLinks) {
            result.computeIfAbsent(link.getImageName(), k -> new HashMap<>())
                    .put(link.getShopId(), link.getImageLink());
        }

        return result;
    }

    /**
     * 创建Excel工作簿
     */
    private Workbook createWorkbook(List<Product> products, List<Shop> shops,
                                     Map<String, Map<Integer, String>> imageLinkMap,
                                     AdvancedExportRequest request) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("产品导出");

        // 创建表头样式
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        CellStyle priceStyle = createPriceStyle(workbook);

        // 创建表头
        int colIndex = 0;
        Row headerRow = sheet.createRow(0);

        // 基础列
        String[] baseHeaders = {"产品编号", "品牌", "型号", "封装", "一级分类", "二级分类", "库存"};
        for (String header : baseHeaders) {
            Cell cell = headerRow.createCell(colIndex++);
            cell.setCellValue(header);
            cell.setCellStyle(headerStyle);
        }

        // 阶梯价格列
        if (Boolean.TRUE.equals(request.getIncludeLadderPrices())) {
            for (int i = 1; i <= 6; i++) {
                Cell qtyCell = headerRow.createCell(colIndex++);
                qtyCell.setCellValue("阶梯" + i + "数量");
                qtyCell.setCellStyle(headerStyle);

                Cell priceCell = headerRow.createCell(colIndex++);
                priceCell.setCellValue("阶梯" + i + "价格");
                priceCell.setCellStyle(headerStyle);
            }
        }

        // 店铺相关列
        for (Shop shop : shops) {
            Cell templateCell = headerRow.createCell(colIndex++);
            templateCell.setCellValue(shop.getShopName() + "-运费模板");
            templateCell.setCellStyle(headerStyle);

            if (Boolean.TRUE.equals(request.getIncludeImageLinks())) {
                Cell linkCell = headerRow.createCell(colIndex++);
                linkCell.setCellValue(shop.getShopName() + "-图片链接");
                linkCell.setCellStyle(headerStyle);
            }
        }

        // 获取分类名称缓存
        Map<Integer, String> level1NameMap = getCategoryLevel1Names();
        Map<Integer, String> level2NameMap = getCategoryLevel2Names();

        // 填充数据
        BigDecimal discountRate = request.getDiscountRate() != null ? request.getDiscountRate() : BigDecimal.ONE;

        int rowIndex = 1;
        for (Product product : products) {
            Row row = sheet.createRow(rowIndex++);
            int col = 0;

            // 基础信息
            createCell(row, col++, product.getProductCode(), dataStyle);
            createCell(row, col++, product.getBrand(), dataStyle);
            createCell(row, col++, product.getModel(), dataStyle);
            createCell(row, col++, product.getPackageName(), dataStyle);
            createCell(row, col++, level1NameMap.getOrDefault(product.getCategoryLevel1Id(), ""), dataStyle);
            createCell(row, col++, level2NameMap.getOrDefault(product.getCategoryLevel2Id(), ""), dataStyle);
            createCell(row, col++, product.getTotalStockQuantity(), dataStyle);

            // 阶梯价格
            if (Boolean.TRUE.equals(request.getIncludeLadderPrices())) {
                col = fillLadderPrices(row, col, product, discountRate, dataStyle, priceStyle);
            }

            // 店铺信息
            for (Shop shop : shops) {
                // 运费模板ID
                createCell(row, col++, shop.getShippingTemplateId(), dataStyle);

                // 图片链接
                if (Boolean.TRUE.equals(request.getIncludeImageLinks())) {
                    String imageLink = "";
                    // 优先使用自定义图片链接（image_links表）
                    if (product.getImageName() != null && imageLinkMap.containsKey(product.getImageName())) {
                        Map<Integer, String> shopLinks = imageLinkMap.get(product.getImageName());
                        if (shopLinks.containsKey(shop.getId())) {
                            imageLink = shopLinks.get(shop.getId());
                        }
                    }
                    // Fallback: 如果没有自定义链接，使用立创原始链接
                    if ((imageLink == null || imageLink.isEmpty()) && product.getProductImageUrlBig() != null) {
                        imageLink = product.getProductImageUrlBig();
                    }
                    createCell(row, col++, imageLink, dataStyle);
                }
            }
        }

        // 自动调整列宽
        for (int i = 0; i < colIndex; i++) {
            sheet.autoSizeColumn(i);
            // 设置最大宽度
            if (sheet.getColumnWidth(i) > 50 * 256) {
                sheet.setColumnWidth(i, 50 * 256);
            }
        }

        log.info("Excel生成完成, 共 {} 行数据", products.size());
        return workbook;
    }

    /**
     * 填充阶梯价格
     */
    private int fillLadderPrices(Row row, int col, Product product, BigDecimal discountRate,
                                  CellStyle dataStyle, CellStyle priceStyle) {
        // 阶梯1
        createCell(row, col++, product.getLadderPrice1Quantity(), dataStyle);
        createPriceCell(row, col++, product.getLadderPrice1Price(), discountRate, priceStyle);

        // 阶梯2
        createCell(row, col++, product.getLadderPrice2Quantity(), dataStyle);
        createPriceCell(row, col++, product.getLadderPrice2Price(), discountRate, priceStyle);

        // 阶梯3
        createCell(row, col++, product.getLadderPrice3Quantity(), dataStyle);
        createPriceCell(row, col++, product.getLadderPrice3Price(), discountRate, priceStyle);

        // 阶梯4
        createCell(row, col++, product.getLadderPrice4Quantity(), dataStyle);
        createPriceCell(row, col++, product.getLadderPrice4Price(), discountRate, priceStyle);

        // 阶梯5
        createCell(row, col++, product.getLadderPrice5Quantity(), dataStyle);
        createPriceCell(row, col++, product.getLadderPrice5Price(), discountRate, priceStyle);

        // 阶梯6
        createCell(row, col++, product.getLadderPrice6Quantity(), dataStyle);
        createPriceCell(row, col++, product.getLadderPrice6Price(), discountRate, priceStyle);

        return col;
    }

    /**
     * 创建单元格
     */
    private void createCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
        cell.setCellStyle(style);
    }

    /**
     * 创建价格单元格（应用折扣）
     */
    private void createPriceCell(Row row, int col, BigDecimal price, BigDecimal discountRate, CellStyle style) {
        Cell cell = row.createCell(col);
        if (price == null) {
            cell.setCellValue("");
        } else {
            BigDecimal discountedPrice = price.multiply(discountRate).setScale(4, RoundingMode.HALF_UP);
            cell.setCellValue(discountedPrice.doubleValue());
        }
        cell.setCellStyle(style);
    }

    /**
     * 获取一级分类名称映射
     */
    private Map<Integer, String> getCategoryLevel1Names() {
        return categoryLevel1CodeService.list().stream()
                .collect(Collectors.toMap(
                        c -> c.getId(),
                        c -> c.getCategoryLevel1Name(),
                        (a, b) -> a
                ));
    }

    /**
     * 获取二级分类名称映射
     */
    private Map<Integer, String> getCategoryLevel2Names() {
        return categoryLevel2CodeService.list().stream()
                .collect(Collectors.toMap(
                        c -> c.getId(),
                        c -> c.getCategoryLevel2Name(),
                        (a, b) -> a
                ));
    }

    /**
     * 创建空工作簿
     */
    private Workbook createEmptyWorkbook() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("产品导出");
        Row row = sheet.createRow(0);
        row.createCell(0).setCellValue("没有符合条件的产品数据");
        return workbook;
    }

    /**
     * 创建表头样式
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    /**
     * 创建数据样式
     */
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * 创建价格样式
     */
    private CellStyle createPriceStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.0000"));
        return style;
    }

    /**
     * 获取导出统计信息（用于预览）
     */
    public Map<String, Object> getExportPreview(AdvancedExportRequest request) {
        List<Product> products = queryProducts(request);
        List<Shop> shops = queryShops(request.getShopIds());

        Map<String, Object> preview = new HashMap<>();
        preview.put("productCount", products.size());
        preview.put("shopCount", shops.size());
        preview.put("shops", shops.stream().map(Shop::getShopName).collect(Collectors.toList()));

        // 分类统计
        Map<Integer, Long> categoryCount = products.stream()
                .collect(Collectors.groupingBy(Product::getCategoryLevel2Id, Collectors.counting()));
        preview.put("categoryCount", categoryCount.size());

        // 品牌统计
        Set<String> brands = products.stream()
                .map(Product::getBrand)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        preview.put("brandCount", brands.size());

        return preview;
    }
}
