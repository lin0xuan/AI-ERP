package com.aierp.service;

import com.aierp.common.PageResult;
import com.aierp.common.PageQuery;
import com.aierp.model.entity.FmsFinanceLedger;
import com.aierp.model.query.FinanceLedgerQuery;
import com.baomidou.mybatisplus.extension.service.IService;

public interface FmsFinanceLedgerService extends IService<FmsFinanceLedger> {
    PageResult<FmsFinanceLedger> queryPageList(FinanceLedgerQuery query, PageQuery pageQuery);

    boolean add(FmsFinanceLedger ledger);

    boolean updateLedger(FmsFinanceLedger ledger);

    boolean deleteLedger(Long id);

    FmsFinanceLedger getById(Long id);

    FmsFinanceLedger getByVoucherNo(String voucherNo);
}