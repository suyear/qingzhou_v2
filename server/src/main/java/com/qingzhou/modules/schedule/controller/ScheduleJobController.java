package com.qingzhou.modules.schedule.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.common.api.R;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.schedule.dto.ScheduleJobSaveRequest;
import com.qingzhou.modules.schedule.dto.ScheduleModeVO;
import com.qingzhou.modules.schedule.dto.SchedulePreviewRequest;
import com.qingzhou.modules.schedule.dto.SchedulePreviewVO;
import com.qingzhou.modules.schedule.entity.ScheduleJob;
import com.qingzhou.modules.schedule.service.ScheduleJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleJobController {

    private final ScheduleJobService scheduleJobService;

    @GetMapping("/mode")
    public R<ScheduleModeVO> mode() {
        return R.ok(scheduleJobService.mode());
    }

    @GetMapping("/jobs")
    public R<IPage<ScheduleJob>> page(PageQuery query) {
        return R.ok(scheduleJobService.pageJobs(query));
    }

    @PostMapping("/preview")
    public R<SchedulePreviewVO> preview(@RequestBody SchedulePreviewRequest request) {
        return R.ok(scheduleJobService.preview(request));
    }

    @PostMapping("/jobs")
    public R<ScheduleJob> create(@Valid @RequestBody ScheduleJobSaveRequest request) {
        return R.ok(scheduleJobService.create(request));
    }

    @PutMapping("/jobs/{id}")
    public R<ScheduleJob> update(@PathVariable Long id, @Valid @RequestBody ScheduleJobSaveRequest request) {
        return R.ok(scheduleJobService.update(id, request));
    }

    @PostMapping("/jobs/{id}/start")
    public R<ScheduleJob> start(@PathVariable Long id) {
        return R.ok(scheduleJobService.start(id));
    }

    @PostMapping("/jobs/{id}/stop")
    public R<ScheduleJob> stop(@PathVariable Long id) {
        return R.ok(scheduleJobService.stop(id));
    }

    @PostMapping("/jobs/{id}/trigger")
    public R<ExecutionVO> trigger(@PathVariable Long id) {
        return R.ok(scheduleJobService.triggerNow(id));
    }

    @DeleteMapping("/jobs/{id}")
    public R<Void> remove(@PathVariable Long id) {
        scheduleJobService.removeJob(id);
        return R.ok();
    }
}
