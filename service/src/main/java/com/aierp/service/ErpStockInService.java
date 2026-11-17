package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;

import com.aierp.model.entity.ErpStockIn;
import com.aierp.model.request.StockInCreateRequest;
import com.aierp.model.request.StockInItemInRequest;
import com.aierp.model.request.StockInLocalRequest;
import com.aierp.model.request.StockInRequest;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【wms_stock_in(入库单)】的数据库操作Service
* @createDate 2024-09-22 16:10:08
*/
public interface ErpStockInService extends IService<ErpStockIn> {
    PageResult<ErpStockIn> queryPageList(ErpStockIn bo, PageQuery pageQuery);
    PageResult<ErpStockIn> queryCloudWarehouseStockInPageList(ErpStockIn bo, PageQuery pageQuery);
    ResultVo<Long> createEntry(Long userId, String userName, StockInCreateRequest request);
    ResultVo<Long> stockIn(Long userId, String userName, StockInRequest request);

    ResultVo<Long> StockInItemIn(Long userId, String userName, StockInItemInRequest request);

    /**
     * 本地仓 入库操作
     * @param userId
     * @param userName
     * @param request
     * @return
     */
    ResultVo<Long> localStockIn(StockInLocalRequest request, Long userId, String userName );


    ErpStockIn getDetailAndItemById(Long id);
}
