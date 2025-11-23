package com.lcsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lcsc.dto.AdvancedExportRequest;
import com.lcsc.dto.ExportTaskItem;
import com.lcsc.entity.*;
import com.lcsc.mapper.ImageLinkMapper;
import com.lcsc.mapper.ProductMapper;
import com.lcsc.mapper.ShopMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 高级导出服务 - 淘宝CSV格式
 */
@Service
public class AdvancedExportService {

    private static final Logger log = LoggerFactory.getLogger(AdvancedExportService.class);

    // CSV固定值
    private static final String FIXED_CID = "50018871";  // 宝贝类目ID（固定值）
    private static final String OPTION_CODE_PREFIX = "1627207:-100";  // 选项编号前缀

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private ImageLinkMapper imageLinkMapper;

    /**
     * 添加产品到任务列表
     * @param request 筛选条件
     * @param currentTasks 当前任务列表
     * @return 更新后的任务列表（按productCode去重）
     */
    public List<ExportTaskItem> addToTaskList(AdvancedExportRequest request, List<ExportTaskItem> currentTasks) {
        log.info("添加产品到任务列表, shopId={}, categoryLevel2Id={}, categoryLevel3Id={}, brand={}",
                request.getShopId(), request.getCategoryLevel2Id(), request.getCategoryLevel3Id(), request.getBrand());

        // 1. 查询符合条件的产品
        List<Product> products = queryProductsByRequest(request);
        log.info("查询到 {} 个符合条件的产品", products.size());

        // 2. 查询店铺信息
        Shop shop = shopMapper.selectById(request.getShopId());
        if (shop == null) {
            throw new RuntimeException("店铺不存在: " + request.getShopId());
        }

        // 3. 转换为任务项
        List<ExportTaskItem> newTasks = products.stream().map(product -> {
            ExportTaskItem task = new ExportTaskItem();
            task.setProductCode(product.getProductCode());
            task.setModel(product.getModel());
            task.setBrand(product.getBrand());
            task.setShopId(shop.getId());
            task.setShopName(shop.getShopName());
            task.setDiscounts(request.getDiscounts());
            task.setAddedAt(System.currentTimeMillis());
            return task;
        }).collect(Collectors.toList());

        // 4. 合并到现有任务列表并去重（按productCode）
        Map<String, ExportTaskItem> taskMap = new LinkedHashMap<>();

        // 先添加现有任务
        for (ExportTaskItem task : currentTasks) {
            taskMap.put(task.getProductCode(), task);
        }

        // 再添加新任务（如果已存在则覆盖）
        for (ExportTaskItem task : newTasks) {
            taskMap.put(task.getProductCode(), task);
        }

        List<ExportTaskItem> result = new ArrayList<>(taskMap.values());
        log.info("任务列表更新完成, 总计 {} 个产品", result.size());
        return result;
    }

    /**
     * 生成淘宝CSV文件
     * @param tasks 任务列表
     * @return CSV文件字节数组
     */
    public byte[] generateTaobaoCsv(List<ExportTaskItem> tasks) throws IOException {
        log.info("开始生成淘宝CSV文件, 任务数量: {}", tasks.size());

        // 1. 查询所有产品详情
        Set<String> productCodes = tasks.stream()
                .map(ExportTaskItem::getProductCode)
                .collect(Collectors.toSet());

        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .in(Product::getProductCode, productCodes)
        );

        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getProductCode, p -> p));

        // 2. 查询所有图片链接
        Map<String, Map<Integer, String>> imageLinkMap = loadImageLinks();

        // 3. 查询所有店铺信息
        Set<Integer> shopIds = tasks.stream()
                .map(ExportTaskItem::getShopId)
                .collect(Collectors.toSet());

        List<Shop> shops = shopMapper.selectBatchIds(shopIds);
        Map<Integer, Shop> shopMap = shops.stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));

        // 4. 构建CSV内容
        StringBuilder csv = new StringBuilder();

        // CSV头部（第1-3行）
        appendCsvHeader(csv);

        // 产品数据行
        for (ExportTaskItem task : tasks) {
            Product product = productMap.get(task.getProductCode());
            if (product == null) {
                log.warn("产品不存在: {}", task.getProductCode());
                continue;
            }

            Shop shop = shopMap.get(task.getShopId());
            if (shop == null) {
                log.warn("店铺不存在: {}", task.getShopId());
                continue;
            }

            appendProductRow(csv, product, shop, task, imageLinkMap);
        }

        log.info("淘宝CSV文件生成完成");
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成淘宝Excel文件
     * @param tasks 任务列表
     * @return Excel文件字节数组
     */
    public byte[] generateTaobaoExcel(List<ExportTaskItem> tasks) throws IOException {
        log.info("开始生成淘宝Excel文件, 任务数量: {}", tasks.size());

        // 1. 查询所有产品详情
        Set<String> productCodes = tasks.stream()
                .map(ExportTaskItem::getProductCode)
                .collect(Collectors.toSet());

        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .in(Product::getProductCode, productCodes)
        );

        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getProductCode, p -> p));

        // 2. 查询所有图片链接
        Map<String, Map<Integer, String>> imageLinkMap = loadImageLinks();

        // 3. 查询所有店铺信息
        Set<Integer> shopIds = tasks.stream()
                .map(ExportTaskItem::getShopId)
                .collect(Collectors.toSet());

        List<Shop> shops = shopMapper.selectBatchIds(shopIds);
        Map<Integer, Shop> shopMap = shops.stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));

        // 4. 创建Excel工作簿
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("淘宝导入");

            // 创建表头（第1-3行）
            createExcelHeader(sheet);

            // 创建产品数据行（从第4行开始，rowIndex=3）
            int rowIndex = 3;
            for (ExportTaskItem task : tasks) {
                Product product = productMap.get(task.getProductCode());
                if (product == null) {
                    log.warn("产品不存在: {}", task.getProductCode());
                    continue;
                }

                Shop shop = shopMap.get(task.getShopId());
                if (shop == null) {
                    log.warn("店铺不存在: {}", task.getShopId());
                    continue;
                }

                createProductRow(sheet, rowIndex++, product, shop, task, imageLinkMap);
            }

            workbook.write(outputStream);
            log.info("淘宝Excel文件生成完成");
            return outputStream.toByteArray();
        }
    }

    /**
     * 创建Excel表头（第1-3行）
     */
    private void createExcelHeader(Sheet sheet) {
        // 第1行：版本信息
        Row row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("version 1.00");
        row1.createCell(1).setCellValue("Excel由系统导出");

        // 第2行：英文列名
        Row row2 = sheet.createRow(1);
        String[] englishHeaders = {"title", "cid", "seller_cids", "stuff_status", "location_state", "location_city", "item_type", "price", "auction_increment", "num", "valid_thru", "freight_payer", "post_fee", "ems_fee", "express_fee", "has_invoice", "has_warranty", "approve_status", "has_showcase", "list_time", "description", "cateProps", "postage_id", "has_discount", "modified", "upload_fail_msg", "picture_status", "auction_point", "picture", "video", "skuProps", "inputPids", "inputValues", "outer_id", "propAlias", "auto_fill", "num_id", "local_cid", "navigation_type", "user_name", "syncStatus", "is_lighting_consigment", "is_xinpin", "foodparame", "features", "buyareatype", "global_stock_type", "global_stock_country", "sub_stock_type", "item_size", "item_weight", "sell_promise", "custom_design_flag", "wireless_desc", "barcode", "sku_barcode", "newprepay", "subtitle", "cpv_memo", "input_custom_cpv", "qualification", "add_qualification", "o2o_bind_service", "departure_place", "car_cascade", "legal_customs", "exSkuProps", "deliveryTimeType", "tbDeliveryTime", "nutrientTable", "exFoodParam", "item_volumn", "image_video_type", "shopping_title", "ysbCheckTask", "subStock", "multiDiscountPromotion", "shopping_title2", "useSizeMapping", "sizeMapping", "shippingArea"};
        for (int i = 0; i < englishHeaders.length; i++) {
            row2.createCell(i).setCellValue(englishHeaders[i]);
        }

        // 第3行：中文列名
        Row row3 = sheet.createRow(2);
        String[] chineseHeaders = {"宝贝名称", "宝贝类目", "店铺类目", "新旧程度", "省", "城市", "出售方式", "宝贝价格", "加价幅度", "宝贝数量", "有效期", "运费承担", "平邮", "EMS", "快递", "发票", "保修", "放入仓库", "橱柜推荐", "开始时间", "宝贝描述", "宝贝属性", "邮费模板ID", "会员打折", "修改时间", "上传状态", "图片状态", "返点比例", "新图片", "视频", "销售属性组合", "用户输入ID串", "用户输入名-值对", "商家编码", "销售属性别名", "代充类型", "数字ID", "本地ID", "宝贝分类", "用户名称", "宝贝状态", "闪电发货", "新品", "食品专项", "尺码库", "采购地", "库存类型", "国家地区", "库存计数", "物流体积", "物流重量", "退换货承诺", "定制工具", "无线详情", "商品条形码", "sku 条形码", "7天退货", "宝贝卖点", "属性值备注", "自定义属性值", "商品资质", "增加商品资质", "关联线下服务", "发货地", "汽车品牌", "报关方式", "扩展Sku", "发货时效", "预售时间", "成份表", "扩展食品安全", "物流体积", "主图视频比例", "导购标题", "商品预检", "拍下减库存", "多件优惠", "导购标题2", "使用商品尺寸表", "商品尺寸表", "新发货地"};
        for (int i = 0; i < chineseHeaders.length; i++) {
            row3.createCell(i).setCellValue(chineseHeaders[i]);
        }
    }

    /**
     * 创建产品数据行
     */
    private void createProductRow(Sheet sheet, int rowIndex, Product product, Shop shop,
                                   ExportTaskItem task, Map<String, Map<Integer, String>> imageLinkMap) {
        Row row = sheet.createRow(rowIndex);
        int col = 0;

        // 0. title: 型号、封装 三级分类、二级分类、一级分类
        row.createCell(col++).setCellValue(buildTitle(product));

        // 1. cid: 固定值
        row.createCell(col++).setCellValue(FIXED_CID);

        // 2. seller_cids: 店铺分类码
        row.createCell(col++).setCellValue(shop.getId() + ";");

        // 3-6. stuff_status, location_state, location_city, item_type: 空
        col += 4;

        // 7. price: 最高阶价格*折扣
        BigDecimal price = calculatePrice(product, task.getDiscounts());
        row.createCell(col++).setCellValue(price.doubleValue());

        // 8. auction_increment: 空
        col++;

        // 9. num: (阶梯级数+2)*1000000
        int ladderCount = getLadderCount(product);
        int num = (ladderCount + 2) * 1000000;
        row.createCell(col++).setCellValue(num);

        // 10-14. valid_thru~express_fee: 空
        col += 5;

        // 15. has_invoice: 0
        row.createCell(col++).setCellValue(0);

        // 16. has_warranty: 0
        row.createCell(col++).setCellValue(0);

        // 17. approve_status: 空
        col++;

        // 18. has_showcase: 1
        row.createCell(col++).setCellValue(1);

        // 19. list_time: 空
        col++;

        // 20. description: 0
        row.createCell(col++).setCellValue(0);

        // 21. description: <p>参数名:参数值</p>格式
        row.createCell(col++).setCellValue(buildDescription(product));

        // 22. cateProps: 选项编号组合
        row.createCell(col++).setCellValue(buildCateProps(ladderCount));

        // 23. postage_id: 店铺运费模板ID
        row.createCell(col++).setCellValue(shop.getShippingTemplateId() != null ? shop.getShippingTemplateId() : "");

        // 24-27. has_discount~auction_point: 空
        col += 4;

        // 28. picture: :1:0:|+图片链接
        row.createCell(col++).setCellValue(buildPicture(product, shop.getId(), imageLinkMap));

        // 29. video: 空
        col++;

        // 30. skuProps: 价格:1000000::选项编号组合
        row.createCell(col++).setCellValue(buildSkuProps(product, task.getDiscounts(), ladderCount));

        // 31-33. inputPids, inputValues, outer_id前的空
        col += 2;

        // 34. outer_id: 品牌（&替换为空格）
        String outerId = product.getBrand() != null ? product.getBrand().replace("&", " ") : "";
        row.createCell(col++).setCellValue(outerId);

        // 35. propAlias: 选项编号:买X-Y个选这个组合
        row.createCell(col++).setCellValue(buildPropAlias(product, ladderCount));

        // 36-79. 剩余字段全部为空（不需要创建）
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 根据请求条件查询产品
     */
    private List<Product> queryProductsByRequest(AdvancedExportRequest request) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        // 分类筛选（优先三级 > 二级 > 一级）
        if (request.getCategoryLevel3Id() != null) {
            wrapper.eq(Product::getCategoryLevel3Id, request.getCategoryLevel3Id());
        } else if (request.getCategoryLevel2Id() != null) {
            wrapper.eq(Product::getCategoryLevel2Id, request.getCategoryLevel2Id());
        } else if (request.getCategoryLevel1Id() != null) {
            wrapper.eq(Product::getCategoryLevel1Id, request.getCategoryLevel1Id());
        }

        // 品牌筛选
        if (request.getBrand() != null && !request.getBrand().isEmpty()) {
            wrapper.eq(Product::getBrand, request.getBrand());
        }

        // 图片筛选
        if (request.getHasImage() != null) {
            if (request.getHasImage()) {
                wrapper.isNotNull(Product::getProductImageUrlBig);
            } else {
                wrapper.isNull(Product::getProductImageUrlBig);
            }
        }

        // 库存筛选
        if (request.getStockMin() != null) {
            wrapper.ge(Product::getTotalStockQuantity, request.getStockMin());
        }
        if (request.getStockMax() != null) {
            wrapper.le(Product::getTotalStockQuantity, request.getStockMax());
        }

        return productMapper.selectList(wrapper);
    }

    /**
     * 加载所有图片链接（image_name -> shop_id -> image_link）
     */
    private Map<String, Map<Integer, String>> loadImageLinks() {
        List<ImageLink> links = imageLinkMapper.selectList(null);
        Map<String, Map<Integer, String>> map = new HashMap<>();

        for (ImageLink link : links) {
            map.computeIfAbsent(link.getImageName(), k -> new HashMap<>())
               .put(link.getShopId(), link.getImageLink());
        }

        return map;
    }

    /**
     * 追加CSV头部（第1-3行）
     */
    private void appendCsvHeader(StringBuilder csv) {
        // 第1行：版本信息
        csv.append("version 1.00,Csv由Tbup理货员导出,");
        for (int i = 0; i < 78; i++) csv.append(",");
        csv.append("\n");

        // 第2行：英文列名
        csv.append("title,cid,seller_cids,stuff_status,location_state,location_city,item_type,price,auction_increment,num,valid_thru,freight_payer,post_fee,ems_fee,express_fee,has_invoice,has_warranty,approve_status,has_showcase,list_time,description,cateProps,postage_id,has_discount,modified,upload_fail_msg,picture_status,auction_point,picture,video,skuProps,inputPids,inputValues,outer_id,propAlias,auto_fill,num_id,local_cid,navigation_type,user_name,syncStatus,is_lighting_consigment,is_xinpin,foodparame,features,buyareatype,global_stock_type,global_stock_country,sub_stock_type,item_size,item_weight,sell_promise,custom_design_flag,wireless_desc,barcode,sku_barcode,newprepay,subtitle,cpv_memo,input_custom_cpv,qualification,add_qualification,o2o_bind_service,departure_place,car_cascade,legal_customs,exSkuProps,deliveryTimeType,tbDeliveryTime,nutrientTable,exFoodParam,item_volumn,image_video_type,shopping_title,ysbCheckTask,subStock,multiDiscountPromotion,shopping_title2,useSizeMapping,sizeMapping,shippingArea\n");

        // 第3行：中文列名
        csv.append("宝贝名称,宝贝类目,店铺类目,新旧程度,省,城市,出售方式,宝贝价格,加价幅度,宝贝数量,有效期,运费承担,平邮,EMS,快递,发票,保修,放入仓库,橱柜推荐,开始时间,宝贝描述,宝贝属性,邮费模板ID,会员打折,修改时间,上传状态,图片状态,返点比例,新图片,视频,销售属性组合,用户输入ID串,用户输入名-值对,商家编码,销售属性别名,代充类型,数字ID,本地ID,宝贝分类,用户名称,宝贝状态,闪电发货,新品,食品专项,尺码库,采购地,库存类型,国家地区,库存计数,物流体积,物流重量,退换货承诺,定制工具,无线详情,商品条形码,sku 条形码,7天退货,宝贝卖点,属性值备注,自定义属性值,商品资质,增加商品资质,关联线下服务,发货地,汽车品牌,报关方式,扩展Sku,发货时效,预售时间,成份表,扩展食品安全,物流体积,主图视频比例,导购标题,商品预检,拍下减库存,多件优惠,导购标题2,使用商品尺寸表,商品尺寸表,新发货地\n");
    }

    /**
     * 追加产品数据行
     */
    private void appendProductRow(StringBuilder csv, Product product, Shop shop, ExportTaskItem task,
                                   Map<String, Map<Integer, String>> imageLinkMap) {
        // 0. title: 型号、封装 三级分类、二级分类、一级分类
        String title = buildTitle(product);
        csv.append(escapeCsv(title)).append(",");

        // 1. cid: 固定值
        csv.append(FIXED_CID).append(",");

        // 2. seller_cids: 店铺二级分类码（从shop获取，暂用店铺ID代替）
        csv.append(shop.getId()).append(";,");

        // 3-6. stuff_status, location_state, location_city, item_type: 空
        csv.append(",,,,");

        // 7. price: 最高阶价格*折扣，向上取整2位小数
        BigDecimal price = calculatePrice(product, task.getDiscounts());
        csv.append(price.toString()).append(",");

        // 8. auction_increment: 空
        csv.append(",");

        // 9. num: (阶梯级数+2)*1000000
        int ladderCount = getLadderCount(product);
        int num = (ladderCount + 2) * 1000000;
        csv.append(num).append(",");

        // 10-20. valid_thru~list_time: 空
        csv.append(",,,,0,0,,1,,0,,");

        // 21. description: <p>参数名:参数值</p>格式
        String description = buildDescription(product);
        csv.append(escapeCsv(description)).append(",");

        // 22. cateProps: 选项编号组合
        String cateProps = buildCateProps(ladderCount);
        csv.append(cateProps).append(",");

        // 23. postage_id: 店铺运费模板ID
        csv.append(shop.getShippingTemplateId() != null ? shop.getShippingTemplateId() : "").append(",");

        // 24-27. has_discount~auction_point: 空
        csv.append(",,,,,");

        // 28. picture: :1:0:|+图片链接
        String picture = buildPicture(product, shop.getId(), imageLinkMap);
        csv.append(picture).append(",");

        // 29. video: 空
        csv.append(",");

        // 30. skuProps: 价格:1000000::选项编号组合
        String skuProps = buildSkuProps(product, task.getDiscounts(), ladderCount);
        csv.append(skuProps).append(",");

        // 31-33. inputPids, inputValues: 空
        csv.append(",,,");

        // 34. outer_id: 品牌（&替换为空格）
        String outerId = product.getBrand() != null ? product.getBrand().replace("&", " ") : "";
        csv.append(escapeCsv(outerId)).append(",");

        // 35. propAlias: 选项编号:买X-Y个选这个组合
        String propAlias = buildPropAlias(product, ladderCount);
        csv.append(escapeCsv(propAlias)).append(",");

        // 36-79. 剩余字段全部为空
        for (int i = 0; i < 44; i++) {
            csv.append(",");
        }

        csv.append("\n");
    }

    // ==================== CSV字段生成方法 ====================

    /**
     * 构建title: 型号、封装 三级分类、二级分类、一级分类
     */
    private String buildTitle(Product product) {
        return String.format("%s、%s %s、%s、%s",
                product.getModel() != null ? product.getModel() : "",
                product.getPackageName() != null ? product.getPackageName() : "",
                product.getCategoryLevel3Name() != null ? product.getCategoryLevel3Name() : "",
                product.getCategoryLevel2Name() != null ? product.getCategoryLevel2Name() : "",
                product.getCategoryLevel1Name() != null ? product.getCategoryLevel1Name() : ""
        );
    }

    /**
     * 计算价格: 最高阶价格（最低价）*折扣，向上取整2位小数
     */
    private BigDecimal calculatePrice(Product product, List<BigDecimal> discounts) {
        // 找出最高阶（最低）价格
        BigDecimal minPrice = null;
        for (int i = 1; i <= 6; i++) {
            BigDecimal price = getLadderPriceByIndex(product, i);
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                if (minPrice == null || price.compareTo(minPrice) < 0) {
                    minPrice = price;
                }
            }
        }

        if (minPrice == null) {
            return BigDecimal.ZERO;
        }

        // 应用第一级折扣
        BigDecimal discount = discounts.get(0).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal result = minPrice.multiply(discount);

        // 向上取整保留2位小数
        return result.setScale(2, RoundingMode.UP);
    }

    /**
     * 构建description: <p>参数名:参数值</p>格式
     */
    private String buildDescription(Product product) {
        // 这里简化处理，实际可能需要从JSON参数中解析
        StringBuilder desc = new StringBuilder();
        if (product.getParametersText() != null && !product.getParametersText().isEmpty()) {
            desc.append("<p>产品参数 : ").append(product.getParametersText()).append("</p>");
        }
        return desc.toString();
    }

    /**
     * 构建cateProps: 选项编号组合（1627207:-1001;1627207:-1002;...）
     */
    private String buildCateProps(int ladderCount) {
        StringBuilder props = new StringBuilder();
        int totalOptions = ladderCount + 2;
        for (int i = 1; i <= totalOptions; i++) {
            props.append(OPTION_CODE_PREFIX).append(i).append(";");
        }
        return props.toString();
    }

    /**
     * 构建picture: :1:0:|+图片链接
     */
    private String buildPicture(Product product, int shopId, Map<String, Map<Integer, String>> imageLinkMap) {
        String imageUrl = "";

        // 优先使用自定义图片链接
        if (product.getImageName() != null && imageLinkMap.containsKey(product.getImageName())) {
            Map<Integer, String> shopLinks = imageLinkMap.get(product.getImageName());
            if (shopLinks.containsKey(shopId)) {
                imageUrl = shopLinks.get(shopId);
            }
        }

        // Fallback: 使用立创原始链接
        if ((imageUrl == null || imageUrl.isEmpty()) && product.getProductImageUrlBig() != null) {
            imageUrl = product.getProductImageUrlBig();
        }

        return ":1:0:|" + imageUrl;
    }

    /**
     * 构建skuProps: 价格1:1000000::1627207:-1001;价格2:1000000::1627207:-1002;...
     */
    private String buildSkuProps(Product product, List<BigDecimal> discounts, int ladderCount) {
        StringBuilder props = new StringBuilder();
        int totalOptions = ladderCount + 2;

        for (int i = 0; i < totalOptions; i++) {
            BigDecimal price;
            if (i < ladderCount) {
                // 实际阶梯价
                price = getLadderPriceByIndex(product, i + 1);
            } else {
                // 额外2级用第1阶价格
                price = getLadderPriceByIndex(product, 1);
            }

            if (price == null) {
                price = BigDecimal.ZERO;
            }

            // 应用对应级别的折扣
            BigDecimal discount = discounts.get(Math.min(i, discounts.size() - 1))
                    .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            BigDecimal finalPrice = price.multiply(discount).setScale(5, RoundingMode.HALF_UP);

            props.append(finalPrice.toString())
                 .append(":1000000::")
                 .append(OPTION_CODE_PREFIX)
                 .append(i + 1)
                 .append(";");
        }

        return props.toString();
    }

    /**
     * 构建propAlias: 1627207:-1001:买5-49个选这个;1627207:-1002:买50-149个选这个;...
     */
    private String buildPropAlias(Product product, int ladderCount) {
        StringBuilder alias = new StringBuilder();
        int totalOptions = ladderCount + 2;

        for (int i = 0; i < totalOptions; i++) {
            String optionCode = OPTION_CODE_PREFIX + (i + 1);
            String text;

            if (i == totalOptions - 2) {
                text = "选数量相符的选项";
            } else if (i == totalOptions - 1) {
                text = "买多少个填多少件";
            } else if (i < ladderCount - 1) {
                int currentQty = getLadderQuantityByIndex(product, i + 1);
                int nextQty = getLadderQuantityByIndex(product, i + 2);
                text = "买" + currentQty + "-" + (nextQty - 1) + "个选这个";
            } else {
                int currentQty = getLadderQuantityByIndex(product, i + 1);
                text = "买" + currentQty + "个起选这个";
            }

            alias.append(optionCode).append(":").append(text).append(";");
        }

        return alias.toString();
    }

    // ==================== 阶梯价格辅助方法 ====================

    /**
     * 获取产品的阶梯价格级数（1-6）
     */
    private int getLadderCount(Product product) {
        for (int i = 6; i >= 1; i--) {
            BigDecimal price = getLadderPriceByIndex(product, i);
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                return i;
            }
        }
        return 1;  // 至少1级
    }

    /**
     * 根据索引获取阶梯价格（1-based）
     */
    private BigDecimal getLadderPriceByIndex(Product product, int index) {
        switch (index) {
            case 1: return product.getLadderPrice1Price();
            case 2: return product.getLadderPrice2Price();
            case 3: return product.getLadderPrice3Price();
            case 4: return product.getLadderPrice4Price();
            case 5: return product.getLadderPrice5Price();
            case 6: return product.getLadderPrice6Price();
            default: return null;
        }
    }

    /**
     * 根据索引获取阶梯数量（1-based）
     */
    private int getLadderQuantityByIndex(Product product, int index) {
        switch (index) {
            case 1: return product.getLadderPrice1Quantity() != null ? product.getLadderPrice1Quantity() : 0;
            case 2: return product.getLadderPrice2Quantity() != null ? product.getLadderPrice2Quantity() : 0;
            case 3: return product.getLadderPrice3Quantity() != null ? product.getLadderPrice3Quantity() : 0;
            case 4: return product.getLadderPrice4Quantity() != null ? product.getLadderPrice4Quantity() : 0;
            case 5: return product.getLadderPrice5Quantity() != null ? product.getLadderPrice5Quantity() : 0;
            case 6: return product.getLadderPrice6Quantity() != null ? product.getLadderPrice6Quantity() : 0;
            default: return 0;
        }
    }

    /**
     * CSV字段转义（处理逗号、双引号、换行）
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        // 如果包含逗号、双引号或换行，需要用双引号包裹，并转义内部双引号
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }
}
