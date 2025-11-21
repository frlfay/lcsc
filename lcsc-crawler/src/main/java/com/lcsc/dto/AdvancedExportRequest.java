package com.lcsc.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 高级导出请求DTO
 */
public class AdvancedExportRequest {

    /**
     * 选择的店铺ID列表
     */
    private List<Integer> shopIds;

    /**
     * 产品筛选条件 - 分类ID列表（二级或三级分类）
     */
    private List<Integer> categoryIds;

    /**
     * 产品筛选条件 - 品牌列表
     */
    private List<String> brands;

    /**
     * 产品筛选条件 - 产品编号列表（精确匹配）
     */
    private List<String> productCodes;

    /**
     * 产品筛选条件 - 关键词搜索（模糊匹配型号、品牌）
     */
    private String keyword;

    /**
     * 价格折扣率（如0.9表示9折）
     */
    private BigDecimal discountRate;

    /**
     * 是否包含图片链接
     */
    private Boolean includeImageLinks;

    /**
     * 是否包含阶梯价格
     */
    private Boolean includeLadderPrices;

    // Getters and Setters
    public List<Integer> getShopIds() {
        return shopIds;
    }

    public void setShopIds(List<Integer> shopIds) {
        this.shopIds = shopIds;
    }

    public List<Integer> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(List<Integer> categoryIds) {
        this.categoryIds = categoryIds;
    }

    public List<String> getBrands() {
        return brands;
    }

    public void setBrands(List<String> brands) {
        this.brands = brands;
    }

    public List<String> getProductCodes() {
        return productCodes;
    }

    public void setProductCodes(List<String> productCodes) {
        this.productCodes = productCodes;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public BigDecimal getDiscountRate() {
        return discountRate;
    }

    public void setDiscountRate(BigDecimal discountRate) {
        this.discountRate = discountRate;
    }

    public Boolean getIncludeImageLinks() {
        return includeImageLinks;
    }

    public void setIncludeImageLinks(Boolean includeImageLinks) {
        this.includeImageLinks = includeImageLinks;
    }

    public Boolean getIncludeLadderPrices() {
        return includeLadderPrices;
    }

    public void setIncludeLadderPrices(Boolean includeLadderPrices) {
        this.includeLadderPrices = includeLadderPrices;
    }
}
