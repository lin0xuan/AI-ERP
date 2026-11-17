package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;
import com.aierp.model.entity.OOrderStockingItem;
import com.aierp.model.bo.ShipStockUpCompleteBo;
import com.aierp.model.bo.StockingOrderItemBo;
import com.aierp.model.bo.SupplierShipOrderItemListBo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author qilip
* @description 针对表【o_supplier_ship_order_item】的数据库操作Service
* @createDate 2025-02-18 13:48:13
*/
public interface OOrderStockingItemService extends IService<OOrderStockingItem> {
    /**
     * 查询供应商备货清单
     * @param bo
     * @param pageQuery
     * @return
     */
    PageResult<OOrderStockingItem> queryVendorStockingPageList(SupplierShipOrderItemListBo bo, PageQuery pageQuery);

    /**
     * 导出excel数据
     * @param bo
     * @return
     */
    List<OOrderStockingItem> queryAllList2(SupplierShipOrderItemListBo bo );

    /**
     * 查询仓库备货清单
     * @param bo
     * @param pageQuery
     * @return
     */
    PageResult<OOrderStockingItem> queryStockingPageList(StockingOrderItemBo bo, PageQuery pageQuery);

    /**
     * 生成出库单()
     * @param bo
     * @return
     */
    ResultVo generateStockOutByItem(ShipStockUpCompleteBo bo);

    ResultVo generateStockOutByOrder(ShipStockUpCompleteBo bo);


}
