package com.aierp.erp.controller.erp;

import com.aierp.common.AjaxResult;
import com.aierp.common.PageQuery;
import com.aierp.common.TableDataInfo;
import com.aierp.common.ResultVo;
import com.aierp.model.entity.ErpPurchaseOrderShip;
import com.aierp.model.request.PurchaseOrderStockInBo;
import com.aierp.model.request.SearchRequest;
import com.aierp.security.common.BaseController;
import com.aierp.security.common.SecurityUtils;
import com.aierp.service.ErpPurchaseOrderShipService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 商品管理Controller
 * 
 * @author qihang
 * @date 2023-12-29
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/erp/purchase")
public class PurchaseOrderShipController extends BaseController
{
    private final ErpPurchaseOrderShipService shipService;
    /**
     *
     */
    @GetMapping("/shipList")
    public TableDataInfo shipList(SearchRequest bo, PageQuery pageQuery)
    {
        return getDataTable(shipService.queryPageList(bo, pageQuery));
    }
    @GetMapping(value = "/shipDetail/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        ErpPurchaseOrderShip detail = shipService.getById(id);
        return AjaxResult.success(detail);
    }
    @PutMapping("/ship/confirmReceipt")
    public AjaxResult confirmReceipt(@RequestBody ErpPurchaseOrderShip erpPurchaseOrderShip, HttpServletRequest request)
    {
        erpPurchaseOrderShip.setUpdateBy(getUsername());
        return toAjax(shipService.updateScmPurchaseOrderShip(erpPurchaseOrderShip));
    }

    @PostMapping("/ship/createStockInEntry")
    public AjaxResult createStockInEntry(@RequestBody PurchaseOrderStockInBo bo)
    {
        if(bo.getId() == null) return AjaxResult.error("缺少参数id");
        if(bo.getWarehouseId()==null) return AjaxResult.error("请选择仓库");

        bo.setCreateBy(getUsername());
        ResultVo<Long> result = shipService.createStockInEntry(bo, SecurityUtils.getUserId(),SecurityUtils.getUsername());
        if(result.getCode()==0)
            return AjaxResult.success();
        else
            return AjaxResult.error(result.getMsg());
    }

}
