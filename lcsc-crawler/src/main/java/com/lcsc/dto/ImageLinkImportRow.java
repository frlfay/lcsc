package com.lcsc.dto;

/**
 * Excel导入行数据DTO
 */
public class ImageLinkImportRow {
    /**
     * 行号（从1开始，不包含标题行）
     */
    private int rowNumber;

    /**
     * 店铺名称
     */
    private String shopName;

    /**
     * 产品编号（可选，仅用于参考验证）
     */
    private String productCode;

    /**
     * 图片名称
     */
    private String imageName;

    /**
     * 图片链接
     */
    private String imageLink;

    public ImageLinkImportRow() {
    }

    public ImageLinkImportRow(int rowNumber, String shopName, String productCode, String imageName, String imageLink) {
        this.rowNumber = rowNumber;
        this.shopName = shopName;
        this.productCode = productCode;
        this.imageName = imageName;
        this.imageLink = imageLink;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public String getImageLink() {
        return imageLink;
    }

    public void setImageLink(String imageLink) {
        this.imageLink = imageLink;
    }
}
