package com.lcsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;


import java.time.LocalDateTime;

/**
 * 二级分类分类码实体类
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */

@TableName("category_level2_codes")
public class CategoryLevel2Code {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 二级分类名称（程序生成）
     */
    private String categoryLevel2Name;

    /**
     * 立创API返回的catalogId
     */
    private String catalogId;

    /**
     * 所属一级分类ID（外键）
     */
    private Integer categoryLevel1Id;

    /**
     * 各店铺分类码（JSON格式：{shop_id: category_number_code}），前端导入CSV格式
     */
    private String shopCategoryCodes;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 爬取状态：NOT_STARTED, IN_QUEUE, PROCESSING, COMPLETED, FAILED
     */
    private String crawlStatus;

    /**
     * 爬取进度 0-100
     */
    private Integer crawlProgress;

    /**
     * 最后爬取时间
     */
    private LocalDateTime lastCrawlTime;

    /**
     * 该分类下产品总数
     */
    private Integer totalProducts;

    /**
     * 已爬取产品数
     */
    private Integer crawledProducts;

    /**
     * 当前爬取页码
     */
    private Integer currentPage;

    /**
     * 错误信息
     */
    private String errorMessage;

    // Getter and Setter methods
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }


    public String getCategoryLevel2Name() {
        return categoryLevel2Name;
    }

    public void setCategoryLevel2Name(String categoryLevel2Name) {
        this.categoryLevel2Name = categoryLevel2Name;
    }

    public String getCatalogId() {
        return catalogId;
    }

    public void setCatalogId(String catalogId) {
        this.catalogId = catalogId;
    }

    public Integer getCategoryLevel1Id() {
        return categoryLevel1Id;
    }

    public void setCategoryLevel1Id(Integer categoryLevel1Id) {
        this.categoryLevel1Id = categoryLevel1Id;
    }

    public String getShopCategoryCodes() {
        return shopCategoryCodes;
    }

    public void setShopCategoryCodes(String shopCategoryCodes) {
        this.shopCategoryCodes = shopCategoryCodes;
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

    public String getCrawlStatus() {
        return crawlStatus;
    }

    public void setCrawlStatus(String crawlStatus) {
        this.crawlStatus = crawlStatus;
    }

    public Integer getCrawlProgress() {
        return crawlProgress;
    }

    public void setCrawlProgress(Integer crawlProgress) {
        this.crawlProgress = crawlProgress;
    }

    public LocalDateTime getLastCrawlTime() {
        return lastCrawlTime;
    }

    public void setLastCrawlTime(LocalDateTime lastCrawlTime) {
        this.lastCrawlTime = lastCrawlTime;
    }

    public Integer getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(Integer totalProducts) {
        this.totalProducts = totalProducts;
    }

    public Integer getCrawledProducts() {
        return crawledProducts;
    }

    public void setCrawledProducts(Integer crawledProducts) {
        this.crawledProducts = crawledProducts;
    }

    public Integer getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(Integer currentPage) {
        this.currentPage = currentPage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
