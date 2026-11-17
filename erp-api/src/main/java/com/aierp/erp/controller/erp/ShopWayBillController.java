package com.aierp.erp.controller.erp;

import com.aierp.common.AjaxResult;
import com.aierp.security.common.BaseController;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequestMapping("/api/erp-api/ewaybill")
@RestController
@AllArgsConstructor
public class ShopWayBillController extends BaseController {

    /**
     * 获取电子面单账户
     * @param
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "/api/erp-api/get_waybill_account_list", method = RequestMethod.GET)
    public AjaxResult getWaybillAccountList() throws Exception {
        return AjaxResult.error("开源版本不支持电子面单相关功能");
    }

    /**
     * 拉取电子面单账户
     * @param
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "/api/erp-api/pull_waybill_account", method = RequestMethod.POST)
    public AjaxResult pull_waybill_account() throws Exception {
        return AjaxResult.error("开源版本不支持电子面单相关功能");
    }

    @RequestMapping(value = "/api/erp-api/updateAccount", method = RequestMethod.POST)
    public AjaxResult updateAccount(  ) throws Exception {
        return AjaxResult.error("开源版本不支持电子面单相关功能");
    }

}
