package com.lcsc.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;


import java.time.LocalDateTime;

/**
 * 一级分类分类码实体类
 *
 * @author lcsc-crawler
 * @since 2024-01-01
 */

@TableName("category_level1_codes")
public class CategoryLevel1Code {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 一级分类名称（程序生成）
     */
    @TableField(insertStrategy = FieldStrategy.ALWAYS)
    private String categoryLevel1Name;

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
     * 分类码（数字串，前端编辑录入）
     */
    private String categoryCode;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    // Getter and Setter methods
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }


    public String getCategoryLevel1Name() {
        return categoryLevel1Name;
    }

    public void setCategoryLevel1Name(String categoryLevel1Name) {
        this.categoryLevel1Name = categoryLevel1Name;
    }

    public String getCatalogId() {
        return catalogId;
    }

    public void setCatalogId(String catalogId) {
        this.catalogId = catalogId;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
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
