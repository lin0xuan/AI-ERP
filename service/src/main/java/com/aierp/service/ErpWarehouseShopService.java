package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;
import com.aierp.model.entity.ErpWarehouseShop;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author qilip
* @description 针对表【erp_cloud_warehouse_shop】的数据库操作Service
* @createDate 2025-07-07 21:22:47
*/
public interface ErpWarehouseShopService extends IService<ErpWarehouseShop> {

    PageResult<ErpWarehouseShop> queryPageList(ErpWarehouseShop query,  PageQuery pageQuery);
    ResultVo saveErpCloudWarehouseShop(List<ErpWarehouseShop> shops);
    ResultVo saveErpCloudWarehouseShop(ErpWarehouseShop shop);

    List<ErpWarehouseShop> getWarehouseShopList(Long warehouseId,Integer shopType,Long merchantId,String ownerNo);
}
