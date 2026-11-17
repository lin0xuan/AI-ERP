package com.aierp.service.impl;

import com.aierp.mapper.OGoodsInventoryBatchMapper;
import com.aierp.model.entity.OGoodsInventoryBatch;
import com.aierp.service.OGoodsInventoryBatchService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 主系统商品库存批次 Service 实现
 *
 * @author qihang
 */
@AllArgsConstructor
@Service
public class OGoodsInventoryBatchServiceImpl
        extends ServiceImpl<OGoodsInventoryBatchMapper, OGoodsInventoryBatch>
        implements OGoodsInventoryBatchService {

    @Override
    public List<OGoodsInventoryBatch> listByInventoryId(Long inventoryId) {
        return this.list(new LambdaQueryWrapper<OGoodsInventoryBatch>()
                .eq(OGoodsInventoryBatch::getInventoryId, inventoryId));
    }

    @Override
    public boolean deductBatchStock(Long batchId, Integer quantity) {
        OGoodsInventoryBatch batch = this.getById(batchId);
        if (batch == null || batch.getCurrentQty() < quantity) return false;

        OGoodsInventoryBatch update = new OGoodsInventoryBatch();
        update.setId(batchId);
        update.setCurrentQty(batch.getCurrentQty() - quantity);
        return this.updateById(update);
    }
}