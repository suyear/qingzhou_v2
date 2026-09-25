package com.qingzhou.modules.schedule.support;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * XXL-JOB 执行器 Handler。Admin 任务参数填工作流 ID。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QingzhouWorkflowJobHandler {

    public static final String HANDLER = "qingzhouWorkflowHandler";

    private final ScheduleTrigger scheduleTrigger;

    @XxlJob(HANDLER)
    public void execute() {
        String param = XxlJobHelper.getJobParam();
        if (!StringUtils.hasText(param)) {
            XxlJobHelper.handleFail("任务参数为空，需要工作流 ID");
            return;
        }
        try {
            scheduleTrigger.fireWorkflow(Long.parseLong(param.trim()));
            XxlJobHelper.handleSuccess();
        } catch (Exception ex) {
            log.error("XXL-JOB 执行失败 param={}", param, ex);
            XxlJobHelper.handleFail(ex.getMessage());
        }
    }
}
