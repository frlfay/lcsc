package com.lcsc.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 高级导出任务项
 * 用于批量添加模式：用户可多次添加产品到任务列表，最后统一导出
 */
@Data
public class ExportTaskItem {
    /**
     * 产品编号（唯一标识，用于去重）
     */
    private String productCode;

    /**
     * 产品型号
     */
    private String model;

    /**
     * 品牌名称
     */
    private String brand;

    /**
     * 关联的店铺ID
     */
    private Integer shopId;

    /**
     * 店铺名称（用于前端显示）
     */
    private String shopName;

    /**
     * 6级价格折扣配置（百分比）
     * 例如：[90, 88, 85, 82, 80, 78] 表示一级价格打9折，二级打88折...
     * 添加时保存，导出时应用
     */
    private List<BigDecimal> discounts;

    /**
     * 添加时间戳（用于排序）
     */
    private Long addedAt;
}
