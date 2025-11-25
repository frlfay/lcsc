package com.lcsc.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 通用拆分单元DTO.
 * 用于封装任意维度的拆分信息（品牌、封装、参数等）.
 *
 * @author Claude Code
 * @since 2025-11-24
 */
public class SplitUnit implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 拆分维度名称（如 "Brand", "Package", "Voltage" 等）
     */
    private String dimensionName;

    /**
     * 筛选值ID（用于API调用）
     */
    private String filterId;

    /**
     * 筛选值名称（用于显示）
     */
    private String filterValue;

    /**
     * 该筛选值下的产品数量
     */
    private int productCount;

    /**
     * 所属分类的catalog ID
     */
    private String catalogId;

    /**
     * 用于API调用的筛选参数
     */
    private Map<String, Object> filterParams;

    // 构造函数
    public SplitUnit() {
        this.filterParams = new HashMap<>();
    }

    public SplitUnit(String dimensionName, String filterId, String filterValue,
                     int productCount, String catalogId) {
        this.dimensionName = dimensionName;
        this.filterId = filterId;
        this.filterValue = filterValue;
        this.productCount = productCount;
        this.catalogId = catalogId;
        this.filterParams = new HashMap<>();
    }

    /**
     * 添加筛选参数
     */
    public void addFilterParam(String key, Object value) {
        this.filterParams.put(key, value);
    }

    // Getters and Setters
    public String getDimensionName() {
        return dimensionName;
    }

    public void setDimensionName(String dimensionName) {
        this.dimensionName = dimensionName;
    }

    public String getFilterId() {
        return filterId;
    }

    public void setFilterId(String filterId) {
        this.filterId = filterId;
    }

    public String getFilterValue() {
        return filterValue;
    }

    public void setFilterValue(String filterValue) {
        this.filterValue = filterValue;
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

    public Map<String, Object> getFilterParams() {
        return filterParams;
    }

    public void setFilterParams(Map<String, Object> filterParams) {
        this.filterParams = filterParams;
    }

    @Override
    public String toString() {
        return "SplitUnit{" +
                "dimensionName='" + dimensionName + '\'' +
                ", filterId='" + filterId + '\'' +
                ", filterValue='" + filterValue + '\'' +
                ", productCount=" + productCount +
                ", catalogId='" + catalogId + '\'' +
                ", filterParams=" + filterParams +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SplitUnit splitUnit = (SplitUnit) o;
        return Objects.equals(dimensionName, splitUnit.dimensionName) &&
               Objects.equals(filterId, splitUnit.filterId) &&
               Objects.equals(catalogId, splitUnit.catalogId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensionName, filterId, catalogId);
    }
}
