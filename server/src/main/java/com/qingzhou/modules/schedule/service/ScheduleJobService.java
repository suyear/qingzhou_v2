package com.qingzhou.modules.schedule.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.schedule.dto.ScheduleJobSaveRequest;
import com.qingzhou.modules.schedule.dto.ScheduleModeVO;
import com.qingzhou.modules.schedule.dto.SchedulePreviewRequest;
import com.qingzhou.modules.schedule.dto.SchedulePreviewVO;
import com.qingzhou.modules.schedule.entity.ScheduleJob;

public interface ScheduleJobService extends IService<ScheduleJob> {

    ScheduleModeVO mode();

    IPage<ScheduleJob> pageJobs(PageQuery query);

    ScheduleJob create(ScheduleJobSaveRequest request);

    ScheduleJob update(Long id, ScheduleJobSaveRequest request);

    ScheduleJob start(Long id);

    ScheduleJob stop(Long id);

    void removeJob(Long id);

    ExecutionVO triggerNow(Long id);

    SchedulePreviewVO preview(SchedulePreviewRequest request);
}
