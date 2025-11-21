package com.lcsc.dto;

import java.io.Serializable;
import java.util.Objects;

/**
 * 品牌拆分单元的数据传输对象 (DTO).
 * 用于在任务拆分服务中封装单个品牌的拆分信息.
 *
 * @author Claude Code
 * @since 2025-11-21
 */
public class BrandSplitUnit implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 品牌ID（立创商城的品牌标识符）.
     */
    private String brandId;

    /**
     * 品牌名称.
     */
    private String brandName;

    /**
     * 该品牌在当前分类下的产品数量.
     */
    private int productCount;

    /**
     * 所属分类的catalog ID.
     */
    private String catalogId;

    // 构造函数
    public BrandSplitUnit() {
    }

    public BrandSplitUnit(String brandId, String brandName, int productCount, String catalogId) {
        this.brandId = brandId;
        this.brandName = brandName;
        this.productCount = productCount;
        this.catalogId = catalogId;
    }

    // Getters and Setters
    public String getBrandId() {
        return brandId;
    }

    public void setBrandId(String brandId) {
        this.brandId = brandId;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public int getProductCount() {
        return productCount;
    }

    public void setProductCount(int productCount) {
        this.productCount = productCount;
    }

    public String getCatalogId() {
        return catalogId;
    }

    public void setCatalogId(String catalogId) {
        this.catalogId = catalogId;
    }

    @Override
    public String toString() {
        return "BrandSplitUnit{" +
                "brandId='" + brandId + '\'' +
                ", brandName='" + brandName + '\'' +
                ", productCount=" + productCount +
                ", catalogId='" + catalogId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BrandSplitUnit that = (BrandSplitUnit) o;
        return Objects.equals(brandId, that.brandId) && Objects.equals(catalogId, that.catalogId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(brandId, catalogId);
    }
}
