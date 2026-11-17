package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;
import com.aierp.model.entity.ErpStockOut;
import com.aierp.model.request.StockOutCreateRequest;
import com.aierp.model.request.StockOutItemRequest;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【wms_stock_out(出库单)】的数据库操作Service
* @createDate 2024-09-22 11:13:23
*/
public interface ErpStockOutService extends IService<ErpStockOut> {
    PageResult<ErpStockOut> queryPageList(ErpStockOut bo, PageQuery pageQuery);
    ResultVo<Long> createEntry(Long userId, String userName, StockOutCreateRequest request);

    ErpStockOut getDetailAndItemById(Long id);

    ResultVo<Long> stockOut(Long userId,String userName,StockOutItemRequest request);
}
