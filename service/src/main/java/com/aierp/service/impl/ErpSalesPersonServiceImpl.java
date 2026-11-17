package com.aierp.service.impl;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.ResultVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aierp.model.entity.ErpSalesPerson;
import com.aierp.service.ErpSalesPersonService;
import com.aierp.mapper.ErpSalesPersonMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
* @author 1
* @description 针对表【erp_sales_person(销售人员表)】的数据库操作Service实现
* @createDate 2026-04-09 20:41:48
*/
@Service
public class ErpSalesPersonServiceImpl extends ServiceImpl<ErpSalesPersonMapper, ErpSalesPerson>
    implements ErpSalesPersonService{

    @Override
    public PageResult<ErpSalesPerson> queryPageList(ErpSalesPerson bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ErpSalesPerson> queryWrapper = new LambdaQueryWrapper<ErpSalesPerson>()
                .eq(bo.getStatus()!=null, ErpSalesPerson::getStatus, bo.getStatus())
                .eq(bo.getMerchantId()!=null, ErpSalesPerson::getMerchantId, bo.getMerchantId())
                .eq(bo.getShopId()!=null, ErpSalesPerson::getShopId, bo.getShopId());

        Page<ErpSalesPerson> pages = this.baseMapper.selectPage(pageQuery.build(), queryWrapper);
        return PageResult.build(pages);
    }

    @Override
    public ResultVo<Long> addSalesPerson(ErpSalesPerson erpSalesPerson) {
        List<ErpSalesPerson> erpSalesPeople = this.baseMapper.selectList(new LambdaQueryWrapper<ErpSalesPerson>().eq(ErpSalesPerson::getMobile, erpSalesPerson.getMobile()));
        if(!erpSalesPeople.isEmpty()) return ResultVo.error("手机号存在");
        erpSalesPeople = this.baseMapper.selectList(new LambdaQueryWrapper<ErpSalesPerson>().eq(ErpSalesPerson::getEmployeeNo, erpSalesPerson.getEmployeeNo()));
        if(!erpSalesPeople.isEmpty()) return ResultVo.error("工号存在");

        erpSalesPerson.setStatus(1);
        erpSalesPerson.setCreatedTime(LocalDateTime.now());
        this.baseMapper.insert(erpSalesPerson);
        return ResultVo.success(erpSalesPerson.getId());
    }

    @Override
    public ResultVo<Long> update(ErpSalesPerson bo) {
        if(bo.getId()==null) return ResultVo.error("ID不能为空");
        bo.setUpdatedTime(LocalDateTime.now());
        this.baseMapper.updateById(bo);
        return ResultVo.success(bo.getId());
    }
}




