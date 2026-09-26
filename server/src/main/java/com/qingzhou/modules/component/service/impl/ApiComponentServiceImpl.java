package com.qingzhou.modules.component.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.common.json.Jsons;
import com.qingzhou.infra.wecom.TokenManager;
import com.qingzhou.modules.component.dto.ComponentSaveRequest;
import com.qingzhou.modules.component.dto.ComponentTestRequest;
import com.qingzhou.modules.component.dto.ComponentTestVO;
import com.qingzhou.modules.component.entity.ApiComponent;
import com.qingzhou.modules.component.mapper.ApiComponentMapper;
import com.qingzhou.modules.component.service.ApiComponentService;
import com.qingzhou.modules.execution.engine.DatabaseComponentSupport;
import com.qingzhou.modules.execution.engine.ExecutionLimits;
import com.qingzhou.modules.execution.engine.DbCallResult;
import com.qingzhou.modules.execution.engine.HttpAuthSupport;
import com.qingzhou.modules.execution.engine.HttpCallResult;
import com.qingzhou.modules.execution.engine.HttpUrlSupport;
import com.qingzhou.modules.execution.engine.OutboundAuthResolver;
import com.qingzhou.modules.execution.engine.NodeHttpInvoker;
import com.qingzhou.modules.execution.engine.NodeJdbcInvoker;
import com.qingzhou.modules.execution.engine.sql.BoundSql;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApiComponentServiceImpl extends ServiceImpl<ApiComponentMapper, ApiComponent>
        implements ApiComponentService {

    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");
    private static final Set<String> PROVIDERS = Set.of("WECOM", "CUSTOM", "DATABASE");

    private final Jsons jsons;
    private final ObjectMapper objectMapper;
    private final NodeHttpInvoker nodeHttpInvoker;
    private final NodeJdbcInvoker nodeJdbcInvoker;
    private final OutboundAuthResolver outboundAuthResolver;
    private final TokenManager tokenManager;

    @Override
    @Transactional
    public ApiComponent create(ComponentSaveRequest request) {
        String code = normalizeCode(request.getComponentCode());
        assertCodeUnique(code, null);
        ApiComponent entity = new ApiComponent();
        fill(entity, request, code);
        entity.setIsPreset(0);
        if (entity.getProvider() == null) {
            entity.setProvider("CUSTOM");
        }
        if (entity.getCategory() == null) {
            entity.setCategory("HTTP");
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        save(entity);
        return entity;
    }

    @Override
    @Transactional
    public ApiComponent update(Long id, ComponentSaveRequest request) {
        ApiComponent entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "接口组件不存在");
        }
        String code = normalizeCode(request.getComponentCode());
        if (Integer.valueOf(1).equals(entity.getIsPreset()) && !code.equals(entity.getComponentCode())) {
            throw new BizException(ResultCode.BAD_REQUEST, "预置组件不允许修改编码");
        }
        assertCodeUnique(code, id);
        fill(entity, request, code);
        updateById(entity);
        return getById(id);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        ApiComponent entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "接口组件不存在");
        }
        if (Integer.valueOf(1).equals(entity.getIsPreset())) {
            throw new BizException(ResultCode.BAD_REQUEST, "预置组件不允许删除");
        }
        removeById(id);
    }

    @Override
    public ComponentTestVO test(Long id, ComponentTestRequest request) {
        return test(id, request, false);
    }

    @Override
    public ComponentTestVO test(Long id, ComponentTestRequest request, boolean preserveResponse) {
        ApiComponent component = getById(id);
        if (component == null) {
            throw new BizException(ResultCode.NOT_FOUND, "接口组件不存在");
        }
        if (DatabaseComponentSupport.isDatabase(component)) {
            return testDatabase(component, request);
        }
        ComponentTestRequest safe = request == null ? new ComponentTestRequest() : request;
        Map<String, Object> params = safe.getParams() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(safe.getParams());

        String token = null;
        HttpAuthSupport.AuthResult inlineAuth = HttpAuthSupport.resolve(component);
        if (HttpUrlSupport.needsAccessToken(component) || inlineAuth.needsWecomToken()) {
            try {
                token = safe.getCredentialId() != null
                        ? tokenManager.getAccessToken(safe.getCredentialId())
                        : tokenManager.getGlobalAccessToken();
            } catch (BizException ex) {
                return ComponentTestVO.fail(ex.getMessage());
            }
        }

        String rawUrl;
        URI uri;
        try {
            rawUrl = HttpUrlSupport.renderUrl(component.getUrlTemplate(), params, token);
            uri = HttpUrlSupport.buildUri(rawUrl, component.getHttpMethod(), params);
        } catch (BizException ex) {
            return ComponentTestVO.fail(ex.getMessage());
        } catch (Exception ex) {
            return ComponentTestVO.fail("URL 无法解析: " + ex.getMessage());
        }

        OutboundAuthResolver.ResolvedAuth auth;
        try {
            auth = outboundAuthResolver.resolve(component, component.getHttpMethod(), uri);
        } catch (BizException ex) {
            return ComponentTestVO.fail(ex.getMessage());
        }
        uri = HttpAuthSupport.appendQueryParams(uri, auth.queryParams());

        int timeout = ExecutionLimits.nodeTimeout(component.getTimeoutMs());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.putAll(auth.headers());
        String body = "GET".equalsIgnoreCase(component.getHttpMethod()) || "DELETE".equalsIgnoreCase(component.getHttpMethod())
                ? null
                : jsons.toJson(params);

        long started = System.currentTimeMillis();
        HttpCallResult result = nodeHttpInvoker.invoke(component.getHttpMethod(), uri, headers, body, timeout, auth);
        long duration = System.currentTimeMillis() - started;

        ComponentTestVO vo = new ComponentTestVO();
        vo.setRequestMethod(component.getHttpMethod());
        vo.setRequestUrl(HttpUrlSupport.maskSecret(uri.toString()));
        vo.setHttpStatus(result.status() == 0 ? null : result.status());
        vo.setDurationMs(duration);
        vo.setResponseBody(preserveResponse ? result.body() : HttpUrlSupport.truncateBody(result.body()));

        Integer wecomCode = readWecomErrcode(result.body());
        String wecomMsg = readWecomErrmsg(result.body());
        vo.setWecomErrcode(wecomCode);

        if (result.timeout()) {
            vo.setSuccess(false);
            vo.setMessage("第三方接口超时");
            return vo;
        }
        if (result.error() != null) {
            vo.setSuccess(false);
            vo.setMessage(result.error());
            return vo;
        }
        if (wecomCode != null && wecomCode != 0) {
            vo.setSuccess(false);
            vo.setMessage(wecomMsg == null ? "企业微信返回 errcode=" + wecomCode : wecomMsg);
            return vo;
        }
        if (result.success()) {
            vo.setSuccess(true);
            vo.setMessage("连通成功 HTTP " + result.status());
            return vo;
        }
        vo.setSuccess(false);
        vo.setMessage("HTTP " + result.status());
        return vo;
    }

    private ComponentTestVO testDatabase(ApiComponent component, ComponentTestRequest request) {
        ComponentTestRequest safe = request == null ? new ComponentTestRequest() : request;
        Map<String, Object> params = safe.getParams() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(safe.getParams());
        DatabaseComponentSupport.DatabaseSpec spec;
        try {
            spec = DatabaseComponentSupport.spec(component, jsons);
        } catch (BizException ex) {
            return ComponentTestVO.fail(ex.getMessage());
        }
        int timeout = ExecutionLimits.nodeTimeout(component.getTimeoutMs());
        long started = System.currentTimeMillis();
        DbCallResult result = nodeJdbcInvoker.invoke(spec, params, timeout);
        long duration = System.currentTimeMillis() - started;

        ComponentTestVO vo = new ComponentTestVO();
        vo.setRequestMethod(spec.method());
        vo.setRequestUrl(result.displayUrl());
        vo.setDurationMs(duration);
        // 对外 / 试连通均返回业务 output（含 rows），logBody.preview 仅用于执行日志截断展示
        vo.setResponseBody(jsons.toJson(result.output() == null || result.output().isEmpty()
                ? result.logBody() : result.output()));
        vo.setHttpStatus(result.success() ? 200 : (result.timeout() ? 504 : 400));
        vo.setSuccess(result.success());
        if (result.timeout()) {
            vo.setMessage("数据库语句超时");
            return vo;
        }
        vo.setMessage(result.error() == null
                ? (result.success() ? "SQL 执行成功" : "SQL 执行失败")
                : result.error());
        return vo;
    }

    private Integer readWecomErrcode(String body) {
        Map<String, Object> json = readJsonMap(body);
        if (json == null || !json.containsKey("errcode")) {
            return null;
        }
        Object raw = json.get("errcode");
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String readWecomErrmsg(String body) {
        Map<String, Object> json = readJsonMap(body);
        if (json == null) {
            return null;
        }
        Object raw = json.get("errmsg");
        return raw == null ? null : String.valueOf(raw);
    }

    private Map<String, Object> readJsonMap(String body) {
        if (!StringUtils.hasText(body) || !body.trim().startsWith("{")) {
            return null;
        }
        try {
            return objectMapper.readValue(body, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    private void fill(ApiComponent entity, ComponentSaveRequest request, String code) {
        String provider = StringUtils.hasText(request.getProvider())
                ? request.getProvider().trim().toUpperCase(Locale.ROOT)
                : entity.getProvider();
        if (provider != null && !PROVIDERS.contains(provider)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的提供方: " + provider);
        }
        boolean database = DatabaseComponentSupport.isDatabase(
                provider,
                request.getCategory(),
                request.getHttpMethod());

        entity.setComponentCode(code);
        entity.setComponentName(request.getComponentName().trim());
        entity.setProvider(provider);
        if (StringUtils.hasText(request.getCategory())) {
            entity.setCategory(request.getCategory().trim().toUpperCase(Locale.ROOT));
        }
        if (database) {
            fillDatabase(entity, request);
            return;
        }
        if (!StringUtils.hasText(request.getHttpMethod())) {
            throw new BizException(ResultCode.BAD_REQUEST, "HTTP 方法不能为空");
        }
        if (!StringUtils.hasText(request.getUrlTemplate())) {
            throw new BizException(ResultCode.BAD_REQUEST, "URL 不能为空");
        }
        String method = request.getHttpMethod().trim().toUpperCase(Locale.ROOT);
        if (!HTTP_METHODS.contains(method)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的 HTTP 方法: " + method);
        }
        if (request.getUrlTemplate().trim().length() > 2048) {
            throw new BizException(ResultCode.BAD_REQUEST, "URL 最长 2048");
        }

        entity.setHttpMethod(method);
        entity.setUrlTemplate(request.getUrlTemplate().trim());
        entity.setHeadersSchema(jsons.toJson(request.getHeadersSchema()));
        entity.setQuerySchema(jsons.toJson(request.getQuerySchema()));
        entity.setBodySchema(jsons.toJson(request.getBodySchema()));
        entity.setResponseSchema(jsons.toJson(request.getResponseSchema()));
        entity.setExtraConfig(jsons.toJson(request.getExtraConfig()));
        entity.setTimeoutMs(defaultPositive(request.getTimeoutMs(), entity.getTimeoutMs(), 10000));
        entity.setRetryTimes(defaultNonNegative(request.getRetryTimes(), entity.getRetryTimes(), 0));
        entity.setRetryIntervalMs(defaultPositive(request.getRetryIntervalMs(), entity.getRetryIntervalMs(), 1000));
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        entity.setDescription(request.getDescription());
    }

    @SuppressWarnings("unchecked")
    private void fillDatabase(ApiComponent entity, ComponentSaveRequest request) {
        if (!StringUtils.hasText(request.getUrlTemplate())) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写 SQL 脚本");
        }
        if (request.getUrlTemplate().trim().length() > 20000) {
            throw new BizException(ResultCode.BAD_REQUEST, "SQL 最长 20000 字符");
        }
        Map<String, Object> extra = asMap(request.getExtraConfig());
        Object rawId = extra.get("datasourceId");
        Long datasourceId = rawId instanceof Number number ? number.longValue() : parseLong(rawId);
        if (datasourceId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择 MySQL 数据源");
        }
        String accessMode = extra.get("accessMode") == null ? "READ" : String.valueOf(extra.get("accessMode"));
        Integer maxRows = extra.get("maxRows") instanceof Number number ? number.intValue() : null;
        DatabaseComponentSupport.DatabaseSpec spec = DatabaseComponentSupport.spec(
                fakeComponent(request.getUrlTemplate(), datasourceId, accessMode, maxRows), jsons);
        BoundSql bound = spec.bound();
        Map<String, Object> storedExtra = DatabaseComponentSupport.extraConfig(datasourceId, accessMode, maxRows);
        entity.setProvider(DatabaseComponentSupport.PROVIDER);
        entity.setCategory(DatabaseComponentSupport.CATEGORY);
        entity.setHttpMethod(spec.method());
        entity.setUrlTemplate(request.getUrlTemplate().trim());
        entity.setHeadersSchema(null);
        entity.setQuerySchema(null);
        entity.setBodySchema(jsons.toJson(mergeParamSchema(request.getBodySchema(), bound)));
        entity.setResponseSchema(jsons.toJson(
                request.getResponseSchema() == null
                        ? DatabaseComponentSupport.defaultResponseSchema()
                        : request.getResponseSchema()));
        entity.setExtraConfig(jsons.toJson(storedExtra));
        entity.setTimeoutMs(defaultPositive(request.getTimeoutMs(), entity.getTimeoutMs(), 10000));
        entity.setRetryTimes(defaultNonNegative(request.getRetryTimes(), entity.getRetryTimes(), 0));
        entity.setRetryIntervalMs(defaultPositive(request.getRetryIntervalMs(), entity.getRetryIntervalMs(), 1000));
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        entity.setDescription(request.getDescription());
    }

    private ApiComponent fakeComponent(String sql, Long datasourceId, String accessMode, Integer maxRows) {
        ApiComponent fake = new ApiComponent();
        fake.setProvider(DatabaseComponentSupport.PROVIDER);
        fake.setCategory(DatabaseComponentSupport.CATEGORY);
        fake.setUrlTemplate(sql);
        fake.setExtraConfig(jsons.toJson(DatabaseComponentSupport.extraConfig(datasourceId, accessMode, maxRows)));
        return fake;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeParamSchema(Object incoming, BoundSql bound) {
        Map<String, Object> generated = DatabaseComponentSupport.paramsToSchema(bound);
        if (incoming == null) {
            return generated;
        }
        Map<String, Object> incomingMap = asMap(incoming);
        Map<String, Object> incomingProps = incomingMap.get("properties") instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
        Map<String, Object> properties = (Map<String, Object>) generated.get("properties");
        for (String name : bound.paramNames()) {
            if (incomingProps.get(name) instanceof Map<?, ?> keep) {
                properties.put(name, keep);
            }
        }
        Object required = incomingMap.get("required");
        if (required instanceof java.util.Collection<?> collection) {
            generated.put("required", collection);
        }
        return generated;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object raw) {
        if (raw == null) {
            return new LinkedHashMap<>();
        }
        if (raw instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            Object parsed = jsons.toObject(text);
            if (parsed instanceof Map<?, ?> map) {
                return new LinkedHashMap<>((Map<String, Object>) map);
            }
        }
        return new LinkedHashMap<>();
    }

    private static Long parseLong(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void assertCodeUnique(String code, Long excludeId) {
        long count = count(new LambdaQueryWrapper<ApiComponent>()
                .eq(ApiComponent::getComponentCode, code)
                .ne(excludeId != null, ApiComponent::getId, excludeId));
        if (count > 0) {
            throw new BizException(ResultCode.CONFLICT, "组件编码已存在: " + code);
        }
    }

    private static String normalizeCode(String code) {
        return code.trim();
    }

    private static Integer defaultPositive(Integer incoming, Integer current, int fallback) {
        if (incoming != null && incoming > 0) {
            return incoming;
        }
        return current != null && current > 0 ? current : fallback;
    }

    private static Integer defaultNonNegative(Integer incoming, Integer current, int fallback) {
        if (incoming != null && incoming >= 0) {
            return incoming;
        }
        return current != null && current >= 0 ? current : fallback;
    }
}
