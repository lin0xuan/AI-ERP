package com.aierp.erp.controller.erp;

import com.aierp.common.AjaxResult;
import com.aierp.model.vo.SalesDailyVo;
import com.aierp.security.common.BaseController;
import com.aierp.service.OOrderItemService;
import com.aierp.service.OOrderService;
import com.aierp.service.OShopService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/report")
public class ReportController extends BaseController {
    private final OOrderService orderService;
    private final OOrderItemService orderItemService;
    private final OShopService shopService;

    @GetMapping("/todayDaily")
    public AjaxResult todayDaily()
    {
        Long shopCount = shopService.list().stream().count();
        Map<String,Object> result = new HashMap<>();
        // 今日销售
        SalesDailyVo todaySalesDaily = orderService.getTodaySalesDaily(0L);
        // 查询库存
//        Long allInventoryQuantity = inventoryService.getAllInventoryQuantity();
//        result.put("inventory",allInventoryQuantity.doubleValue());
        result.put("waitShip",orderService.getWaitShipOrderAllCount(0L));
        result.put("salesVolume",todaySalesDaily.getAmount());
        result.put("orderCount",todaySalesDaily.getCount().doubleValue());
        result.put("shopCount",shopCount.doubleValue());

        return AjaxResult.success(result);
    }


    @GetMapping("/salesDaily")
    public AjaxResult salesDaily()
    {
        List<SalesDailyVo> salesDailyVos = orderService.salesDaily();

        return AjaxResult.success(salesDailyVos);
    }

}
