package com.lcsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 三级分类实体类
 *
 * @author lcsc-crawler
 * @since 2025-11-20
 */
@TableName("category_level3_codes")
public class CategoryLevel3Code {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 三级分类名称
     */
    private String categoryLevel3Name;

    /**
     * API源名称（只读，每次同步更新）
     */
    private String sourceName;

    /**
     * 用户自定义名称（手动编辑后设置，优先级最高）
     */
    private String customName;

    /**
     * 是否被用户修改过（0=否，1=是）
     */
    private Integer isCustomized;

    /**
     * 立创API返回的catalogId
     */
    private String catalogId;

    /**
     * 所属一级分类ID（外键）
     */
    private Integer categoryLevel1Id;

    /**
     * 所属二级分类ID（外键）
     */
    private Integer categoryLevel2Id;

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

    public String getCategoryLevel3Name() {
        return categoryLevel3Name;
    }

    public void setCategoryLevel3Name(String categoryLevel3Name) {
        this.categoryLevel3Name = categoryLevel3Name;
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

    public Integer getCategoryLevel2Id() {
        return categoryLevel2Id;
    }

    public void setCategoryLevel2Id(Integer categoryLevel2Id) {
        this.categoryLevel2Id = categoryLevel2Id;
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

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public Integer getIsCustomized() {
        return isCustomized;
    }

    public void setIsCustomized(Integer isCustomized) {
        this.isCustomized = isCustomized;
    }
}
