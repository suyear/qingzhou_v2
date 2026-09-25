package com.qingzhou.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.common.json.Jsons;
import com.qingzhou.modules.workflow.dto.DagGraph;
import com.qingzhou.modules.workflow.dto.ParamMappingItem;
import com.qingzhou.modules.workflow.dto.WorkflowSaveRequest;
import com.qingzhou.modules.workflow.dto.WorkflowVO;
import com.qingzhou.modules.workflow.entity.Workflow;
import com.qingzhou.modules.workflow.entity.WorkflowSnapshot;
import com.qingzhou.modules.workflow.mapper.WorkflowMapper;
import com.qingzhou.modules.workflow.service.WorkflowService;
import com.qingzhou.modules.workflow.service.WorkflowSnapshotService;
import com.qingzhou.modules.workflow.support.DagValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl extends ServiceImpl<WorkflowMapper, Workflow> implements WorkflowService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final Jsons jsons;
    private final ObjectMapper objectMapper;
    private final WorkflowSnapshotService workflowSnapshotService;

    @Override
    @Transactional
    public WorkflowVO create(WorkflowSaveRequest request) {
        DagValidator.validate(request.getGraph(), request.getParamMapping());
        String code = request.getWorkflowCode().trim();
        assertCodeUnique(code, null);

        Workflow entity = new Workflow();
        fill(entity, request, code);
        entity.setStatus(STATUS_DRAFT);
        entity.setVersion(1);
        if (entity.getTimeoutMs() == null) {
            entity.setTimeoutMs(60000);
        }
        if (!StringUtils.hasText(entity.getCredentialMode())) {
            entity.setCredentialMode("GLOBAL");
        }
        save(entity);
        return toVo(entity);
    }

    @Override
    @Transactional
    public WorkflowVO update(Long id, WorkflowSaveRequest request) {
        Workflow entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工作流不存在");
        }
        DagValidator.validate(request.getGraph(), request.getParamMapping());
        String code = request.getWorkflowCode().trim();
        assertCodeUnique(code, id);
        fill(entity, request, code);
        updateById(entity);
        return detail(id);
    }

    @Override
    public WorkflowVO detail(Long id) {
        Workflow entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工作流不存在");
        }
        return toVo(entity);
    }

    @Override
    @Transactional
    public WorkflowVO publish(Long id) {
        Workflow entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工作流不存在");
        }
        DagGraph graph = parseGraph(entity.getGraphJson());
        List<ParamMappingItem> mappings = parseMappings(entity.getParamMapping());
        DagValidator.validate(graph, mappings);
        if (graph.getNodes() == null || graph.getNodes().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请先编排至少一个节点再发布");
        }
        int version = entity.getPublishTime() == null
                ? Math.max(entity.getVersion() == null ? 1 : entity.getVersion(), 1)
                : entity.getVersion() + 1;
        LocalDateTime now = LocalDateTime.now();
        WorkflowSnapshot snapshot = new WorkflowSnapshot();
        snapshot.setWorkflowId(entity.getId());
        snapshot.setVersion(version);
        snapshot.setGraphJson(entity.getGraphJson());
        snapshot.setParamMapping(entity.getParamMapping());
        snapshot.setInputSchema(entity.getInputSchema());
        snapshot.setOutputSchema(entity.getOutputSchema());
        snapshot.setCredentialMode(entity.getCredentialMode());
        snapshot.setCredentialId(entity.getCredentialId());
        snapshot.setTimeoutMs(entity.getTimeoutMs());
        snapshot.setPublishTime(now);
        workflowSnapshotService.save(snapshot);

        entity.setVersion(version);
        entity.setStatus(STATUS_PUBLISHED);
        entity.setPublishTime(now);
        updateById(entity);
        return detail(id);
    }

    @Override
    @Transactional
    public WorkflowVO disable(Long id) {
        Workflow entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工作流不存在");
        }
        entity.setStatus(STATUS_DISABLED);
        updateById(entity);
        return detail(id);
    }

    private void fill(Workflow entity, WorkflowSaveRequest request, String code) {
        entity.setWorkflowCode(code);
        entity.setWorkflowName(request.getWorkflowName().trim());
        entity.setDescription(request.getDescription());
        entity.setGraphJson(jsons.toJson(request.getGraph() == null ? new DagGraph() : request.getGraph()));
        List<ParamMappingItem> mappings = request.getParamMapping() == null
                ? new ArrayList<>()
                : request.getParamMapping();
        entity.setParamMapping(jsons.toJson(mappings));
        if (request.getInputSchema() != null) {
            entity.setInputSchema(jsons.toJson(request.getInputSchema()));
        }
        if (request.getOutputSchema() != null) {
            entity.setOutputSchema(jsons.toJson(request.getOutputSchema()));
        }
        if (StringUtils.hasText(request.getCredentialMode())) {
            entity.setCredentialMode(request.getCredentialMode().trim().toUpperCase(Locale.ROOT));
        }
        if ("INDEPENDENT".equals(entity.getCredentialMode())) {
            Long credId = request.getCredentialId() != null ? request.getCredentialId() : entity.getCredentialId();
            if (credId == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "独立凭证模式必须选择凭证");
            }
            entity.setCredentialId(credId);
        } else {
            entity.setCredentialId(null);
        }
        if (request.getTimeoutMs() != null && request.getTimeoutMs() > 0) {
            entity.setTimeoutMs(request.getTimeoutMs());
        }
        entity.setCronExpr(request.getCronExpr());
    }

    private void assertCodeUnique(String code, Long excludeId) {
        long count = count(new LambdaQueryWrapper<Workflow>()
                .eq(Workflow::getWorkflowCode, code)
                .ne(excludeId != null, Workflow::getId, excludeId));
        if (count > 0) {
            throw new BizException(ResultCode.CONFLICT, "工作流编码已存在: " + code);
        }
    }

    private WorkflowVO toVo(Workflow entity) {
        WorkflowVO vo = new WorkflowVO();
        vo.setId(entity.getId());
        vo.setWorkflowCode(entity.getWorkflowCode());
        vo.setWorkflowName(entity.getWorkflowName());
        vo.setDescription(entity.getDescription());
        vo.setStatus(entity.getStatus());
        vo.setVersion(entity.getVersion());
        vo.setGraph(parseGraph(entity.getGraphJson()));
        vo.setParamMapping(parseMappings(entity.getParamMapping()));
        vo.setInputSchema(jsons.toObject(entity.getInputSchema()));
        vo.setOutputSchema(jsons.toObject(entity.getOutputSchema()));
        vo.setCredentialMode(entity.getCredentialMode());
        vo.setCredentialId(entity.getCredentialId());
        vo.setTimeoutMs(entity.getTimeoutMs());
        vo.setCronExpr(entity.getCronExpr());
        vo.setXxlJobId(entity.getXxlJobId());
        vo.setPublishTime(entity.getPublishTime());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private DagGraph parseGraph(String json) {
        DagGraph graph = jsons.fromJson(json, DagGraph.class);
        return graph == null ? new DagGraph() : graph;
    }

    private List<ParamMappingItem> parseMappings(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BizException(ResultCode.SERVER_ERROR, "param_mapping 反序列化失败");
        }
    }
}
