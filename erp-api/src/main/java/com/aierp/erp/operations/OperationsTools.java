package com.aierp.erp.operations;

import com.alibaba.fastjson2.JSON;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Read-only; approval and posting are always explicit user actions in the workbench. */
public class OperationsTools {
    private final OperationsService service;
    private final OperationsService.Actor actor;
    public OperationsTools(OperationsService service,OperationsService.Actor actor) {this.service=service;this.actor=actor;}
    @Tool(description="按当前用户商户/店铺/仓库授权查询库存与成本。成本为空表示未知，禁止将其当作零成本。每页50条，不能对单页求和冒充全域统计。")
    public String getScopedInventory(@ToolParam(description="页码，从1开始") int page) {return JSON.toJSONString(service.inventories(actor,page));}
    @Tool(description="查询当前用户有权查看的补货建议，包含库存、工作台采购在途与计算依据。仅供参考，不会自动采购。")
    public String getReplenishmentSuggestions(@ToolParam(description="页码，从1开始") int page) {return JSON.toJSONString(service.replenishment(actor,page));}
    @Tool(description="查询库存账本，追溯每次数量与成本变动。每页50条。")
    public String getStockLedger(@ToolParam(description="库存记录ID") long inventoryId,@ToolParam(description="页码") int page) {return JSON.toJSONString(service.ledger(actor,inventoryId,page));}
    @Tool(description="查询当前用户有权查看的采购及库存单据、审批状态与入库进度。每页50条。")
    public String getOperationDocuments(@ToolParam(description="页码") int page) {return JSON.toJSONString(service.documents(actor,page));}
}
