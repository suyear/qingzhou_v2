package com.qingzhou.modules.schedule.support;

import com.qingzhou.modules.schedule.entity.ScheduleJob;
import com.qingzhou.modules.schedule.service.ScheduleJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 未接入 XXL-JOB Admin 时，在进程内注册多种调度触发器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalCronRegistrar {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone(ZONE);

    private final ThreadPoolTaskScheduler qingzhouTaskScheduler;
    private final ScheduleJobService scheduleJobService;
    private final ScheduleTrigger scheduleTrigger;

    @Value("${qingzhou.xxl.admin-addresses:}")
    private String adminAddresses;

    private final Map<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public boolean localMode() {
        return !StringUtils.hasText(adminAddresses);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreRunningJobs() {
        if (!localMode()) {
            log.info("已配置 XXL-JOB Admin，内置调度关闭");
            return;
        }
        scheduleJobService.lambdaQuery()
                .eq(ScheduleJob::getStatus, 1)
                .list()
                .forEach(this::register);
    }

    public void register(ScheduleJob job) {
        if (!localMode() || job.getId() == null) {
            return;
        }
        cancel(job.getId());
        Long jobId = job.getId();
        ScheduleType type = ScheduleType.of(job.getScheduleType());
        ScheduledFuture<?> future = scheduleFuture(job, jobId, type);
        if (future == null) {
            log.warn("无法注册调度 jobId={} type={}", jobId, type);
            return;
        }
        futures.put(jobId, future);
        log.info("注册本地调度 jobId={} type={} summary={}", jobId, type, ScheduleSupport.summary(job));
    }

    public void cancel(Long jobId) {
        ScheduledFuture<?> future = futures.remove(jobId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private ScheduledFuture<?> scheduleFuture(ScheduleJob job, Long jobId, ScheduleType type) {
        Runnable task = () -> fire(jobId, type);
        return switch (type) {
            case CRON, DAILY, WEEKLY -> qingzhouTaskScheduler.schedule(
                    task, new CronTrigger(ScheduleSupport.effectiveCron(job), TIME_ZONE));
            case INTERVAL -> {
                PeriodicTrigger periodic = new PeriodicTrigger(Duration.ofSeconds(job.getIntervalSeconds()));
                periodic.setFixedRate(true);
                yield qingzhouTaskScheduler.schedule(task, periodic);
            }
            case ONCE -> {
                if (job.getFireAt() == null) {
                    yield null;
                }
                Instant instant = job.getFireAt().atZone(ZONE).toInstant();
                if (instant.isBefore(Instant.now())) {
                    yield null;
                }
                yield qingzhouTaskScheduler.schedule(task, instant);
            }
        };
    }

    private void fire(Long jobId, ScheduleType type) {
        try {
            ScheduleJob current = scheduleJobService.getById(jobId);
            if (current == null || current.getStatus() == null || current.getStatus() != 1) {
                return;
            }
            scheduleTrigger.fireJob(current);
            if (type == ScheduleType.ONCE) {
                scheduleJobService.stop(jobId);
            }
        } catch (Exception ex) {
            log.error("本地调度触发失败 jobId={}", jobId, ex);
        }
    }
}
