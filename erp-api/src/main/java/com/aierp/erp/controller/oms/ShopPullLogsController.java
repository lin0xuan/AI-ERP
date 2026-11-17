package com.aierp.erp.controller.oms;

import com.aierp.common.PageQuery;
import com.aierp.common.TableDataInfo;
import com.aierp.model.entity.OShopPullLogs;
import com.aierp.security.common.BaseController;
import com.aierp.service.OShopPullLogsService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺拉取日志 Controller
 * 对应前端 /api/oms-api/shop/pull_logs_list
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/oms-api/shop")
public class ShopPullLogsController extends BaseController {
    private final OShopPullLogsService pullLogsService;

    @GetMapping("/pull_logs_list")
    public TableDataInfo list(OShopPullLogs logs, PageQuery pageQuery) {
        var pageList = pullLogsService.queryPageList(logs, pageQuery);
        return getDataTable(pageList);
    }
}