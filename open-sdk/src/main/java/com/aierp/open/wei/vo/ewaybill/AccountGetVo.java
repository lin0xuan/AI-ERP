package com.aierp.open.wei.vo.ewaybill;

import com.aierp.open.wei.vo.BaseResVo;
import lombok.Data;

import java.util.List;

@Data
public class AccountGetVo extends BaseResVo {
    private Integer total_num;
    private List<AccountVo> account_list;
}
