package com.lcsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;


import java.time.LocalDateTime;

/**
 * 店铺及运费模板实体类
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */

@TableName("shops")
public class Shop {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 店铺名称（英文、中文、数字和符号）
     */
    private String shopName;

    /**
     * 运费模板ID码（数字串）
     */
    private String shippingTemplateId;

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


    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getShippingTemplateId() {
        return shippingTemplateId;
    }

    public void setShippingTemplateId(String shippingTemplateId) {
        this.shippingTemplateId = shippingTemplateId;
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
}
