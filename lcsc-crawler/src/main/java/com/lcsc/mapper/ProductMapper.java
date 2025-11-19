package com.lcsc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcsc.entity.Product;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 产品信息 Mapper 接口
 *
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 根据二级分类ID删除产品(覆盖模式)
     * @param catalogId 二级分类ID
     * @return 删除的记录数
     */
    @Delete("DELETE FROM products WHERE category_level2_id = #{catalogId}")
    int deleteByCatalogId(@Param("catalogId") Integer catalogId);

}
