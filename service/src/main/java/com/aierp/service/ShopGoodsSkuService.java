package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;
import com.aierp.model.bo.BindErpGoodsSkuBo;
import com.aierp.model.bo.LinkErpGoodsSkuBo;

import com.aierp.model.entity.ShopGoodsSku;
import com.aierp.model.query.ShopGoodsSkuBo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author qilip
* @description 针对表【oms_shop_goods_sku(其他渠道店铺商品SKU)】的数据库操作Service
* @createDate 2025-07-15 08:29:21
*/
public interface ShopGoodsSkuService extends IService<ShopGoodsSku> {
    PageResult<ShopGoodsSku> queryPageList(ShopGoodsSkuBo bo, PageQuery pageQuery);
    PageResult<ShopGoodsSku> queryBenShuPageList(ShopGoodsSkuBo bo, PageQuery pageQuery);
    List<ShopGoodsSku> querySkuList(Long shopGoodsId);
    ResultVo linkErpGoodsSku(LinkErpGoodsSkuBo bo);

    /**
     * 查询平台skuid是否存在商品
     * @param platformSkuId
     * @param shopId
     * @return
     */
    ShopGoodsSku selectByPlatformSkuId(String platformSkuId,Long shopId);
    /**
     * 虚拟发货商品配置发货商品sku
     * @param bo
     * @return
     */
    ResultVo bindErpGoodsSku(BindErpGoodsSkuBo bo);
}
