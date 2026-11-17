package com.aierp.service;

import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;
import com.aierp.model.entity.ErpSupplier;
import com.aierp.model.entity.ErpWarehouse;
import com.aierp.model.request.WarehouseCloudQuery;
import com.aierp.model.request.WarehousePageQuery;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author qilip
* @description 针对表【erp_cloud_warehouse】的数据库操作Service
* @createDate 2025-07-07 15:10:27
*/
public interface ErpWarehouseService extends IService<ErpWarehouse> {
    PageResult<ErpWarehouse> queryPageList(WarehousePageQuery query );

    List<ErpWarehouse> getCloudWarehouseList(WarehouseCloudQuery query);
    List<ErpWarehouse> getJdWarehouseList(ErpWarehouse bo);
    List<ErpWarehouse> getJkyWarehouseList(ErpWarehouse bo);
    List<ErpWarehouse> getWarehouseList(ErpWarehouse bo);
    ResultVo<Long> saveErpCloudWarehouse(List<ErpWarehouse> list);
    ResultVo shareMerchant(Long warehouseId,Long[] merchantIds);
    ErpWarehouse getByLoginName(String loginName);

    /**
     * 自动创建供应商仓库
     * @param supplier
     * @return
     */
    ResultVo<Long> addSupplierWarehouse(ErpSupplier supplier,String username);
}
