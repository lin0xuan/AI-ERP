package com.aierp.open.wei.response;

import lombok.Data;

@Data
public class WeiTokenResponse {
    private String access_token;
    private Long expires_in;
    private Integer errcode;
    private String errmsg;
}
