package com.qingzhou.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
public class XxlJobConfig {

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(
            @Value("${qingzhou.xxl.admin-addresses}") String adminAddresses,
            @Value("${qingzhou.xxl.access-token:}") String accessToken,
            @Value("${qingzhou.xxl.executor.appname}") String appname,
            @Value("${qingzhou.xxl.executor.port}") int port,
            @Value("${qingzhou.xxl.executor.log-path}") String logPath,
            @Value("${qingzhou.xxl.executor.log-retention-days}") int logRetentionDays) {
        if (!StringUtils.hasText(adminAddresses)) {
            log.info("未配置 XXL-JOB Admin，执行器不启动，调度走内置 Cron");
            return null;
        }
        log.info("启用 XXL-JOB 执行器 appname={} admin={}", appname, adminAddresses);
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appname);
        executor.setPort(port);
        executor.setAccessToken(accessToken);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }

    @Bean(destroyMethod = "destroy")
    public ThreadPoolTaskScheduler qingzhouTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("qz-cron-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
