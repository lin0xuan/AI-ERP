package com.aierp.erp.controller.erp;

import com.aierp.common.AjaxResult;
import com.aierp.common.PageQuery;
import com.aierp.common.ResultVo;
import com.aierp.common.TableDataInfo;
import com.aierp.model.entity.ErpStockOut;
import com.aierp.model.request.StockOutCreateRequest;
import com.aierp.model.request.StockOutItemRequest;
import com.aierp.security.common.BaseController;
import com.aierp.service.ErpStockOutService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/stockOut")
public class StockOutController extends BaseController {
    private final ErpStockOutService stockOutService;

    @GetMapping("/list")
    public TableDataInfo list(ErpStockOut bo, PageQuery pageQuery)
    {
        var pageList = stockOutService.queryPageList(bo,pageQuery);
        return getDataTable(pageList);
    }



    @PostMapping("/create")
    public AjaxResult createEntry(@RequestBody StockOutCreateRequest request)
    {
        ResultVo<Long> resultVo = stockOutService.createEntry(getUserId(), getUsername(), request);
        if(resultVo.getCode()==0)
            return AjaxResult.success();
        else return AjaxResult.error(resultVo.getMsg());
    }

    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        ErpStockOut entry = stockOutService.getDetailAndItemById(id);
        return success(entry);
    }
    @GetMapping(value = "/print/{id}")
    public AjaxResult print(@PathVariable("id") Long id)
    {
        ErpStockOut out = new ErpStockOut();
        out.setId(id);
        out.setPrintStatus(1);
        out.setPrintTime(LocalDateTime.now());
        stockOutService.updateById(out);
        return AjaxResult.success();
    }
    @PostMapping("/out")
    public AjaxResult out(@RequestBody StockOutItemRequest request)
    {
        ResultVo<Long> resultVo = stockOutService.stockOut(getUserId(), getUsername(), request);
        if(resultVo.getCode()==0)
            return AjaxResult.success();
        else return AjaxResult.error(resultVo.getMsg());
    }
}
