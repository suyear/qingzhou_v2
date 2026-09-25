package com.qingzhou.modules.dashboard.support;

import com.qingzhou.modules.dashboard.dto.TrendPointVO;
import com.qingzhou.modules.dashboard.dto.TrendRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DashboardStatsSupportTest {

    @Test
    void clampDaysFallsBackAndCaps() {
        assertEquals(7, DashboardStatsSupport.clampDays(null));
        assertEquals(1, DashboardStatsSupport.clampDays(0));
        assertEquals(30, DashboardStatsSupport.clampDays(99));
        assertEquals(14, DashboardStatsSupport.clampDays(14));
    }

    @Test
    void fillTrendPadsMissingDays() {
        TrendRow row = new TrendRow();
        row.setBucket("2026-09-10");
        row.setTotal(4L);
        row.setSuccessCount(3L);
        row.setFailedCount(1L);
        row.setRunningCount(0L);

        List<TrendPointVO> points = DashboardStatsSupport.fillTrend(
                List.of(row), 3, LocalDate.of(2026, 9, 11));

        assertEquals(3, points.size());
        assertEquals("2026-09-09", points.get(0).getDate());
        assertEquals(0, points.get(0).getTotal());
        assertEquals("2026-09-10", points.get(1).getDate());
        assertEquals(4, points.get(1).getTotal());
        assertEquals(3, points.get(1).getSuccessCount());
        assertEquals(25.0, points.get(1).getFailRate());
        assertEquals("2026-09-11", points.get(2).getDate());
        assertEquals(0, points.get(2).getTotal());
        assertNull(points.get(2).getFailRate());
    }

    @Test
    void successRateIgnoresEmptyAndRoundsTenth() {
        assertNull(DashboardStatsSupport.successRate(0, 0));
        assertEquals(100.0, DashboardStatsSupport.successRate(5, 0));
        assertEquals(66.7, DashboardStatsSupport.successRate(2, 1));
    }

    @Test
    void failRateAndDurationStats() {
        assertNull(DashboardStatsSupport.failRate(0, 0));
        assertEquals(25.0, DashboardStatsSupport.failRate(1, 4));
        assertEquals(100L, DashboardStatsSupport.averageMs(List.of(50L, 150L)));
        assertEquals(150L, DashboardStatsSupport.percentileMs(List.of(10L, 20L, 30L, 150L), 0.95));
        assertNull(DashboardStatsSupport.averageMs(List.of()));
        assertNull(DashboardStatsSupport.percentileMs(null, 0.95));
    }
}
