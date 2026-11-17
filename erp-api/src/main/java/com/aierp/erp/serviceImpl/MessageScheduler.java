package com.aierp.erp.serviceImpl;

import com.aierp.erp.alert.AlertRules;
import com.aierp.erp.notify.NotifierService;
import com.aierp.erp.serviceImpl.ai.AiOrchestrationService;
import com.aierp.erp.serviceImpl.ai.InventoryTools;
import com.aierp.erp.serviceImpl.ai.RefundTools;
import com.aierp.model.entity.OGoodsInventory;
import com.aierp.model.entity.OGoodsSku;
import com.aierp.model.entity.OOrder;
import com.aierp.model.entity.ShopRefund;
import com.aierp.model.entity.SysMessage;
import com.aierp.model.vo.SalesDailyVo;
import com.aierp.model.entity.SysConfig;
import com.aierp.service.ISysMessageService;
import com.aierp.service.SysConfigService;
import com.aierp.service.OGoodsInventoryService;
import com.aierp.service.OGoodsSkuService;
import com.aierp.service.OOrderService;
import com.aierp.service.ShopRefundService;
import com.aierp.sse.SseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageScheduler {

    private final OOrderService orderService;
    private final OGoodsSkuService skuService;
    private final OGoodsInventoryService inventoryService;
    private final ShopRefundService refundService;
    private final ISysMessageService messageService;
    private final AiOrchestrationService orchestrationService;
    private final RefundTools refundTools;
    private final InventoryTools inventoryTools;
    private final NotifierService notifierService;
    private final SseService sseService;
    private final SysConfigService configService;

    private static final List<String> NEED_NOTIFY_TYPES = List.of("stock_low", "order_timeout", "ai_analysis");

    @Scheduled(fixedRate = 1800000)
    public void run() {
        log.info("======自动分析====");
        checkSalesZero();
        checkShipPending();
        checkRefundExcess();
        checkStockLow();
        checkOrderTimeout();
        checkAiAnalysis();
    }

    @Scheduled(fixedRate = 600000)
    public void retryFailedNotify() {
        List<SysMessage> failed = messageService.getFailedNotify();
        for (SysMessage msg : failed) {
            boolean allOk = notifierService.notifyAll(msg.getTitle(), msg.getContent());
            msg.setNotifyStatus(allOk ? 1 : 2);
            msg.setNotifyTime(LocalDateTime.now());
            messageService.save(msg);
        }
        if (!failed.isEmpty()) log.info("重试推送 {} 条消息", failed.size());
    }

    private void checkSalesZero() {
        SalesDailyVo today = orderService.getTodaySalesDaily(0L);
        if (today != null && today.getAmount() != null && today.getAmount() > 0) return;
        save("sales_zero", "high", "今日销售额为零", "今日暂无销售额，请检查运营情况", "/sale/shop_order_list", "system");
    }

    private void checkShipPending() {
        Integer count = orderService.getWaitShipOrderAllCount(0L);
        if (count == null || count < configInt("alert.ship.pending.min", 50)) return;
        save("ship_pending", "medium", "发货积压提醒", "待发货订单" + count + "单，请尽快安排发货", "/sale/shop_order_list", "system");
    }

    private void checkRefundExcess() {
        LambdaQueryWrapper<ShopRefund> w = new LambdaQueryWrapper<>();
        w.ge(ShopRefund::getCreateOn, LocalDateTime.now().minusDays(30));
        long count = refundService.count(w);
        if (count < configInt("alert.refund.excess.min", 20)) return;
        save("refund_excess", "medium", "退款过多提醒", "近30天退款" + count + "单，请关注退款原因", "/sale/shop_refund", "system");
    }

    private void checkStockLow() {
        Map<String, Integer> availableBySku = new HashMap<>();
        List<OGoodsInventory> inventories = inventoryService.list(new LambdaQueryWrapper<OGoodsInventory>()
                .eq(OGoodsInventory::getIsDelete, 0));
        for (OGoodsInventory row : inventories) {
            if (row.getSkuId() == null) {
                continue;
            }
            int qty = row.getAvailableQuantity() == null ? 0 : row.getAvailableQuantity();
            availableBySku.merge(String.valueOf(row.getSkuId()), qty, Integer::sum);
        }
        int emitted = 0;
        List<OGoodsSku> skus = skuService.list(new LambdaQueryWrapper<OGoodsSku>().gt(OGoodsSku::getLowQty, 0));
        for (OGoodsSku sku : skus) {
            if (emitted >= configInt("alert.stock.max.per.run", 20)) {
                break;
            }
            int available = availableBySku.getOrDefault(sku.getId(), 0);
            if (!AlertRules.belowSafetyStock(sku.getLowQty(), available)) {
                continue;
            }
            String name = sku.getSkuName() == null || sku.getSkuName().isBlank() ? sku.getSkuCode() : sku.getSkuName();
            save("stock_low", "high", "库存不足: " + name,
                    "SKU " + sku.getSkuCode() + " 可用库存 " + available + "，预警线 " + sku.getLowQty(),
                    "/wms/goods_stock", "system");
            emitted++;
        }
    }

    private void checkOrderTimeout() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(configInt("alert.ship.timeout.hours", AlertRules.SHIP_TIMEOUT_HOURS));
        long count = orderService.count(new LambdaQueryWrapper<OOrder>()
                .in(OOrder::getOrderStatus, 0, 1)
                .lt(OOrder::getCreateTime, deadline));
        if (count <= 0) {
            return;
        }
        save("order_timeout", "medium", "发货超时提醒",
                count + " 个订单超过 " + AlertRules.SHIP_TIMEOUT_HOURS + " 小时仍未发货",
                "/ship/manual_ship", "system");
    }

    private void checkAiAnalysis() {
        if (messageService.existsRecent("ai_analysis", null, LocalDateTime.now().minusHours(AlertRules.DEDUP_HOURS))) {
            return;
        }
        if (orchestrationService.getDefaultConfig() == null) {
            return;
        }
        try {
            ChatClient cc = orchestrationService.buildDefaultChatClient(refundTools, inventoryTools);
            String result = CompletableFuture.supplyAsync(() ->
                    cc.prompt().user("""
                            你是电商运营监控助手，请查询数据检查是否存在异常：

                            如果有异常，按以下格式返回JSON数组，没有则返回[]：
                            [{"type":"ai_analysis","level":"high","title":"异常标题","content":"异常描述"}]
                            只返回JSON数组，不要其他文字。
                            """).call().content()
            ).get(60, TimeUnit.SECONDS);

            if (result == null || result.isBlank() || result.trim().equals("[]")) return;

            String json = result.trim().replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            if (!json.startsWith("[")) { int i = json.indexOf("["); if (i >= 0) json = json.substring(i); else return; }

            var items = com.alibaba.fastjson2.JSONArray.parseArray(json, com.alibaba.fastjson2.JSONObject.class);
            for (var item : items) {
                save("ai_analysis", item.getString("level"), item.getString("title"), item.getString("content"), "", "ai");
            }
        } catch (Exception e) {
            log.warn("AI消息检查失败", e);
        }
    }

    private int configInt(String key, int fallback) {
        try {
            SysConfig config = configService.getOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key), false);
            if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) return fallback;
            return Integer.parseInt(config.getConfigValue().trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private void save(String type, String level, String title, String content, String link, String source) {
        if (messageService.existsRecent(type, title, LocalDateTime.now().minusHours(AlertRules.DEDUP_HOURS))) {
            return;
        }
        boolean needNotify = "high".equals(level) || NEED_NOTIFY_TYPES.contains(type);

        SysMessage m = new SysMessage();
        m.setType(type);
        m.setLevel(level);
        m.setTitle(title);
        m.setContent(content);
        m.setLink(link);
        m.setSource(source);
        m.setIsRead(0);
        m.setNeedNotify(needNotify ? 1 : 0);
        m.setNotifyStatus(0);
        m.setCreatedTime(LocalDateTime.now());
        messageService.save(m);

        if (needNotify) {
            boolean allOk = notifierService.notifyAll(title, content);
            m.setNotifyStatus(allOk ? 1 : 2);
            m.setNotifyTime(LocalDateTime.now());
            messageService.save(m);
        }

        sseService.broadcastMessage("message", Map.of(
                "type", type, "level", level,
                "title", title, "content", content, "id", m.getId()));

        log.info("消息: [{}] {} (needNotify={})", level, title, needNotify);
    }
}
