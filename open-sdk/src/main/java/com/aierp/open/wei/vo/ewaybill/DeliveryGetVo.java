package com.aierp.open.wei.vo.ewaybill;

import com.aierp.open.wei.vo.BaseResVo;
import lombok.Data;

import java.util.List;

@Data
public class DeliveryGetVo extends BaseResVo {

    private List<DeliveryVo> list;

    private String shop_id;

}
