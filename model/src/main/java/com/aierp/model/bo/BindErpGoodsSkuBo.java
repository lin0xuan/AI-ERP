package com.aierp.model.bo;

import com.aierp.model.entity.OGoodsSku;
import lombok.Data;

import java.util.List;

@Data
public class BindErpGoodsSkuBo {
    private String id;//主键ID（店铺商品SKU主键ID）
    private List<OGoodsSku> skuList;
}
