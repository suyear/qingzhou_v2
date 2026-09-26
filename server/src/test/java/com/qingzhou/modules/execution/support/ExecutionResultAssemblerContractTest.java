package com.qingzhou.modules.execution.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.execution.entity.ExecutionInstance;
import com.qingzhou.modules.execution.entity.ExecutionNodeLog;
import com.qingzhou.modules.workflow.entity.WorkflowSnapshot;
import com.qingzhou.modules.workflow.service.WorkflowService;
import com.qingzhou.modules.workflow.service.WorkflowSnapshotService;
import com.qingzhou.modules.workflow.support.WorkflowSnapshotCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionResultAssemblerContractTest {

    @Mock
    private WorkflowSnapshotService snapshotService;
    @Mock
    private WorkflowService workflowService;

    private ExecutionResultAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ExecutionResultAssembler(
                new ObjectMapper(), snapshotService, workflowService, new WorkflowSnapshotCache());
    }

    @Test
    void firstRowReturnsRowsZeroAndIgnoresLogPreview() {
        stubSchema("{\"mode\":\"firstRow\"}");
        ExecutionVO vo = execution(
                "{\"n1\":{\"rowCount\":1,\"rows\":[{\"id\":1,\"username\":\"admin\",\"role\":\"ADMIN\"}],\"preview\":[{\"id\":99}]}}",
                "{\"preview\":[{\"id\":99,\"username\":\"log\"}]}");

        Object output = assembler.projectPublicOutput(vo);

        Map<?, ?> row = assertInstanceOf(Map.class, output);
        assertEquals(1, row.get("id"));
        assertEquals("admin", row.get("username"));
        assertFalse(row.containsKey("preview"));
    }

    @Test
    void firstRowWithoutRowsReturnsNull() {
        stubSchema("{\"mode\":\"firstRow\"}");
        ExecutionVO vo = execution("{\"n1\":{\"rowCount\":0,\"rows\":[],\"truncated\":false}}",
                "{\"rows\":[]}");

        assertNull(assembler.projectPublicOutput(vo));
    }

    @Test
    void lastStepDropsPreviewAndKeepsRows() {
        stubSchema("{\"mode\":\"last\"}");
        ExecutionVO vo = execution(
                "{\"n1\":{\"rowCount\":2,\"rows\":[{\"id\":1},{\"id\":2}],\"preview\":[{\"id\":1}]}}",
                "{\"preview\":[{\"id\":1}]}");

        Object output = assembler.projectPublicOutput(vo);
        Map<?, ?> body = assertInstanceOf(Map.class, output);
        assertFalse(body.containsKey("preview"));
        assertEquals(2, ((List<?>) body.get("rows")).size());
    }

    @Test
    void openApiErrorDoesNotLeakNodeName() {
        stubSchema("{\"mode\":\"last\"}");
        ExecutionVO vo = execution("{\"n1\":{\"ok\":true}}", "{\"ok\":true}");
        vo.getInstance().setStatus("FAILED");
        vo.getInstance().setErrorMsg("节点 按照ID查询用户 失败: HTTP 502");

        assertEquals("HTTP 502", assembler.forOpenApi(vo).getErrorMsg());
        assertEquals("节点 按照ID查询用户 失败: HTTP 502", vo.getInstance().getErrorMsg());
    }

    private void stubSchema(String schema) {
        WorkflowSnapshot snapshot = new WorkflowSnapshot();
        snapshot.setId(9L);
        snapshot.setOutputSchema(schema);
        when(snapshotService.getById(9L)).thenReturn(snapshot);
    }

    private static ExecutionVO execution(String outputResult, String logBody) {
        ExecutionInstance instance = new ExecutionInstance();
        instance.setId(1L);
        instance.setSnapshotId(9L);
        instance.setStatus("SUCCESS");
        instance.setOutputResult(outputResult);
        ExecutionNodeLog log = new ExecutionNodeLog();
        log.setNodeId("n1");
        log.setNodeName("按照ID查询用户");
        log.setStatus("SUCCESS");
        log.setResponseBody(logBody);
        ExecutionVO vo = new ExecutionVO();
        vo.setInstance(instance);
        vo.setLogs(List.of(log));
        return vo;
    }
}
