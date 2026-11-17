package com.aierp.service;

import com.aierp.model.dto.ImportResult;
import com.aierp.model.entity.FmsExpense;

import java.util.List;

public interface PlatformBillImportService {

    List<String> queryPlatforms();

    ImportResult saveExpensesFromBillImport(List<FmsExpense> expenses, String operator);
}
