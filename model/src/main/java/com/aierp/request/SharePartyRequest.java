package com.aierp.request;

import lombok.Data;

@Data
public class SharePartyRequest {
    private Integer pageNum;
    private Integer pageSize;
    private String partyName;
    private Integer partyType;
    private Integer status;
}
