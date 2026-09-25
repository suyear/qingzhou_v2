package com.qingzhou.modules.execution.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.execution.dto.OpenApiExecuteResultVO;
import com.qingzhou.modules.execution.dto.RunResultVO;
import com.qingzhou.modules.execution.dto.RunStepVO;
import com.qingzhou.modules.execution.entity.ExecutionInstance;
import com.qingzhou.modules.execution.entity.ExecutionNodeLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExecutionResultAssembler {

    private final ObjectMapper objectMapper;

    /** 编排试跑 / 立即触发：含步骤摘要，便于内部排障 */
    public RunResultVO from(ExecutionVO vo) {
        if (vo == null || vo.getInstance() == null) {
            return RunResultVO.builder()
                    .status("FAILED")
                    .errorMsg("无执行结果")
                    .steps(List.of())
                    .build();
        }
        ExecutionInstance instance = vo.getInstance();
        Map<String, Object> output = DbResultCleaner.stripMap(parseObjectMap(instance.getOutputResult()));

        List<RunStepVO> steps = new ArrayList<>();
        for (ExecutionNodeLog log : vo.getLogs() == null ? List.<ExecutionNodeLog>of() : vo.getLogs()) {
            steps.add(RunStepVO.builder()
                    .nodeId(log.getNodeId())
                    .nodeName(log.getNodeName())
                    .componentCode(log.getComponentCode())
                    .status(log.getStatus())
                    .durationMs(log.getDurationMs())
                    .errorMsg(log.getErrorMsg())
                    .response(DbResultCleaner.stripRedundant(parseJson(log.getResponseBody())))
                    .build());
        }

        return RunResultVO.builder()
                .executionId(instance.getId())
                .executionNo(instance.getExecutionNo())
                .status(instance.getStatus())
                .durationMs(instance.getDurationMs())
                .errorMsg(instance.getErrorMsg())
                .output(output)
                .steps(steps)
                .build();
    }

    /** 开放网关：只返回调用结果，隐藏编排步骤与节点 ID */
    public OpenApiExecuteResultVO forOpenApi(ExecutionVO vo) {
        if (vo == null || vo.getInstance() == null) {
            return OpenApiExecuteResultVO.builder()
                    .status("FAILED")
                    .errorMsg("无执行结果")
                    .build();
        }
        ExecutionInstance instance = vo.getInstance();
        return OpenApiExecuteResultVO.builder()
                .executionId(instance.getId())
                .executionNo(instance.getExecutionNo())
                .status(instance.getStatus())
                .durationMs(instance.getDurationMs())
                .errorMsg(instance.getErrorMsg())
                .output(buildPublicOutput(vo))
                .build();
    }

    private Object buildPublicOutput(ExecutionVO vo) {
        List<ExecutionNodeLog> logs = vo.getLogs() == null ? List.of() : vo.getLogs();
        List<ExecutionNodeLog> successLogs = logs.stream()
                .filter(log -> "SUCCESS".equals(log.getStatus()))
                .toList();
        List<ExecutionNodeLog> source = successLogs.isEmpty() ? logs : successLogs;
        if (source.isEmpty()) {
            Map<String, Object> raw = DbResultCleaner.stripMap(parseObjectMap(vo.getInstance().getOutputResult()));
            if (raw.size() == 1) {
                return raw.values().iterator().next();
            }
            return raw.isEmpty() ? null : raw;
        }
        if (source.size() == 1) {
            return DbResultCleaner.stripRedundant(parseJson(source.get(0).getResponseBody()));
        }
        Map<String, Object> byName = new LinkedHashMap<>();
        for (ExecutionNodeLog log : source) {
            String key = StringUtils.hasText(log.getNodeName()) ? log.getNodeName() : log.getNodeId();
            String unique = key;
            int i = 2;
            while (byName.containsKey(unique)) {
                unique = key + "_" + i++;
            }
            byName.put(unique, DbResultCleaner.stripRedundant(parseJson(log.getResponseBody())));
        }
        return byName;
    }

    private Map<String, Object> parseObjectMap(String json) {
        Object parsed = parseJson(json);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private Object parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return json;
        }
    }
}
