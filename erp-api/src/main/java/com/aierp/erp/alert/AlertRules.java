package com.aierp.erp.alert;

import java.time.LocalDateTime;

/**
 * 预警是否该发出的纯规则。调度器负责查库，规则本身不访问数据库。
 */
public final class AlertRules {

    public static final int SHIP_TIMEOUT_HOURS = 4;
    public static final int DEDUP_HOURS = 12;

    private AlertRules() {
    }

    public static boolean belowSafetyStock(Integer lowQty, int available) {
        return lowQty != null && lowQty > 0 && available < lowQty;
    }

    /** 新订单或待发货，且创建时间早于当前 4 小时。 */
    public static boolean shipTimeout(Integer status, LocalDateTime created, LocalDateTime now) {
        if (status == null || created == null || now == null) {
            return false;
        }
        if (status != 0 && status != 1) {
            return false;
        }
        return created.isBefore(now.minusHours(SHIP_TIMEOUT_HOURS));
    }

    public static boolean recentlyAlerted(LocalDateTime lastCreated, LocalDateTime now) {
        return lastCreated != null && now != null && lastCreated.isAfter(now.minusHours(DEDUP_HOURS));
    }
}
