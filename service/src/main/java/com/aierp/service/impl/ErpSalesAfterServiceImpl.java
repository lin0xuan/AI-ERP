package com.aierp.service.impl;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.model.entity.ErpSalesAfter;
import com.aierp.mapper.ErpSalesOrderAfterMapper;
import com.aierp.service.ErpSalesAfterService;
import com.aierp.request.RefundSearchRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
* @author qilip
* @description 针对表【offline_refund(线下渠道退款表)】的数据库操作Service实现
* @createDate 2024-09-16 13:31:26
*/
@AllArgsConstructor
@Service
public class ErpSalesAfterServiceImpl extends ServiceImpl<ErpSalesOrderAfterMapper, ErpSalesAfter>
    implements ErpSalesAfterService {
    private final ErpSalesOrderAfterMapper refundMapper;

    @Override
    public PageResult<ErpSalesAfter> queryPageList(RefundSearchRequest bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ErpSalesAfter> queryWrapper = new LambdaQueryWrapper<ErpSalesAfter>()
                .eq(bo.getShopId()!=null, ErpSalesAfter::getShopId,bo.getShopId())
                .eq(bo.getMerchantId()!=null, ErpSalesAfter::getMerchantId,bo.getMerchantId())
                .eq(StringUtils.hasText(bo.getRefundNum()), ErpSalesAfter::getRefundNum,bo.getRefundNum())
                .eq(StringUtils.hasText(bo.getSkuCode()), ErpSalesAfter::getSkuNum,bo.getSkuCode())
                .eq(StringUtils.hasText(bo.getOrderNum()), ErpSalesAfter::getOrderNum,bo.getOrderNum());

        Page<ErpSalesAfter> pages = refundMapper.selectPage(pageQuery.build(), queryWrapper);

        return PageResult.build(pages);
    }
}




