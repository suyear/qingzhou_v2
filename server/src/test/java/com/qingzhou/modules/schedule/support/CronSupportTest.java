package com.qingzhou.modules.schedule.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CronSupportTest {

    @Test
    void acceptSixFieldCron() {
        CronSupport.assertValid("0 */5 * * * *");
        assertNotNull(CronSupport.nextFireTime("0 0 8 * * *"));
    }

    @Test
    void rejectInvalidCron() {
        assertThrows(RuntimeException.class, () -> CronSupport.assertValid("not-a-cron"));
        assertThrows(RuntimeException.class, () -> CronSupport.assertValid("0 0 8 * *"));
    }
}
