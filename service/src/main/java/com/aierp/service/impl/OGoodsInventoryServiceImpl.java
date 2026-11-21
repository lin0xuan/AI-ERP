package com.aierp.service.impl;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.mapper.OGoodsInventoryMapper;
import com.aierp.model.entity.OGoodsInventory;
import com.aierp.service.OGoodsInventoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 主系统商品库存 Service 实现
 *
 * @author qihang
 */
@AllArgsConstructor
@Service
public class OGoodsInventoryServiceImpl
        extends ServiceImpl<OGoodsInventoryMapper, OGoodsInventory>
        implements OGoodsInventoryService {
    private final com.aierp.service.foundation.StockLedgerService ledger;

    @Override
    public PageResult<OGoodsInventory> queryPageList(OGoodsInventory bo, PageQuery pageQuery) {
        LambdaQueryWrapper<OGoodsInventory> qw = new LambdaQueryWrapper<OGoodsInventory>()
                .eq(bo.getIsDelete() != null, OGoodsInventory::getIsDelete, bo.getIsDelete())
                .eq(bo.getWarehouseId() != null, OGoodsInventory::getWarehouseId, bo.getWarehouseId())
                .eq(bo.getMerchantId() != null, OGoodsInventory::getMerchantId, bo.getMerchantId())
                .eq(bo.getSkuId() != null, OGoodsInventory::getSkuId, bo.getSkuId())
                .eq(bo.getGoodsId() != null, OGoodsInventory::getGoodsId, bo.getGoodsId())
                .eq(OGoodsInventory::getSkuCode, bo.getSkuCode())
                .like(bo.getGoodsName() != null, OGoodsInventory::getGoodsName, bo.getGoodsName());
        // 默认只查未删除的
        if (bo.getIsDelete() == null) {
            qw.eq(OGoodsInventory::getIsDelete, 0);
        }
        IPage<OGoodsInventory> page = this.baseMapper.selectPage(pageQuery.build(), qw);
        return PageResult.build(page);
    }

    @Override
    public boolean deductStock(Long inventoryId, Integer quantity) {
        return deductStock(inventoryId,quantity,java.util.UUID.randomUUID().toString());
    }

    @Override
    public boolean deductStock(Long inventoryId, Integer quantity, String businessKey) {
        positive(quantity);
        ledger.move(inventoryId,-quantity,0,null,businessKey,"LEGACY_ISSUE","system","原出库服务");
        return true;
    }

    @Override
    public boolean addStock(Long inventoryId, Integer quantity) {
        return addStock(inventoryId,quantity,java.util.UUID.randomUUID().toString());
    }

    @Override
    public boolean addStock(Long inventoryId, Integer quantity, String businessKey) {
        positive(quantity);
        ledger.move(inventoryId,quantity,0,null,businessKey,"LEGACY_RECEIPT","system","原入库服务，成本待核定");
        return true;
    }

    @Override
    public boolean lockStock(Long inventoryId, Integer quantity) {
        if(quantity==null || quantity==0) throw new IllegalArgumentException("锁定/释放数量不能为空或零");
        ledger.move(inventoryId,0,quantity,null,java.util.UUID.randomUUID().toString(),"LEGACY_RESERVATION","system","原锁库服务");
        return true;
    }
    private void positive(Integer quantity) {if(quantity==null || quantity<=0) throw new IllegalArgumentException("数量必须大于零");}
}
