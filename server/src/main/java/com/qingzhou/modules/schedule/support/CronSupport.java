package com.qingzhou.modules.schedule.support;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class CronSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private CronSupport() {
    }

    // Spring 6 位 Cron：秒 分 时 日 月 周，例如每 5 分钟 0 */5 * * * *
    public static void assertValid(String cron) {
        if (cron == null || !CronExpression.isValidExpression(cron.trim())) {
            throw new BizException(ResultCode.BAD_REQUEST, "Cron 无效，需 6 位（秒 分 时 日 月 周），例如 0 */5 * * * *");
        }
    }

    public static LocalDateTime nextFireTime(String cron) {
        assertValid(cron);
        ZonedDateTime next = CronExpression.parse(cron.trim()).next(ZonedDateTime.now(ZONE));
        return next == null ? null : next.toLocalDateTime();
    }
}
