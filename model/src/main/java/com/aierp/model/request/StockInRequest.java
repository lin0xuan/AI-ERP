package com.aierp.model.request;

import lombok.Data;

import java.util.List;

@Data
public class StockInRequest {
    private Long stockInId;
    private Long warehouseId;
    private String stockInOperator;
    private List<StockInItem> itemList;
}
