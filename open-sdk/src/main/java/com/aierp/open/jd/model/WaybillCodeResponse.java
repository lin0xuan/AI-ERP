package com.aierp.open.jd.model;

import lombok.Data;

import java.util.List;

@Data
public class WaybillCodeResponse {
    private String platformOrderNo;
    private List<String> waybillCodeList;
}
