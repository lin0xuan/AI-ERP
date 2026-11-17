package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;
import com.aierp.model.entity.ErpPurchaseOrder;
import com.aierp.model.request.PurchaseOrderAddRequest;
import com.aierp.model.request.PurchaseOrderOptionRequest;
import com.aierp.model.request.SearchRequest;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【scm_purchase_order(采购订单)】的数据库操作Service
* @createDate 2024-10-20 15:36:33
*/
public interface ErpPurchaseOrderService extends IService<ErpPurchaseOrder> {
    PageResult<ErpPurchaseOrder> queryPageList(SearchRequest bo, PageQuery pageQuery);
    ErpPurchaseOrder getDetailById(Long id);
    ResultVo<Long> createPurchaseOrder(PurchaseOrderAddRequest addBo);
    int updateScmPurchaseOrder(PurchaseOrderOptionRequest request);
}
