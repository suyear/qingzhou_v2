package com.qingzhou.modules.schedule.support;

import com.qingzhou.modules.schedule.entity.ScheduleJob;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleSupportTest {

    @Test
    void dailyCron() {
        ScheduleJob job = job("DAILY", null, null, "08:30", null, null);
        assertEquals("0 30 8 * * *", ScheduleSupport.effectiveCron(job));
        assertNotNull(ScheduleSupport.nextFireTime(job));
    }

    @Test
    void weeklyCron() {
        ScheduleJob job = job("WEEKLY", null, null, "09:15", "1,3,5", null);
        assertEquals("0 15 9 * * MON,WED,FRI", ScheduleSupport.effectiveCron(job));
    }

    @Test
    void intervalPreview() {
        ScheduleJob job = job("INTERVAL", null, 300, null, null, null);
        List<LocalDateTime> times = ScheduleSupport.previewNext(job, 3);
        assertEquals(3, times.size());
    }

    @Test
    void onceMustBeFutureOnStart() {
        ScheduleJob job = job("ONCE", null, null, null, null, LocalDateTime.now().minusMinutes(1));
        assertThrows(RuntimeException.class, () -> ScheduleSupport.validateForStart(job));
    }

    @Test
    void summaryText() {
        ScheduleJob job = job("INTERVAL", null, 3600, null, null, null);
        assertTrue(ScheduleSupport.summary(job).contains("小时"));
    }

    @Test
    void rejectShortInterval() {
        ScheduleJob job = job("INTERVAL", null, 5, null, null, null);
        assertThrows(RuntimeException.class, () -> ScheduleSupport.validate(job));
    }

    private static ScheduleJob job(String type, String cron, Integer interval, String dailyTime,
                                   String weekDays, LocalDateTime fireAt) {
        ScheduleJob job = new ScheduleJob();
        job.setScheduleType(type);
        job.setCronExpr(cron);
        job.setIntervalSeconds(interval);
        job.setDailyTime(dailyTime);
        job.setWeekDays(weekDays);
        job.setFireAt(fireAt);
        return job;
    }
}
