package com.aierp.erp.controller.erp;

import com.aierp.common.AjaxResult;
import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.TableDataInfo;
import com.aierp.model.entity.ErpPurchaseOrder;
import com.aierp.model.request.PurchaseOrderAddRequest;
import com.aierp.model.request.PurchaseOrderOptionRequest;
import com.aierp.model.request.SearchRequest;
import com.aierp.security.common.BaseController;
import com.aierp.service.ErpPurchaseOrderService;
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
public class PurchaseOrderController extends BaseController
{
    private final ErpPurchaseOrderService erpPurchaseOrderService;
    /**
     *
     */
    @GetMapping("/list")
    public TableDataInfo list(SearchRequest bo, PageQuery pageQuery)
    {
        PageResult<ErpPurchaseOrder> pageResult = erpPurchaseOrderService.queryPageList(bo, pageQuery);
        return getDataTable(pageResult);
    }

    @GetMapping(value = "/detail/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        ErpPurchaseOrder detail = erpPurchaseOrderService.getDetailById(id);
        return AjaxResult.success(detail);
    }

    @PostMapping("/create")
    public AjaxResult add(@RequestBody PurchaseOrderAddRequest addBo, HttpServletRequest request)
    {
        addBo.setCreateBy(getUsername());
        var result = erpPurchaseOrderService.createPurchaseOrder(addBo);
        if (result.getCode() == 0) return AjaxResult.success();
        else return AjaxResult.error(result.getMsg());
    }
    @PutMapping("/updateStatus")
    public AjaxResult updateStatus(@RequestBody PurchaseOrderOptionRequest req, HttpServletRequest request)
    {
        req.setUpdateBy(getUsername());
        int result = erpPurchaseOrderService.updateScmPurchaseOrder(req);
        if(result == -1){
            return new AjaxResult(0,"状态不正确");
        }else{
            return toAjax(result);
        }

    }
}
