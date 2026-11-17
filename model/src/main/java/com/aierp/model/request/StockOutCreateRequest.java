package com.aierp.model.request;

import com.aierp.model.vo.GoodsSkuInventoryVo;
import lombok.Data;

import java.util.List;

@Data
public class StockOutCreateRequest {
    private Long shopId;
    private Long shopGroupId;
    private Long warehouseId;
    private String outNum;
    private Integer type;
    private String sourceNo;
    private String operator;
    private String remark;
    private Long merchantId;
    private Long vendorId;
    private List<GoodsSkuInventoryVo> itemList;
}
