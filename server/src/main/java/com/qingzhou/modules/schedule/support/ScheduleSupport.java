package com.qingzhou.modules.schedule.support;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.schedule.entity.ScheduleJob;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ScheduleSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]?\\d|2[0-3]):([0-5]\\d)$");
    private static final String[] WEEK_NAMES = {"", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
    private static final int MIN_INTERVAL_SECONDS = 10;
    private static final int MAX_PREVIEW = 8;

    private ScheduleSupport() {
    }

    public static void validate(ScheduleJob job) {
        ScheduleType type = ScheduleType.of(job.getScheduleType());
        switch (type) {
            case CRON -> CronSupport.assertValid(job.getCronExpr());
            case INTERVAL -> assertInterval(job.getIntervalSeconds());
            case DAILY -> assertDailyTime(job.getDailyTime());
            case WEEKLY -> {
                assertDailyTime(job.getDailyTime());
                assertWeekDays(job.getWeekDays());
            }
            case ONCE -> assertFireAt(job.getFireAt(), false);
        }
    }

    public static void validateForStart(ScheduleJob job) {
        validate(job);
        if (ScheduleType.of(job.getScheduleType()) == ScheduleType.ONCE) {
            assertFireAt(job.getFireAt(), true);
        }
    }

    public static String effectiveCron(ScheduleJob job) {
        ScheduleType type = ScheduleType.of(job.getScheduleType());
        return switch (type) {
            case CRON -> job.getCronExpr().trim();
            case DAILY -> toDailyCron(job.getDailyTime());
            case WEEKLY -> toWeeklyCron(job.getDailyTime(), job.getWeekDays());
            default -> null;
        };
    }

    public static LocalDateTime nextFireTime(ScheduleJob job) {
        List<LocalDateTime> preview = previewNext(job, 1);
        return preview.isEmpty() ? null : preview.get(0);
    }

    public static List<LocalDateTime> previewNext(ScheduleJob job, int count) {
        int limit = Math.min(Math.max(count, 1), MAX_PREVIEW);
        ScheduleType type = ScheduleType.of(job.getScheduleType());
        return switch (type) {
            case CRON, DAILY, WEEKLY -> previewCron(effectiveCron(job), limit);
            case INTERVAL -> previewInterval(job.getIntervalSeconds(), limit);
            case ONCE -> previewOnce(job.getFireAt());
        };
    }

    public static String summary(ScheduleJob job) {
        ScheduleType type = ScheduleType.of(job.getScheduleType());
        return switch (type) {
            case CRON -> "Cron · " + job.getCronExpr();
            case INTERVAL -> "每 " + formatInterval(job.getIntervalSeconds());
            case DAILY -> "每天 " + job.getDailyTime();
            case WEEKLY -> "每周 " + formatWeekDays(job.getWeekDays()) + " " + job.getDailyTime();
            case ONCE -> "一次性 · " + formatDateTime(job.getFireAt());
        };
    }

    private static List<LocalDateTime> previewCron(String cron, int count) {
        CronSupport.assertValid(cron);
        List<LocalDateTime> times = new ArrayList<>();
        ZonedDateTime cursor = ZonedDateTime.now(ZONE);
        CronExpression expression = CronExpression.parse(cron.trim());
        for (int i = 0; i < count; i++) {
            ZonedDateTime next = expression.next(cursor);
            if (next == null) {
                break;
            }
            times.add(next.toLocalDateTime());
            cursor = next;
        }
        return times;
    }

    private static List<LocalDateTime> previewInterval(Integer intervalSeconds, int count) {
        assertInterval(intervalSeconds);
        List<LocalDateTime> times = new ArrayList<>();
        LocalDateTime cursor = LocalDateTime.now(ZONE);
        for (int i = 0; i < count; i++) {
            cursor = cursor.plusSeconds(intervalSeconds);
            times.add(cursor);
        }
        return times;
    }

    private static List<LocalDateTime> previewOnce(LocalDateTime fireAt) {
        if (fireAt == null) {
            return List.of();
        }
        return fireAt.isAfter(LocalDateTime.now(ZONE)) ? List.of(fireAt) : List.of();
    }

    private static String toDailyCron(String dailyTime) {
        int[] hm = parseDailyTime(dailyTime);
        return String.format(Locale.ROOT, "0 %d %d * * *", hm[1], hm[0]);
    }

    private static String toWeeklyCron(String dailyTime, String weekDays) {
        int[] hm = parseDailyTime(dailyTime);
        String days = Arrays.stream(weekDays.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Integer::parseInt)
                .filter(day -> day >= 1 && day <= 7)
                .map(day -> WEEK_NAMES[day])
                .collect(Collectors.joining(","));
        if (!StringUtils.hasText(days)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择至少一个星期");
        }
        return String.format(Locale.ROOT, "0 %d %d * * %s", hm[1], hm[0], days);
    }

    private static int[] parseDailyTime(String dailyTime) {
        assertDailyTime(dailyTime);
        String[] parts = dailyTime.trim().split(":");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    private static void assertInterval(Integer intervalSeconds) {
        if (intervalSeconds == null || intervalSeconds < MIN_INTERVAL_SECONDS) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "固定间隔不能小于 " + MIN_INTERVAL_SECONDS + " 秒");
        }
    }

    private static void assertDailyTime(String dailyTime) {
        if (!StringUtils.hasText(dailyTime) || !TIME_PATTERN.matcher(dailyTime.trim()).matches()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写有效时刻，格式 HH:mm");
        }
    }

    private static void assertWeekDays(String weekDays) {
        if (!StringUtils.hasText(weekDays)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择至少一个星期");
        }
        boolean valid = Arrays.stream(weekDays.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .allMatch(day -> {
                    try {
                        int value = Integer.parseInt(day);
                        return value >= 1 && value <= 7;
                    } catch (NumberFormatException ex) {
                        return false;
                    }
                });
        if (!valid) {
            throw new BizException(ResultCode.BAD_REQUEST, "星期格式无效，应为 1-7");
        }
    }

    private static void assertFireAt(LocalDateTime fireAt, boolean requireFuture) {
        if (fireAt == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请指定触发时间");
        }
        if (requireFuture && !fireAt.isAfter(LocalDateTime.now(ZONE))) {
            throw new BizException(ResultCode.BAD_REQUEST, "一次性任务触发时间必须晚于当前时间");
        }
    }

    private static String formatInterval(Integer seconds) {
        if (seconds == null) {
            return "—";
        }
        if (seconds % 3600 == 0) {
            return (seconds / 3600) + " 小时";
        }
        if (seconds % 60 == 0) {
            return (seconds / 60) + " 分钟";
        }
        return seconds + " 秒";
    }

    private static String formatWeekDays(String weekDays) {
        if (!StringUtils.hasText(weekDays)) {
            return "—";
        }
        String[] labels = {"", "一", "二", "三", "四", "五", "六", "日"};
        return Arrays.stream(weekDays.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Integer::parseInt)
                .map(day -> "周" + labels[day])
                .collect(Collectors.joining("、"));
    }

    private static String formatDateTime(LocalDateTime time) {
        if (time == null) {
            return "—";
        }
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
