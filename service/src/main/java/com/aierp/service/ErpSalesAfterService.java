package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;

import com.aierp.model.entity.ErpSalesAfter;
import com.aierp.request.RefundSearchRequest;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【offline_refund(线下渠道退款表)】的数据库操作Service
* @createDate 2024-09-16 13:31:26
*/
public interface ErpSalesAfterService extends IService<ErpSalesAfter> {
    PageResult<ErpSalesAfter> queryPageList(RefundSearchRequest bo, PageQuery pageQuery);
}
