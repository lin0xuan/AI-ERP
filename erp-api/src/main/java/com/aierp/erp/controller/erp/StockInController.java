package com.aierp.erp.controller.erp;

import com.aierp.common.AjaxResult;
import com.aierp.common.PageQuery;
import com.aierp.common.ResultVo;
import com.aierp.common.TableDataInfo;
import com.aierp.model.entity.ErpStockIn;
import com.aierp.model.request.StockInCreateRequest;
import com.aierp.model.request.StockInRequest;

import com.aierp.security.common.BaseController;
import com.aierp.service.ErpStockInService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/stockIn")
public class StockInController extends BaseController {
    private final ErpStockInService stockInService;
    @GetMapping("/list")
    public TableDataInfo list(ErpStockIn bo, PageQuery pageQuery)
    {
        var pageList = stockInService.queryPageList(bo,pageQuery);
        return getDataTable(pageList);
    }

    @PostMapping("/create")
    public AjaxResult createEntry(@RequestBody StockInCreateRequest request)
    {
        ResultVo<Long> resultVo = stockInService.createEntry(getUserId(), getUsername(), request);
        if(resultVo.getCode()==0)
            return AjaxResult.success();
        else return AjaxResult.error(resultVo.getMsg());
    }

    @PostMapping("/in")
    public AjaxResult in(@RequestBody StockInRequest request)
    {
        ResultVo<Long> resultVo = stockInService.stockIn(getUserId(), getUsername(), request);
        if(resultVo.getCode()==0)
            return AjaxResult.success();
        else return AjaxResult.error(resultVo.getMsg());
    }

    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        ErpStockIn entry = stockInService.getDetailAndItemById(id);

        return success(entry);
    }

}
