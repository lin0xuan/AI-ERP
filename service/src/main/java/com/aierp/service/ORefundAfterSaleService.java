package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;

import com.aierp.common.ResultVo;
import com.aierp.model.request.AfterSalesExchangeConfirmRequest;
import com.aierp.model.request.AfterSalesReturnedAndStockInRequest;
import com.aierp.model.request.AfterSalesShipAgainConfirmRequest;
import com.aierp.model.entity.ORefundAfterSale;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【o_after_sale(OMS售后处理表)】的数据库操作Service
* @createDate 2024-09-15 21:30:30
*/
public interface ORefundAfterSaleService extends IService<ORefundAfterSale> {
    PageResult<ORefundAfterSale> queryPageList(ORefundAfterSale bo, PageQuery pageQuery);

    /**
     * 退货确认收货并入库
     * @return
     */
    ResultVo<Long> returnedConfirmAndStockIn(AfterSalesReturnedAndStockInRequest request, Long userId);

    /**
     * 补发确认
     * @param request
     * @param userId
     * @return
     */
    ResultVo<Long> shipAgainConfirm(AfterSalesShipAgainConfirmRequest request, Long userId);
    ResultVo<Long> exchangeConfirm(AfterSalesExchangeConfirmRequest request, Long userId);
}
