package com.lcsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 产品信息实体类
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@TableName("products")
public class Product {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 产品编号（C********格式）
     */
    private String productCode;

    /**
     * 所属一级分类ID（外键）
     */
    private Integer categoryLevel1Id;

    /**
     * 所属二级分类ID（外键）
     */
    private Integer categoryLevel2Id;

    /**
     * 所属三级分类ID（外键，可为空）
     */
    private Integer categoryLevel3Id;

    /**
     * 所属品牌（中文，&替换为空格）
     */
    private String brand;

    /**
     * 型号（不改变任何字符）
     */
    private String model;

    /**
     * 封装名称（-删除为空）
     */
    private String packageName;

    /**
     * PDF文件名（产品编号_品牌_型号格式，-删除为空）
     */
    private String pdfFilename;

    /**
     * PDF文件本地路径
     */
    private String pdfLocalPath;

    /**
     * 图片名称（C********_****.jpg格式）
     */
    private String imageName;

    /**
     * 图片文件本地路径
     */
    private String imageLocalPath;

    /**
     * 产品图片信息JSON数组
     */
    private String productImagesInfo;

    /**
     * 主图本地路径
     */
    private String mainImageLocalPath;

    /**
     * 总库存数量
     */
    private Integer totalStockQuantity;

    /**
     * 简介（型号+封装+二级分类+一级分类，60字节限制）
     */
    private String briefDescription;

    /**
     * 阶梯数量及价格（最多6阶，包含日期记录）
     */
    private String tierPrices;

    /**
     * 阶梯价格最后更新日期（180天内不更新）
     */
    private LocalDate tierPricesLastUpdate;

    /**
     * 是否人工编辑过
     */
    private Boolean tierPricesManualEdit;

    /**
     * 详细参数（所有中文字段，-删除为空）
     */
    private String detailedParameters;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 最后爬取时间
     */
    private LocalDateTime lastCrawledAt;

    /**
     * 一级分类名称（对应CSV中的"一级分类"）
     */
    private String categoryLevel1Name;

    /**
     * 二级分类名称（对应CSV中的"二级分类"）
     */
    private String categoryLevel2Name;

    /**
     * 主图URL（对应CSV中的"主图"）
     */
    private String productImageUrlBig;

    /**
     * PDF文件URL（对应CSV中的"pdf"）
     */
    private String pdfUrl;

    /**
     * 阶梯价1_数量
     */
    private Integer ladderPrice1Quantity;

    /**
     * 阶梯价1_价格
     */
    private java.math.BigDecimal ladderPrice1Price;

    /**
     * 阶梯价2_数量
     */
    private Integer ladderPrice2Quantity;

    /**
     * 阶梯价2_价格
     */
    private java.math.BigDecimal ladderPrice2Price;

    /**
     * 阶梯价3_数量
     */
    private Integer ladderPrice3Quantity;

    /**
     * 阶梯价3_价格
     */
    private java.math.BigDecimal ladderPrice3Price;

    /**
     * 阶梯价4_数量
     */
    private Integer ladderPrice4Quantity;

    /**
     * 阶梯价4_价格
     */
    private java.math.BigDecimal ladderPrice4Price;

    /**
     * 阶梯价5_数量
     */
    private Integer ladderPrice5Quantity;

    /**
     * 阶梯价5_价格
     */
    private java.math.BigDecimal ladderPrice5Price;

    /**
     * 产品参数文本格式（对应CSV中的"产品参数"）
     */
    private String parametersText;

    // Getter and Setter methods
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }


    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public Integer getCategoryLevel1Id() {
        return categoryLevel1Id;
    }

    public void setCategoryLevel1Id(Integer categoryLevel1Id) {
        this.categoryLevel1Id = categoryLevel1Id;
    }

    public Integer getCategoryLevel2Id() {
        return categoryLevel2Id;
    }

    public void setCategoryLevel2Id(Integer categoryLevel2Id) {
        this.categoryLevel2Id = categoryLevel2Id;
    }

    public Integer getCategoryLevel3Id() {
        return categoryLevel3Id;
    }

    public void setCategoryLevel3Id(Integer categoryLevel3Id) {
        this.categoryLevel3Id = categoryLevel3Id;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPdfFilename() {
        return pdfFilename;
    }

    public void setPdfFilename(String pdfFilename) {
        this.pdfFilename = pdfFilename;
    }

    public String getPdfLocalPath() {
        return pdfLocalPath;
    }

    public void setPdfLocalPath(String pdfLocalPath) {
        this.pdfLocalPath = pdfLocalPath;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public String getImageLocalPath() {
        return imageLocalPath;
    }

    public void setImageLocalPath(String imageLocalPath) {
        this.imageLocalPath = imageLocalPath;
    }

    public String getProductImagesInfo() {
        return productImagesInfo;
    }

    public void setProductImagesInfo(String productImagesInfo) {
        this.productImagesInfo = productImagesInfo;
    }

    public String getMainImageLocalPath() {
        return mainImageLocalPath;
    }

    public void setMainImageLocalPath(String mainImageLocalPath) {
        this.mainImageLocalPath = mainImageLocalPath;
    }

    public Integer getTotalStockQuantity() {
        return totalStockQuantity;
    }

    public void setTotalStockQuantity(Integer totalStockQuantity) {
        this.totalStockQuantity = totalStockQuantity;
    }

    public String getBriefDescription() {
        return briefDescription;
    }

    public void setBriefDescription(String briefDescription) {
        this.briefDescription = briefDescription;
    }

    public String getTierPrices() {
        return tierPrices;
    }

    public void setTierPrices(String tierPrices) {
        this.tierPrices = tierPrices;
    }

    public LocalDate getTierPricesLastUpdate() {
        return tierPricesLastUpdate;
    }

    public void setTierPricesLastUpdate(LocalDate tierPricesLastUpdate) {
        this.tierPricesLastUpdate = tierPricesLastUpdate;
    }

    public Boolean getTierPricesManualEdit() {
        return tierPricesManualEdit;
    }

    public void setTierPricesManualEdit(Boolean tierPricesManualEdit) {
        this.tierPricesManualEdit = tierPricesManualEdit;
    }

    public String getDetailedParameters() {
        return detailedParameters;
    }

    public void setDetailedParameters(String detailedParameters) {
        this.detailedParameters = detailedParameters;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastCrawledAt() {
        return lastCrawledAt;
    }

    public void setLastCrawledAt(LocalDateTime lastCrawledAt) {
        this.lastCrawledAt = lastCrawledAt;
    }

    public String getCategoryLevel1Name() {
        return categoryLevel1Name;
    }

    public void setCategoryLevel1Name(String categoryLevel1Name) {
        this.categoryLevel1Name = categoryLevel1Name;
    }

    public String getCategoryLevel2Name() {
        return categoryLevel2Name;
    }

    public void setCategoryLevel2Name(String categoryLevel2Name) {
        this.categoryLevel2Name = categoryLevel2Name;
    }

    public String getProductImageUrlBig() {
        return productImageUrlBig;
    }

    public void setProductImageUrlBig(String productImageUrlBig) {
        this.productImageUrlBig = productImageUrlBig;
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }

    public Integer getLadderPrice1Quantity() {
        return ladderPrice1Quantity;
    }

    public void setLadderPrice1Quantity(Integer ladderPrice1Quantity) {
        this.ladderPrice1Quantity = ladderPrice1Quantity;
    }

    public java.math.BigDecimal getLadderPrice1Price() {
        return ladderPrice1Price;
    }

    public void setLadderPrice1Price(java.math.BigDecimal ladderPrice1Price) {
        this.ladderPrice1Price = ladderPrice1Price;
    }

    public Integer getLadderPrice2Quantity() {
        return ladderPrice2Quantity;
    }

    public void setLadderPrice2Quantity(Integer ladderPrice2Quantity) {
        this.ladderPrice2Quantity = ladderPrice2Quantity;
    }

    public java.math.BigDecimal getLadderPrice2Price() {
        return ladderPrice2Price;
    }

    public void setLadderPrice2Price(java.math.BigDecimal ladderPrice2Price) {
        this.ladderPrice2Price = ladderPrice2Price;
    }

    public Integer getLadderPrice3Quantity() {
        return ladderPrice3Quantity;
    }

    public void setLadderPrice3Quantity(Integer ladderPrice3Quantity) {
        this.ladderPrice3Quantity = ladderPrice3Quantity;
    }

    public java.math.BigDecimal getLadderPrice3Price() {
        return ladderPrice3Price;
    }

    public void setLadderPrice3Price(java.math.BigDecimal ladderPrice3Price) {
        this.ladderPrice3Price = ladderPrice3Price;
    }

    public Integer getLadderPrice4Quantity() {
        return ladderPrice4Quantity;
    }

    public void setLadderPrice4Quantity(Integer ladderPrice4Quantity) {
        this.ladderPrice4Quantity = ladderPrice4Quantity;
    }

    public java.math.BigDecimal getLadderPrice4Price() {
        return ladderPrice4Price;
    }

    public void setLadderPrice4Price(java.math.BigDecimal ladderPrice4Price) {
        this.ladderPrice4Price = ladderPrice4Price;
    }

    public Integer getLadderPrice5Quantity() {
        return ladderPrice5Quantity;
    }

    public void setLadderPrice5Quantity(Integer ladderPrice5Quantity) {
        this.ladderPrice5Quantity = ladderPrice5Quantity;
    }

    public java.math.BigDecimal getLadderPrice5Price() {
        return ladderPrice5Price;
    }

    public void setLadderPrice5Price(java.math.BigDecimal ladderPrice5Price) {
        this.ladderPrice5Price = ladderPrice5Price;
    }

    public String getParametersText() {
        return parametersText;
    }

    public void setParametersText(String parametersText) {
        this.parametersText = parametersText;
    }
}
