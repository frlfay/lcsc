package com.lcsc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lcsc.entity.Shop;
import org.apache.ibatis.annotations.Mapper;

/**
 * 店铺及运费模板 Mapper 接口
 * 
 * @author lcsc-crawler
 * @since 2024-01-01
 */
@Mapper
public interface ShopMapper extends BaseMapper<Shop> {

}
