package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;
import com.aierp.model.entity.ErpWarehouseSalesAfter;
import com.aierp.model.bo.RefundProcessingBo;
import com.aierp.model.bo.RefundSearchBo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【o_supplier_refund(供应商售后表)】的数据库操作Service
* @createDate 2025-02-22 11:22:40
*/
public interface ErpWarehouseSalesAfterService extends IService<ErpWarehouseSalesAfter> {
    PageResult<ErpWarehouseSalesAfter> queryPageList(RefundSearchBo bo, PageQuery pageQuery);
    /**
     * 售后处理
     * @param processingBo
     * @return
     */
    ResultVo<Long> refundProcessing(RefundProcessingBo processingBo, String createBy,Long supplierId);
}
