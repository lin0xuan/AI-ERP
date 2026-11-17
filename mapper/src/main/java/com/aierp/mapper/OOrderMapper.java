package com.aierp.mapper;

import com.aierp.model.entity.OOrder;
import com.aierp.model.vo.OrderDiscountVo;
import com.aierp.model.vo.SalesDailyVo;
import com.aierp.model.vo.WaitShipReportVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;


import java.util.List;

/**
* @author TW
* @description 针对表【o_order(订单表)】的数据库操作Mapper
* @createDate 2024-03-11 14:24:49
* @Entity com.aierp.model.entity.OOrder
*/
public interface OOrderMapper extends BaseMapper<OOrder> {
    List<OrderDiscountVo> getTaoOrderDiscount(String tid);
    List<OrderDiscountVo> getJdOrderDiscount(String orderId);


    List<SalesDailyVo> salesDaily(@Param("merchantId") Long merchantId,@Param("shopId") Long shopId);
    /**
     * 待发货统计
     * @param merchantId
     * @return
     */
    List<WaitShipReportVo> waitOrderReport(@Param("merchantId") Long merchantId);
    SalesDailyVo getTodaySalesDaily(@Param("merchantId") Long merchantId);
    Integer getWaitShipOrderAllCount(@Param("merchantId") Long merchantId);

}




