package com.qingzhou.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.common.json.Jsons;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.schedule.dto.ScheduleJobSaveRequest;
import com.qingzhou.modules.schedule.dto.ScheduleModeVO;
import com.qingzhou.modules.schedule.dto.SchedulePreviewRequest;
import com.qingzhou.modules.schedule.dto.SchedulePreviewVO;
import com.qingzhou.modules.schedule.entity.ScheduleJob;
import com.qingzhou.modules.schedule.mapper.ScheduleJobMapper;
import com.qingzhou.modules.schedule.service.ScheduleJobService;
import com.qingzhou.modules.schedule.support.LocalCronRegistrar;
import com.qingzhou.modules.schedule.support.QingzhouWorkflowJobHandler;
import com.qingzhou.modules.schedule.support.ScheduleSupport;
import com.qingzhou.modules.schedule.support.ScheduleTrigger;
import com.qingzhou.modules.schedule.support.ScheduleType;
import com.qingzhou.modules.workflow.entity.Workflow;
import com.qingzhou.modules.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
@Service
@RequiredArgsConstructor
public class ScheduleJobServiceImpl extends ServiceImpl<ScheduleJobMapper, ScheduleJob> implements ScheduleJobService {

    private static final DateTimeFormatter FIRE_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final WorkflowService workflowService;
    private final ScheduleTrigger scheduleTrigger;
    private final ObjectProvider<LocalCronRegistrar> localCronRegistrar;
    private final Jsons jsons;

    @Override
    public ScheduleModeVO mode() {
        ScheduleModeVO vo = new ScheduleModeVO();
        vo.setLocalMode(registrar().localMode());
        vo.setHandler(QingzhouWorkflowJobHandler.HANDLER);
        vo.setHint(vo.isLocalMode()
                ? "未配置 XXL-JOB Admin，启停由本进程调度生效（支持间隔 / 每天 / 每周 / 一次性 / Cron）"
                : "已接入 XXL-JOB Admin，请在控制台创建同名 Handler 任务，参数填工作流 ID");
        return vo;
    }

    @Override
    public IPage<ScheduleJob> pageJobs(PageQuery query) {
        LambdaQueryWrapper<ScheduleJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(ScheduleJob::getJobName, query.getKeyword())
                        .or()
                        .like(ScheduleJob::getCronExpr, query.getKeyword())
                        .or()
                        .like(ScheduleJob::getScheduleType, query.getKeyword())
                        .or()
                        .like(ScheduleJob::getRemark, query.getKeyword()))
                .orderByDesc(ScheduleJob::getUpdateTime);
        return page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
    }

    @Override
    public ScheduleJob create(ScheduleJobSaveRequest request) {
        Workflow workflow = requireWorkflow(request.getWorkflowId());
        ScheduleJob job = applyRequest(new ScheduleJob(), request, workflow);
        job.setExecutorHandler(QingzhouWorkflowJobHandler.HANDLER);
        job.setStatus(0);
        save(job);
        if (Boolean.TRUE.equals(request.getStart())) {
            return start(job.getId());
        }
        return job;
    }

    @Override
    public ScheduleJob update(Long id, ScheduleJobSaveRequest request) {
        ScheduleJob job = requireJob(id);
        Workflow workflow = requireWorkflow(request.getWorkflowId());
        applyRequest(job, request, workflow);
        updateById(job);
        if (job.getStatus() != null && job.getStatus() == 1) {
            registrar().register(getById(id));
        }
        return getById(id);
    }

    @Override
    public ScheduleJob start(Long id) {
        ScheduleJob job = requireJob(id);
        ScheduleSupport.validateForStart(job);
        job.setStatus(1);
        job.setNextFireTime(ScheduleSupport.nextFireTime(job));
        updateById(job);
        registrar().register(getById(id));
        return getById(id);
    }

    @Override
    public ScheduleJob stop(Long id) {
        ScheduleJob job = requireJob(id);
        job.setStatus(0);
        updateById(job);
        registrar().cancel(id);
        return getById(id);
    }

    @Override
    public void removeJob(Long id) {
        requireJob(id);
        registrar().cancel(id);
        removeById(id);
    }

    @Override
    public ExecutionVO triggerNow(Long id) {
        ScheduleJob job = requireJob(id);
        return scheduleTrigger.fireJob(job);
    }

    @Override
    public SchedulePreviewVO preview(SchedulePreviewRequest request) {
        ScheduleJob job = toPreviewJob(request);
        ScheduleSupport.validate(job);
        SchedulePreviewVO vo = new SchedulePreviewVO();
        vo.setSummary(ScheduleSupport.summary(job));
        vo.setEffectiveCron(ScheduleSupport.effectiveCron(job));
        vo.setNextTimes(ScheduleSupport.previewNext(job, 5));
        return vo;
    }

    private ScheduleJob applyRequest(ScheduleJob job, ScheduleJobSaveRequest request, Workflow workflow) {
        job.setWorkflowId(workflow.getId());
        if (StringUtils.hasText(request.getJobName())) {
            job.setJobName(request.getJobName().trim());
        } else if (!StringUtils.hasText(job.getJobName())) {
            job.setJobName(workflow.getWorkflowName() + " 定时任务");
        }
        job.setScheduleType(StringUtils.hasText(request.getScheduleType())
                ? request.getScheduleType().trim().toUpperCase()
                : ScheduleType.CRON.name());
        job.setIntervalSeconds(request.getIntervalSeconds());
        job.setDailyTime(StringUtils.hasText(request.getDailyTime()) ? request.getDailyTime().trim() : null);
        job.setWeekDays(normalizeWeekDays(request.getWeekDays()));
        job.setFireAt(parseFireAt(request.getFireAt()));
        job.setRemark(request.getRemark());
        job.setTriggerInput(request.getTriggerInput() == null || request.getTriggerInput().isEmpty()
                ? null
                : jsons.toJson(request.getTriggerInput()));

        ScheduleType type = ScheduleType.of(job.getScheduleType());
        if (type == ScheduleType.CRON) {
            job.setCronExpr(StringUtils.hasText(request.getCronExpr()) ? request.getCronExpr().trim() : null);
        } else if (type == ScheduleType.DAILY || type == ScheduleType.WEEKLY) {
            job.setCronExpr(ScheduleSupport.effectiveCron(job));
        } else {
            job.setCronExpr(null);
        }

        ScheduleSupport.validate(job);
        job.setNextFireTime(ScheduleSupport.nextFireTime(job));
        return job;
    }

    private ScheduleJob toPreviewJob(SchedulePreviewRequest request) {
        ScheduleJob job = new ScheduleJob();
        job.setScheduleType(StringUtils.hasText(request.getScheduleType())
                ? request.getScheduleType().trim().toUpperCase()
                : ScheduleType.CRON.name());
        job.setCronExpr(request.getCronExpr());
        job.setIntervalSeconds(request.getIntervalSeconds());
        job.setDailyTime(request.getDailyTime());
        job.setWeekDays(normalizeWeekDays(request.getWeekDays()));
        job.setFireAt(parseFireAt(request.getFireAt()));
        ScheduleType type = ScheduleType.of(job.getScheduleType());
        if ((type == ScheduleType.DAILY || type == ScheduleType.WEEKLY) && job.getCronExpr() == null) {
            job.setCronExpr(ScheduleSupport.effectiveCron(job));
        }
        return job;
    }

    private LocalDateTime parseFireAt(String fireAt) {
        if (!StringUtils.hasText(fireAt)) {
            return null;
        }
        String text = fireAt.trim().replace(' ', 'T');
        if (text.length() == 16) {
            text = text + ":00";
        }
        return LocalDateTime.parse(text, FIRE_AT_FORMAT);
    }

    private String normalizeWeekDays(String weekDays) {
        if (!StringUtils.hasText(weekDays)) {
            return null;
        }
        return weekDays.trim();
    }

    private LocalCronRegistrar registrar() {
        return localCronRegistrar.getObject();
    }

    private ScheduleJob requireJob(Long id) {
        ScheduleJob job = getById(id);
        if (job == null) {
            throw new BizException(ResultCode.NOT_FOUND, "调度任务不存在");
        }
        if (!StringUtils.hasText(job.getScheduleType())) {
            job.setScheduleType(ScheduleType.CRON.name());
        }
        return job;
    }

    private Workflow requireWorkflow(Long workflowId) {
        Workflow workflow = workflowService.getById(workflowId);
        if (workflow == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工作流不存在");
        }
        if (!StringUtils.hasText(workflow.getGraphJson())) {
            throw new BizException(ResultCode.BAD_REQUEST, "工作流尚未编排，无法调度");
        }
        if (!"PUBLISHED".equals(workflow.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "请先发布工作流再配置调度");
        }
        return workflow;
    }
}
