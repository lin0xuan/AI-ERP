package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.model.entity.ErpSalesGoodsPackage;
import com.aierp.model.entity.ErpSalesGoodsPackageItem;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ErpSalesGoodsPackageService extends IService<ErpSalesGoodsPackage> {
    PageResult<ErpSalesGoodsPackage> queryPageList(PageQuery pageQuery);

    boolean addPackage(ErpSalesGoodsPackage pkg);

    Long addPackageAndGetId(ErpSalesGoodsPackage pkg);

    boolean updatePackage(ErpSalesGoodsPackage pkg);

    boolean deletePackage(Long id);

    ErpSalesGoodsPackage getPackageById(Long id);

    List<ErpSalesGoodsPackageItem> getPackageItems(Long packageId);

    boolean savePackageItems(Long packageId, List<ErpSalesGoodsPackageItem> items);
}