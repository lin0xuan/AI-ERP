package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;
import com.aierp.model.entity.ShopWaybillAccount;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author 1
* @description 针对表【oms_diansan_waybill_branch(点三电子面单物流网点表)】的数据库操作Service
* @createDate 2025-10-12 17:35:35
*/
public interface ShopWaybillAccountService extends IService<ShopWaybillAccount> {
    PageResult<ShopWaybillAccount> queryPageList(ShopWaybillAccount branch, PageQuery pageQuery);
    List<ShopWaybillAccount> queryList(Long merchantId, Long shopId, String cpCode);
    ResultVo<Long> saveAndUpdate(ShopWaybillAccount branch);
}
