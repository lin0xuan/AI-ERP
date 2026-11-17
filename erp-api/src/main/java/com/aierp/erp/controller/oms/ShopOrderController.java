package com.aierp.erp.controller.oms;

import com.aierp.common.AjaxResult;
import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;
import com.aierp.common.TableDataInfo;
import com.aierp.erp.service.ShopPullApiService;
import com.aierp.model.bo.ShopOrderCreateBo;
import com.aierp.model.entity.ShopOrder;
import com.aierp.model.entity.ShopOrderItem;
import com.aierp.model.query.ShopOrderQueryBo;
import com.aierp.model.request.OrderPullRequest;
import com.aierp.security.common.BaseController;
import com.aierp.service.ShopOrderItemService;
import com.aierp.erp.serviceImpl.ShopSyncService;
import com.aierp.service.ShopOrderService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/oms-api/shop/order")
public class ShopOrderController extends BaseController {

    private final ShopOrderService shopOrderService;
    private final ShopOrderItemService shopOrderItemService;
    private final ShopSyncService shopSyncService;
    private final ShopPullApiService shopPullApiService;

    /**
     * 查询店铺订单列表
     */
    @PostMapping("/create")
    public AjaxResult create(@RequestBody ShopOrderCreateBo order) {
        if (order.getShopId() == null) return AjaxResult.error("请选择店铺");
        if (order.getItemList() == null || order.getItemList().isEmpty()) return AjaxResult.error("请添加商品");
        ResultVo<Long> result = shopOrderService.createOrder(order, getUsername());
        if (result.getCode() == 0) return AjaxResult.success(result.getData());
        return AjaxResult.error(result.getMsg());
    }

    @GetMapping("/list")
    public TableDataInfo list(ShopOrderQueryBo bo, PageQuery pageQuery) {
        PageResult<ShopOrder> pageList = shopOrderService.queryOrderPageList(bo, pageQuery);
        return getDataTable(pageList);
    }

    /**
     * 查询店铺订单明细列表
     */
    @GetMapping("/item_list")
    public TableDataInfo itemList(ShopOrderQueryBo bo, PageQuery pageQuery) {
        PageResult<ShopOrderItem> pageList = shopOrderItemService.queryPageList(bo, pageQuery);
        return getDataTable(pageList);
    }

    /**
     * 拉取店铺订单列表（按下单日期，根据 shopType 路由到对应平台 SDK）。
     * 前端：{@code /api/oms-api/shop/order/pull_list}，参数 {shopId, startTime, endTime}
     */
    @PostMapping("/pull_list")
    public AjaxResult pullList(@RequestBody OrderPullRequest req) {
        ResultVo<String> result = shopPullApiService.pullOrder(req.getShopId(), req.getStartTime(), req.getEndTime());
        if (result.getCode() == 0) {
            return AjaxResult.success(result.getMsg());
        }
        return AjaxResult.error(result.getCode(), result.getMsg());
    }

    /**
     * 拉取单个订单详情并同步。
     * 前端：{@code /api/oms-api/shop/order/pull_detail}，参数 {shopId, orderId}
     */
    @PostMapping("/pull_detail")
    public AjaxResult pullDetail(@RequestBody OrderPullRequest req) {
        ResultVo<String> result = shopPullApiService.pullOrderDetail(req.getShopId(), req.getOrderId());
        if (result.getCode() == 0) {
            return AjaxResult.success(result.getMsg());
        }
        return AjaxResult.error(result.getCode(), result.getMsg());
    }

    /**
     * 推送到 OMS（同步 oms_shop_order → o_order）。
     * 前端：{@code /api/oms-api/shop/order/push_oms}，参数 {ids:[shopOrderId...]}
     */
    @PostMapping("/push_oms")
    public AjaxResult pushOms(@RequestBody Map<String, Object> body) {
        Object idsObj = body.get("ids");
        if (idsObj == null) {
            return AjaxResult.error("参数错误，缺少ids");
        }
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) idsObj;
        int success = 0, fail = 0;
        for (Object id : ids) {
            try {
                Long shopOrderId = Long.parseLong(String.valueOf(id));
                ResultVo<Long> r = shopSyncService.syncOrder(shopOrderId);
                if (r.getCode() == 0) success++;
                else fail++;
            } catch (Exception e) {
                log.error("推送订单到OMS异常：{}", id, e);
                fail++;
            }
        }
        return AjaxResult.success("同步完成，成功：" + success + "条，失败：" + fail + "条");
    }
}
