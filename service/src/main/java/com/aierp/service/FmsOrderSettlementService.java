package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.model.entity.FmsOrderSettlement;
import com.aierp.model.entity.OOrder;
import com.aierp.model.query.OrderSettlementQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

public interface FmsOrderSettlementService {

    PageResult<FmsOrderSettlement> queryPageList(OrderSettlementQuery query, PageQuery pageQuery);

    List<OOrder> getUnsettledOrders(OrderSettlementQuery query, Page<OOrder> page);

    List<FmsOrderSettlement> getSettledOrders(OrderSettlementQuery query, Page<FmsOrderSettlement> page);

    Map<String, Object> autoSettlement(List<String> orderIds, String operator);

    Map<String, Object> manualSettlement(String orderId, String settlementId, String operator);

    boolean cancelSettlement(Long id, String operator);

    FmsOrderSettlement getSettlementDetail(Long id);

    FmsOrderSettlement calculateOrderSettlement(String orderId);
}
