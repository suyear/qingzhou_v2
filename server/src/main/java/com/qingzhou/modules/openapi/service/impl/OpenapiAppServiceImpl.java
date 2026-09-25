package com.qingzhou.modules.openapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.crypto.AesEncryptor;
import com.qingzhou.common.crypto.SignatureUtil;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.common.json.Jsons;
import com.qingzhou.modules.openapi.dto.OpenapiAppBindRequest;
import com.qingzhou.modules.openapi.dto.OpenapiAppCreateRequest;
import com.qingzhou.modules.openapi.dto.OpenapiAppCreatedVO;
import com.qingzhou.modules.openapi.dto.OpenapiAppListVO;
import com.qingzhou.modules.openapi.dto.OpenapiAppUpdateRequest;
import com.qingzhou.modules.openapi.security.IpRules;
import com.qingzhou.modules.openapi.entity.OpenapiApp;
import com.qingzhou.modules.openapi.entity.OpenapiAppWorkflow;
import com.qingzhou.modules.openapi.mapper.OpenapiAppMapper;
import com.qingzhou.modules.openapi.service.OpenapiAppService;
import com.qingzhou.modules.openapi.service.OpenapiAppWorkflowService;
import com.qingzhou.modules.workflow.entity.Workflow;
import com.qingzhou.modules.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OpenapiAppServiceImpl extends ServiceImpl<OpenapiAppMapper, OpenapiApp> implements OpenapiAppService {

    private final AesEncryptor aesEncryptor;
    private final OpenapiAppWorkflowService openapiAppWorkflowService;
    private final WorkflowService workflowService;
    private final Jsons jsons;

    @Override
    public IPage<OpenapiAppListVO> pageApps(PageQuery query) {
        LambdaQueryWrapper<OpenapiApp> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(OpenapiApp::getAppName, keyword).or().like(OpenapiApp::getAppKey, keyword));
        }
        wrapper.orderByDesc(OpenapiApp::getUpdateTime);
        IPage<OpenapiApp> page = page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        List<Long> appIds = page.getRecords().stream().map(OpenapiApp::getId).toList();
        Map<Long, Long> grantCounts = countGrants(appIds);
        return page.convert(item -> toListVo(item, grantCounts.getOrDefault(item.getId(), 0L)));
    }

    @Override
    @Transactional
    public OpenapiAppCreatedVO createApp(OpenapiAppCreateRequest request) {
        String appKey = "ak_" + SignatureUtil.randomNonce().substring(0, 16);
        String appSecret = SignatureUtil.randomNonce() + SignatureUtil.randomNonce();
        OpenapiApp app = new OpenapiApp();
        app.setAppName(request.getAppName());
        app.setAppKey(appKey);
        app.setAppSecretCipher(aesEncryptor.encrypt(appSecret));
        app.setStatus(1);
        app.setRateLimitQps(normalizeQps(request.getRateLimitQps(), 10));
        app.setIpWhitelist(normalizeWhitelist(request.getIpWhitelist()));
        app.setRemark(request.getRemark());
        save(app);

        OpenapiAppCreatedVO vo = new OpenapiAppCreatedVO();
        vo.setId(app.getId());
        vo.setAppName(app.getAppName());
        vo.setAppKey(appKey);
        vo.setAppSecret(appSecret);
        return vo;
    }

    @Override
    @Transactional
    public OpenapiApp updateApp(Long id, OpenapiAppUpdateRequest request) {
        OpenapiApp app = requireApp(id);
        app.setAppName(request.getAppName().trim());
        app.setRateLimitQps(normalizeQps(request.getRateLimitQps(), app.getRateLimitQps()));
        app.setIpWhitelist(normalizeWhitelist(request.getIpWhitelist()));
        if (request.getStatus() != null) {
            app.setStatus(request.getStatus() == 1 ? 1 : 0);
        }
        app.setRemark(request.getRemark());
        updateById(app);
        return getById(id);
    }

    @Override
    public List<Long> listGrantedWorkflowIds(Long appId) {
        requireApp(appId);
        return openapiAppWorkflowService.lambdaQuery()
                .eq(OpenapiAppWorkflow::getAppId, appId)
                .list()
                .stream()
                .map(OpenapiAppWorkflow::getWorkflowId)
                .toList();
    }

    @Override
    @Transactional
    public OpenapiAppCreatedVO resetSecret(Long id) {
        OpenapiApp app = requireApp(id);
        String appSecret = SignatureUtil.randomNonce() + SignatureUtil.randomNonce();
        app.setAppSecretCipher(aesEncryptor.encrypt(appSecret));
        updateById(app);
        OpenapiAppCreatedVO vo = new OpenapiAppCreatedVO();
        vo.setId(app.getId());
        vo.setAppName(app.getAppName());
        vo.setAppKey(app.getAppKey());
        vo.setAppSecret(appSecret);
        return vo;
    }

    @Override
    @Transactional
    public void bindWorkflows(Long appId, OpenapiAppBindRequest request) {
        requireApp(appId);
        List<Long> ids = request.getWorkflowIds() == null ? List.of() : request.getWorkflowIds();
        Set<Long> target = new HashSet<>(ids);
        for (Long workflowId : target) {
            Workflow workflow = workflowService.getById(workflowId);
            if (workflow == null) {
                throw new BizException(ResultCode.NOT_FOUND, "工作流不存在: " + workflowId);
            }
            if (!"PUBLISHED".equals(workflow.getStatus())) {
                throw new BizException(ResultCode.BAD_REQUEST, "仅可授权已发布的工作流: " + workflow.getWorkflowName());
            }
        }
        openapiAppWorkflowService.remove(new LambdaQueryWrapper<OpenapiAppWorkflow>()
                .eq(OpenapiAppWorkflow::getAppId, appId));
        for (Long workflowId : target) {
            OpenapiAppWorkflow bind = new OpenapiAppWorkflow();
            bind.setAppId(appId);
            bind.setWorkflowId(workflowId);
            openapiAppWorkflowService.save(bind);
        }
    }

    @Override
    public void assertGranted(Long appId, String workflowCode) {
        if (appId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "缺少调用方应用");
        }
        Workflow workflow = workflowService.lambdaQuery()
                .eq(Workflow::getWorkflowCode, workflowCode)
                .one();
        if (workflow == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工作流不存在: " + workflowCode);
        }
        if (!"PUBLISHED".equals(workflow.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "工作流未发布，无法通过 OpenAPI 执行");
        }
        boolean granted = openapiAppWorkflowService.lambdaQuery()
                .eq(OpenapiAppWorkflow::getAppId, appId)
                .eq(OpenapiAppWorkflow::getWorkflowId, workflow.getId())
                .exists();
        if (!granted) {
            throw new BizException(ResultCode.FORBIDDEN, "应用未授权该工作流");
        }
    }

    private OpenapiApp requireApp(Long appId) {
        OpenapiApp app = getById(appId);
        if (app == null) {
            throw new BizException(ResultCode.NOT_FOUND, "OpenAPI 应用不存在");
        }
        return app;
    }

    private static int normalizeQps(Integer incoming, Integer fallback) {
        if (incoming != null && incoming >= 0) {
            return incoming;
        }
        return fallback == null ? 10 : fallback;
    }

    private String normalizeWhitelist(String raw) {
        var rules = IpRules.parse(raw);
        return rules.isEmpty() ? null : jsons.toJson(rules);
    }

    private Map<Long, Long> countGrants(List<Long> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return Map.of();
        }
        return openapiAppWorkflowService.lambdaQuery()
                .in(OpenapiAppWorkflow::getAppId, appIds)
                .list()
                .stream()
                .collect(Collectors.groupingBy(OpenapiAppWorkflow::getAppId, Collectors.counting()));
    }

    private OpenapiAppListVO toListVo(OpenapiApp app, long grantedCount) {
        OpenapiAppListVO vo = new OpenapiAppListVO();
        vo.setId(app.getId());
        vo.setAppName(app.getAppName());
        vo.setAppKey(app.getAppKey());
        vo.setAppSecretCipher(app.getAppSecretCipher());
        vo.setStatus(app.getStatus());
        vo.setRateLimitQps(app.getRateLimitQps());
        vo.setIpWhitelist(app.getIpWhitelist());
        vo.setRemark(app.getRemark());
        vo.setExpireTime(app.getExpireTime());
        vo.setCreateTime(app.getCreateTime());
        vo.setUpdateTime(app.getUpdateTime());
        vo.setGrantedCount((int) grantedCount);
        return vo;
    }
}
