//package com.aierp.open.wei.service;
//
//
//import com.aierp.open.wei.bo.ewaybill.AccountGetApiBo;
//import com.aierp.open.wei.bo.ewaybill.DeliveryGetApiBo;
//import com.aierp.open.wei.bo.ewaybill.WaybillRequest;
//import com.aierp.open.wei.vo.ewaybill.AccountGetVo;
//import com.aierp.open.wei.vo.ewaybill.DeliveryGetVo;
//import com.aierp.open.wei.vo.ewaybill.EwaybillOrderCreateVo;
//import com.aierp.open.wei.vo.ewaybill.EwaybillOrderPreCreateVo;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.service.annotation.HttpExchange;
//import org.springframework.web.service.annotation.PostExchange;
//
//@HttpExchange
//public interface WeiWaybillApiService {
//    /**
//     * 查询开通的快递公司
//     * @param access_token
//     * @param bo
//     * @return
//     */
//    @PostExchange("/channels/ec/logistics/ewaybill/biz/delivery/get")
//    DeliveryGetVo getDeliveryList(@RequestParam(name = "access_token") String access_token, @RequestBody DeliveryGetApiBo bo);
//
//    /**
//     * 查询开通的网点账号信息
//     * @param access_token
//     * @param bo
//     * @return
//     */
//    @PostExchange("/channels/ec/logistics/ewaybill/biz/account/get")
//    AccountGetVo getEwaybillAccount(@RequestParam(name = "access_token") String access_token, @RequestBody AccountGetApiBo bo);
//
//    /**
//     * 电子面单预取号
//     *
//     * @param access_token
//     * @param bo
//     * @return
//     */
//    @PostExchange("/channels/ec/logistics/ewaybill/biz/order/precreate")
//    EwaybillOrderPreCreateVo ewaybillOrderPreCreate(@RequestParam(name = "access_token") String access_token, @RequestBody WaybillRequest bo);
//
//    @PostExchange("/channels/ec/logistics/ewaybill/biz/order/create")
//    EwaybillOrderCreateVo ewaybillOrderCreate(@RequestParam(name = "access_token") String access_token, @RequestBody WaybillRequest bo);
//
//}
