package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;
import com.aierp.model.entity.ErpSupplierCustomer;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ErpSupplierCustomerService extends IService<ErpSupplierCustomer> {
    PageResult<ErpSupplierCustomer> queryPageList(ErpSupplierCustomer bo, PageQuery pageQuery);
    
    ResultVo updateStatus(Long id, Integer status);
}
