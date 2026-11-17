package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;
import com.aierp.model.bo.MerchantAddBo;
import com.aierp.model.query.MerchantQuery;
import com.aierp.model.bo.ShareCloudWarehouseToMerchantWarehouse;
import com.aierp.model.entity.ErpMerchant;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author qilip
* @description 针对表【oms_tenant(租户用户表)】的数据库操作Service
* @createDate 2024-06-23 11:10:08
*/
public interface ErpMerchantService extends IService<ErpMerchant> {
    PageResult<ErpMerchant> queryPageList(MerchantQuery bo, PageQuery pageQuery);
    ErpMerchant selectUserByUserName(String userName);
    void updateByUserId(ErpMerchant tenant, Long userId);
    ResultVo<ErpMerchant> add(MerchantAddBo bo,String createBy);
    ResultVo<ErpMerchant> setLoginName(Long id,MerchantAddBo bo,String updateBy);

    /**
     * 分配云仓给商户
     * @param id 商户id
     * @param warehouseList 分配的云仓List
     * @return
     */
    ResultVo shareCloudWarehouse(Long id, List<ShareCloudWarehouseToMerchantWarehouse> warehouseList);
}
