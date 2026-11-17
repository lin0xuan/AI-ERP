package com.aierp.erp.controller.ai;

import com.aierp.common.AjaxResult;
import com.aierp.common.PageQuery;
import com.aierp.common.TableDataInfo;
import com.aierp.erp.serviceImpl.ai.AiAnalysisService;
import com.aierp.model.entity.AiAnalysisRecord;
import com.aierp.security.common.BaseController;
import com.aierp.service.IAiAnalysisRecordService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/erp-api/ai/analysis")
@RequiredArgsConstructor
public class AiAnalysisController extends BaseController {

    private final AiAnalysisService analysisService;
    private final IAiAnalysisRecordService recordService;

    @PostMapping("/sales")
    public AjaxResult sales() {
        return run("sales", null);
    }

    @PostMapping("/inventory")
    public AjaxResult inventory(@RequestBody(required = false) AnalysisBody body) {
        String text = body == null ? "" : join(body.getInventoryData(), body.getSalesData());
        return run("inventory", text);
    }

    @PostMapping("/customer")
    public AjaxResult customer(@RequestBody(required = false) AnalysisBody body) {
        String text = body == null ? "" : join(body.getCustomerData(), body.getOrderData());
        return run("customer", text);
    }

    @PostMapping("/operation")
    public AjaxResult operation(@RequestBody(required = false) AnalysisBody body) {
        return run("operation", body == null ? "" : body.getOperationData());
    }

    @PostMapping("/custom")
    public AjaxResult custom(@RequestBody(required = false) AnalysisBody body) {
        return run("custom", body == null ? "" : body.getPrompt());
    }

    @GetMapping("/history")
    public TableDataInfo history(PageQuery query) {
        return getDataTable(recordService.queryPageList(query));
    }

    @GetMapping("/history/{id}")
    public AjaxResult historyDetail(@PathVariable Long id) {
        AiAnalysisRecord record = recordService.getById(id);
        if (record == null) {
            return AjaxResult.error("记录不存在");
        }
        return AjaxResult.success(record);
    }

    private AjaxResult run(String type, String source) {
        try {
            String result = analysisService.analyze(type, source, getUserId());
            return AjaxResult.success("分析完成", result);
        } catch (IllegalArgumentException ex) {
            return AjaxResult.error(ex.getMessage());
        } catch (RuntimeException ex) {
            return AjaxResult.error(ex.getMessage() == null ? "分析失败" : ex.getMessage());
        }
    }

    private static String join(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a + "\n" + b;
    }

    @Data
    public static class AnalysisBody {
        private String inventoryData;
        private String salesData;
        private String customerData;
        private String orderData;
        private String operationData;
        private String prompt;
    }
}
