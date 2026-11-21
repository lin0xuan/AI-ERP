package com.aierp.erp.datacenter;

import com.alibaba.fastjson2.JSON;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.time.LocalDate;

/** Per-turn instance: permission captured on authenticated request thread, never inferred from the model. */
public class DataCenterTools {
    private final DataCenterService service;
    private final boolean allowed;
    public DataCenterTools(DataCenterService service,boolean allowed) {this.service=service;this.allowed=allowed;}
    @Tool(description="查询与数据中台看板完全相同的支付金额、成功退款、净销售额、库存和采购指标。销售汇总必须使用本工具，不要对截断的订单列表求和。返回口径、快照批次和质量缺口。")
    public String getDataCenterMetrics(
            @ToolParam(description="起始日期，YYYY-MM-DD") String from,
            @ToolParam(description="结束日期，YYYY-MM-DD，包含当日") String to,
            @ToolParam(description="统一店铺编码，可传空字符串查询全域",required=false) String shop,
            @ToolParam(description="看板快照batchId；为空时读取最新成功快照",required=false) String batch) {
        if(!allowed) return "没有全域数据中台读取权限，请管理员授权 data:center:read。";
        if(from==null || to==null) return "指标不可用：请提供起止日期 YYYY-MM-DD。";
        try {return JSON.toJSONString(service.metrics(LocalDate.parse(from),LocalDate.parse(to),shop,batch));}
        catch(java.time.DateTimeException | IllegalArgumentException ex) {return "指标不可用："+ex.getMessage();}
    }
}
