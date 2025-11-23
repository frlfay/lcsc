package com.lcsc.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 高级导出请求DTO - 淘宝CSV格式
 * 用于批量添加模式的筛选条件
 */
@Data
public class AdvancedExportRequest {

    /**
     * 选择的店铺ID（单选，必填）
     * 用于关联运费模板、店铺分类码
     */
    private Integer shopId;

    /**
     * 一级分类ID（可选）
     */
    private Integer categoryLevel1Id;

    /**
     * 二级分类ID（可选）
     */
    private Integer categoryLevel2Id;

    /**
     * 三级分类ID（可选）
     */
    private Integer categoryLevel3Id;

    /**
     * 品牌名称（可选，单选）
     */
    private String brand;

    /**
     * 是否有图片（可选）
     * - true: 只选有图片的产品
     * - false: 只选无图片的产品
     * - null: 不限
     */
    private Boolean hasImage;

    /**
     * 库存最小值（可选）
     */
    private Integer stockMin;

    /**
     * 库存最大值（可选）
     */
    private Integer stockMax;

    /**
     * 6级价格折扣配置（百分比，必填）
     * 例如：[90, 88, 85, 82, 80, 78] 表示一级打9折，二级打88折...
     * 数组长度必须为6
     */
    private List<BigDecimal> discounts;
}
