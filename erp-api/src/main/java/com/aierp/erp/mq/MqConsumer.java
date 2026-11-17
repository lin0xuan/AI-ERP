package com.aierp.erp.mq;

import com.aierp.erp.notify.NotifierService;
import com.aierp.model.entity.SysMessage;
import com.aierp.service.ISysMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单、售后、发货事件的进程内通知。没有启用 Kafka 时，由业务入口直接调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqConsumer {

    private final ISysMessageService messageService;
    private final NotifierService notifierService;

    public void handleOrderMessage(String message) {
        publish("order_sync", "订单同步", message);
    }

    public void handleRefundMessage(String message) {
        publish("refund_sync", "售后同步", message);
    }

    public void handleShipMessage(String message) {
        publish("ship_sync", "发货通知", message);
    }

    private void publish(String type, String title, String message) {
        if (message == null || message.isBlank()) return;
        SysMessage row = new SysMessage();
        row.setType(type);
        row.setLevel("low");
        row.setTitle(title);
        row.setContent(message);
        row.setSource("mq");
        row.setIsRead(0);
        row.setNeedNotify(1);
        row.setNotifyStatus(0);
        row.setCreatedTime(LocalDateTime.now());
        messageService.save(row);
        boolean ok = notifierService.notifyAll(title, message);
        row.setNotifyStatus(ok ? 1 : 2);
        row.setNotifyTime(LocalDateTime.now());
        messageService.save(row);
        log.info("业务通知已记录: {} {}", title, message);
    }
}
