package com.qingzhou.modules.dashboard.support;

import com.qingzhou.modules.dashboard.dto.NamedCountVO;
import com.qingzhou.modules.dashboard.dto.TrendPointVO;
import com.qingzhou.modules.dashboard.dto.TrendRow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DashboardStatsSupport {

    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 30;
    public static final int DEFAULT_DAYS = 7;

    private DashboardStatsSupport() {
    }

    public static int clampDays(Integer days) {
        if (days == null) {
            return DEFAULT_DAYS;
        }
        return Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));
    }

    public static LocalDateTime rangeStart(int days, LocalDate today) {
        return today.minusDays(days - 1L).atStartOfDay();
    }

    public static List<TrendPointVO> fillTrend(List<TrendRow> rows, int days, LocalDate today) {
        Map<String, TrendRow> byDay = new LinkedHashMap<>();
        if (rows != null) {
            for (TrendRow row : rows) {
                if (row != null && row.getBucket() != null) {
                    byDay.put(row.getBucket(), row);
                }
            }
        }
        List<TrendPointVO> points = new ArrayList<>(days);
        LocalDate start = today.minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            LocalDate day = start.plusDays(i);
            String key = day.toString();
            TrendRow row = byDay.get(key);
            TrendPointVO point = new TrendPointVO();
            point.setDate(key);
            if (row == null) {
                points.add(point);
                continue;
            }
            point.setTotal(nz(row.getTotal()));
            point.setSuccessCount(nz(row.getSuccessCount()));
            point.setFailedCount(nz(row.getFailedCount()));
            point.setRunningCount(nz(row.getRunningCount()));
            point.setFailRate(failRate(point.getFailedCount(), point.getSuccessCount() + point.getFailedCount()));
            points.add(point);
        }
        return points;
    }

    public static Double successRate(long success, long failed) {
        long finished = success + failed;
        if (finished <= 0) {
            return null;
        }
        return Math.round(success * 1000.0 / finished) / 10.0;
    }

    public static Double failRate(long failed, long finished) {
        if (finished <= 0) {
            return null;
        }
        return Math.round(failed * 1000.0 / finished) / 10.0;
    }

    public static Long averageMs(List<Long> durations) {
        List<Long> values = cleanDurations(durations);
        if (values.isEmpty()) {
            return null;
        }
        long sum = 0;
        for (Long value : values) {
            sum += value;
        }
        return Math.round(sum / (double) values.size());
    }

    public static Long percentileMs(List<Long> durations, double percentile) {
        List<Long> values = cleanDurations(durations);
        if (values.isEmpty()) {
            return null;
        }
        values.sort(Long::compareTo);
        double ratio = Math.min(1.0, Math.max(0.0, percentile));
        int index = (int) Math.ceil(values.size() * ratio) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    private static List<Long> cleanDurations(List<Long> durations) {
        List<Long> values = new ArrayList<>();
        if (durations == null) {
            return values;
        }
        for (Long value : durations) {
            if (value != null && value >= 0) {
                values.add(value);
            }
        }
        return values;
    }

    public static NamedCountVO named(String name, String label, long count) {
        NamedCountVO vo = new NamedCountVO();
        vo.setName(name);
        vo.setLabel(label);
        vo.setCount(count);
        return vo;
    }

    public static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
