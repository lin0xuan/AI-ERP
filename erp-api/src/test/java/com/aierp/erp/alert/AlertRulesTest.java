package com.aierp.erp.alert;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRulesTest {

    @Test
    void stockAlertOnlyWhenAvailableDropsBelowTheSkuLine() {
        assertFalse(AlertRules.belowSafetyStock(null, 0));
        assertFalse(AlertRules.belowSafetyStock(0, 0));
        assertFalse(AlertRules.belowSafetyStock(5, 5));
        assertTrue(AlertRules.belowSafetyStock(5, 4));
    }

    @Test
    void shipTimeoutCoversNewAndWaitingOrdersOlderThanFourHours() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 17, 12, 0);
        assertTrue(AlertRules.shipTimeout(1, now.minusHours(5), now));
        assertTrue(AlertRules.shipTimeout(0, now.minusHours(4).minusMinutes(1), now));
        assertFalse(AlertRules.shipTimeout(1, now.minusHours(3), now));
        assertFalse(AlertRules.shipTimeout(2, now.minusDays(2), now));
        assertFalse(AlertRules.shipTimeout(1, null, now));
    }

    @Test
    void sameAlertIsSuppressedForTwelveHours() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 17, 12, 0);
        assertTrue(AlertRules.recentlyAlerted(now.minusHours(11), now));
        assertFalse(AlertRules.recentlyAlerted(now.minusHours(13), now));
        assertFalse(AlertRules.recentlyAlerted(null, now));
    }
}
