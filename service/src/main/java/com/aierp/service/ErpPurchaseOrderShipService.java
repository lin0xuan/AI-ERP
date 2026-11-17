package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;
import com.aierp.model.entity.ErpPurchaseOrderShip;
import com.aierp.model.request.PurchaseOrderStockInBo;
import com.aierp.model.request.SearchRequest;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【scm_purchase_order_ship(采购订单物流表)】的数据库操作Service
* @createDate 2024-10-20 17:18:53
*/
public interface ErpPurchaseOrderShipService extends IService<ErpPurchaseOrderShip> {
    PageResult<ErpPurchaseOrderShip> queryPageList(SearchRequest bo, PageQuery pageQuery);

    int updateScmPurchaseOrderShip(ErpPurchaseOrderShip erpPurchaseOrderShip);

    ResultVo<Long> createStockInEntry(PurchaseOrderStockInBo bo,Long userId,String userName);
}
