package com.aierp.erp.controller.erp;

import com.aierp.erp.request.OrderItemSpecIdUpdateBo;
import com.aierp.common.AjaxResult;
import com.aierp.common.PageQuery;
import com.aierp.common.TableDataInfo;
import com.aierp.model.query.OrderItemQuery;
import com.aierp.security.common.BaseController;
import com.aierp.service.OOrderItemService;
import com.aierp.service.OOrderService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 店铺订单Controller
 *
 * @author qihang
 * @date 2023-12-31
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/order")
public class OrderItemController extends BaseController
{
    private final OOrderItemService itemService;
    private final OOrderService orderService;

    /**
     * 查询店铺订单列表
     */
    @GetMapping("/item_list")
    public TableDataInfo list(OrderItemQuery bo, PageQuery pageQuery)
    {
        var pageList = itemService.selectPageVo(pageQuery,bo);
        return getDataTable(pageList);
    }
    /**
     * 修改订单明细specId
     * @param bo
     * @return
     */
    @PostMapping("/order_item_sku_id_update")
    public AjaxResult orderItemSpecIdUpdate(@RequestBody OrderItemSpecIdUpdateBo bo)
    {
        if(bo.getOrderItemId()==null || bo.getOrderItemId() ==0) return AjaxResult.error("参数错误：orderItemId为空");
        if(bo.getErpGoodsSpecId()==null || bo.getErpGoodsSpecId() ==0) return AjaxResult.error("参数错误：ErpGoodsSpecId为空");

        var result = itemService.updateErpSkuId(bo.getOrderItemId(),bo.getErpGoodsSpecId().toString());
        if (result.getCode() == 0) {
            return AjaxResult.success();
        } else return AjaxResult.error(result.getMsg());

    }

}
