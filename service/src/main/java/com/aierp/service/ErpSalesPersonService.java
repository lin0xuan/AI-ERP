package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;
import com.aierp.model.entity.ErpSalesPerson;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 1
* @description 针对表【erp_sales_person(销售人员表)】的数据库操作Service
* @createDate 2026-04-09 20:41:48
*/
public interface ErpSalesPersonService extends IService<ErpSalesPerson> {
    PageResult<ErpSalesPerson> queryPageList(ErpSalesPerson bo, PageQuery pageQuery);
    ResultVo<Long> addSalesPerson(ErpSalesPerson erpSalesPerson);
    ResultVo<Long> update(ErpSalesPerson bo);
}
