package com.aierp.erp.serviceImpl.ai;

import com.aierp.erp.datacenter.DataCenterService;
import com.aierp.erp.datacenter.DataCenterAccess;
import com.alibaba.fastjson2.JSON;
import com.aierp.model.entity.AiAnalysisRecord;
import com.aierp.model.entity.OGoodsInventory;
import com.aierp.model.entity.OOrder;
import com.aierp.service.IAiAnalysisRecordService;
import com.aierp.service.OGoodsInventoryService;
import com.aierp.service.OOrderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用库里的真实汇总数做分析。没有默认模型时只返回汇总，不生成看起来像结论的文字。
 */
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final DataCenterService dataCenterService;

    private final OOrderService orderService;
    private final OGoodsInventoryService inventoryService;
    private final AiOrchestrationService orchestrationService;
    private final IAiAnalysisRecordService recordService;

    public String analyze(String type, String sourceText, Long userId) {
        String facts = buildFacts(type, sourceText);
        if (facts == null || facts.isBlank()) {
            throw new IllegalArgumentException(emptyMessage(type));
        }
        String answer;
        if (orchestrationService.getDefaultConfig() == null) {
            answer = "未配置默认大模型，下面只是数据库汇总，没有生成式分析。\n\n" + facts;
        } else {
            answer = askModel(type, facts);
        }
        saveRecord(type, facts, answer, userId);
        return answer;
    }

    private String buildFacts(String type, String sourceText) {
        if ("sales".equals(type)) return salesFacts();
        if ("inventory".equals(type)) {
            String fromDb = inventoryFacts();
            return fromDb + (sourceText == null || sourceText.isBlank() ? "" : "\n补充说明：\n" + trim(sourceText));
        }
        if ("operation".equals(type)) {
            String fromDb = operationFacts();
            return fromDb + (sourceText == null || sourceText.isBlank() ? "" : "\n补充说明：\n" + trim(sourceText));
        }
        return trim(sourceText);
    }

    private String inventoryFacts() {
        List<OGoodsInventory> rows = inventoryService.list(new LambdaQueryWrapper<OGoodsInventory>()
                .eq(OGoodsInventory::getIsDelete, 0)
                .orderByAsc(OGoodsInventory::getAvailableQuantity)
                .last("LIMIT 30"));
        if (rows.isEmpty()) return "库存表当前没有记录。";
        StringBuilder text = new StringBuilder("可用库存最低的记录（最多 30 条）：\n");
        for (OGoodsInventory row : rows) {
            text.append(row.getSkuCode()).append(" ").append(row.getSkuName())
                    .append(" 可用 ").append(row.getAvailableQuantity() == null ? 0 : row.getAvailableQuantity())
                    .append(" 锁定 ").append(row.getLockedQuantity() == null ? 0 : row.getLockedQuantity())
                    .append("\n");
        }
        return text.toString();
    }

    private String operationFacts() {
        long waiting = orderService.count(new LambdaQueryWrapper<OOrder>().in(OOrder::getOrderStatus, 0, 1));
        long shipped = orderService.count(new LambdaQueryWrapper<OOrder>().eq(OOrder::getOrderStatus, 2));
        long done = orderService.count(new LambdaQueryWrapper<OOrder>().eq(OOrder::getOrderStatus, 3));
        return "订单状态汇总：待发货 " + waiting + "，已发货 " + shipped + "，已完成 " + done + "。不要补充未给出的时效和人员。";
    }

    private String salesFacts() {
        DataCenterAccess.require("read");
        LocalDate today = LocalDate.now(DataCenterService.ZONE);
        return JSON.toJSONString(dataCenterService.metrics(today.minusDays(6), today, null, null));
    }

    private String askModel(String type, String facts) {
        ChatClient client = orchestrationService.buildDefaultChatClient();
        String prompt = """
                你是电商经营分析助手。只根据下面给出的事实写作，不要编造商品、渠道、客户或未出现的数字。
                事实不足时直接说明缺什么。使用简体中文和 Markdown。
                分析类型：%s

                %s
                """.formatted(type, facts);
        String content = client.prompt().user(prompt).call().content();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("模型没有返回内容");
        }
        return content;
    }

    private void saveRecord(String type, String facts, String answer, Long userId) {
        AiAnalysisRecord record = new AiAnalysisRecord();
        record.setAnalysisType(type);
        record.setAnalysisContent(trim(facts));
        record.setAnalysisResult(answer);
        record.setStatus(1);
        record.setUserId(userId);
        record.setCreatedTime(LocalDateTime.now());
        record.setUpdatedTime(LocalDateTime.now());
        recordService.save(record);
    }

    private static String trim(String text) {
        if (text == null) {
            return "";
        }
        String value = text.trim();
        return value.length() <= 6000 ? value : value.substring(0, 6000);
    }

    private static String emptyMessage(String type) {
        return switch (type) {
            case "inventory" -> "请填写库存数据，或先在仓库里产生库存";
            case "customer" -> "请填写客户或订单数据";
            case "operation" -> "请填写运营数据";
            case "custom" -> "请填写分析提示词";
            default -> "没有可分析的数据";
        };
    }
}
