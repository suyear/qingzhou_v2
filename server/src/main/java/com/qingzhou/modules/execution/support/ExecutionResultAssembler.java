package com.qingzhou.modules.execution.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.modules.execution.dto.ExecutionVO;
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

    public RunResultVO from(ExecutionVO vo) {
        if (vo == null || vo.getInstance() == null) {
            return RunResultVO.builder()
                    .status("FAILED")
                    .errorMsg("无执行结果")
                    .steps(List.of())
                    .build();
        }
        ExecutionInstance instance = vo.getInstance();
        Map<String, Object> output = parseObjectMap(instance.getOutputResult());
        output = DbResultCleaner.stripMap(output);

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
