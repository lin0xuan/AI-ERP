package com.aierp.mapper;

import com.aierp.model.entity.ErpSalesGoodsPackage;
import com.aierp.model.entity.ErpSalesGoodsPackageItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ErpSalesGoodsPackageMapper extends BaseMapper<ErpSalesGoodsPackage> {

    List<ErpSalesGoodsPackageItem> selectItemsByPackageId(@Param("packageId") Long packageId);
}