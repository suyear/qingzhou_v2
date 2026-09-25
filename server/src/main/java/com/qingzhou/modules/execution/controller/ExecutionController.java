package com.qingzhou.modules.execution.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qingzhou.common.api.R;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.execution.dto.BatchReplayRequest;
import com.qingzhou.modules.execution.dto.BatchReplayResultVO;
import com.qingzhou.modules.execution.dto.ExecutionListVO;
import com.qingzhou.modules.execution.dto.ExecutionQuery;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.execution.dto.TryRunRequest;
import com.qingzhou.modules.execution.engine.WorkflowEngine;
import com.qingzhou.modules.execution.entity.ExecutionNodeLog;
import com.qingzhou.modules.execution.service.ExecutionInstanceService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private static final int EXPORT_MAX = 5000;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ExecutionInstanceService executionInstanceService;
    private final WorkflowEngine workflowEngine;

    @GetMapping
    public R<IPage<ExecutionListVO>> page(ExecutionQuery query) {
        return R.ok(executionInstanceService.pageExecutions(query));
    }

    @GetMapping("/export")
    public void export(ExecutionQuery query, HttpServletResponse response) throws Exception {
        List<ExecutionListVO> rows = executionInstanceService.listForExport(query, EXPORT_MAX);
        String filename = "executions_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        try (PrintWriter writer = response.getWriter()) {
            writer.write('\ufeff');
            writer.println("执行单号,工作流,编码,触发方式,触发来源,状态,失败分类,耗时ms,错误摘要,开始时间,TraceId");
            for (ExecutionListVO row : rows) {
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        csv(row.getExecutionNo()),
                        csv(row.getWorkflowName()),
                        csv(row.getWorkflowCode()),
                        csv(row.getTriggerType()),
                        csv(row.getTriggerSourceLabel()),
                        csv(row.getStatus()),
                        csv(row.getFailureCategoryLabel()),
                        row.getDurationMs() == null ? "" : row.getDurationMs(),
                        csv(row.getErrorMsg()),
                        row.getStartTime() == null ? "" : TIME_FMT.format(row.getStartTime()),
                        csv(row.getTraceId()));
            }
        }
    }

    @GetMapping("/{id}")
    public R<ExecutionVO> detail(@PathVariable Long id) {
        return R.ok(executionInstanceService.detail(id));
    }

    @GetMapping("/{id}/logs")
    public R<List<ExecutionNodeLog>> logs(@PathVariable Long id) {
        return R.ok(executionInstanceService.detail(id).getLogs());
    }

    @PostMapping("/{id}/replay")
    public R<ExecutionVO> replay(@PathVariable Long id, @RequestBody(required = false) TryRunRequest request) {
        return R.ok(workflowEngine.replay(id, request == null ? null : request.getInput()));
    }

    @PostMapping("/batch-replay")
    public R<BatchReplayResultVO> batchReplay(@RequestBody BatchReplayRequest request) {
        List<Long> ids = request == null || request.getIds() == null ? List.of() : request.getIds();
        if (ids.isEmpty()) {
            throw new BizException("请选择要重放的执行记录");
        }
        if (ids.size() > 50) {
            throw new BizException("单次最多重放 50 条");
        }
        BatchReplayResultVO result = new BatchReplayResultVO();
        for (Long id : ids) {
            try {
                var instance = executionInstanceService.getById(id);
                if (instance == null) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.getMessages().add(id + ": 不存在");
                    continue;
                }
                if ("RUNNING".equals(instance.getStatus())) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.getMessages().add(instance.getExecutionNo() + ": 运行中跳过");
                    continue;
                }
                ExecutionVO vo = workflowEngine.replay(id, null);
                result.setSuccess(result.getSuccess() + 1);
                String nextNo = vo.getInstance() == null ? "" : vo.getInstance().getExecutionNo();
                result.getMessages().add(instance.getExecutionNo() + " → " + nextNo);
            } catch (Exception ex) {
                result.setFailed(result.getFailed() + 1);
                result.getMessages().add(id + ": " + ex.getMessage());
            }
        }
        return R.ok(result);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("\"", "\"\"");
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v + "\"";
        }
        return v;
    }
}
