package com.qingzhou.modules.credential.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.crypto.AesEncryptor;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.common.json.Jsons;
import com.qingzhou.infra.wecom.TokenManager;
import com.qingzhou.modules.credential.dto.CredentialSaveRequest;
import com.qingzhou.modules.credential.dto.CredentialTestVO;
import com.qingzhou.modules.credential.dto.CredentialVO;
import com.qingzhou.modules.credential.entity.Credential;
import com.qingzhou.modules.credential.mapper.CredentialMapper;
import com.qingzhou.modules.credential.service.CredentialService;
import com.qingzhou.modules.credential.support.HttpAuthCredentialSupport;
import com.qingzhou.modules.credential.support.JdbcCredentialSupport;
import com.qingzhou.modules.execution.engine.DatasourcePoolManager;
import com.qingzhou.modules.execution.engine.auth.MtlsHttpClientFactory;
import com.qingzhou.modules.execution.engine.auth.OAuth2ClientCredentialsClient;
import com.qingzhou.modules.workflow.entity.Workflow;
import com.qingzhou.modules.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CredentialServiceImpl extends ServiceImpl<CredentialMapper, Credential> implements CredentialService {

    private static final String TYPE_WECOM = "WECOM";
    private static final String TYPE_HTTP_AUTH = HttpAuthCredentialSupport.TYPE_HTTP_AUTH;
    private static final String TYPE_DATABASE = JdbcCredentialSupport.TYPE_DATABASE;
    private static final String TYPE_CUSTOM_LEGACY = HttpAuthCredentialSupport.TYPE_CUSTOM_LEGACY;
    private static final String TYPE_MYSQL_LEGACY = JdbcCredentialSupport.TYPE_MYSQL_LEGACY;
    private static final String SCOPE_GLOBAL = "GLOBAL";
    private static final String SCOPE_WORKFLOW = "WORKFLOW";

    private final AesEncryptor aesEncryptor;
    private final Jsons jsons;
    private final WorkflowService workflowService;
    private final ObjectProvider<TokenManager> tokenManager;
    private final ObjectProvider<DatasourcePoolManager> datasourcePoolManager;
    private final ObjectProvider<OAuth2ClientCredentialsClient> oauth2Client;
    private final ObjectProvider<MtlsHttpClientFactory> mtlsFactory;

    @Override
    public IPage<CredentialVO> pageVo(PageQuery query) {
        LambdaQueryWrapper<Credential> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(Credential::getCredentialName, query.getKeyword())
                        .or()
                        .like(Credential::getCorpId, query.getKeyword())
                        .or()
                        .like(Credential::getExtraConfig, query.getKeyword()))
                .orderByDesc(Credential::getUpdateTime);
        return page(new Page<>(query.getCurrent(), query.getSize()), wrapper).convert(this::toVo);
    }

    @Override
    public CredentialVO detail(Long id) {
        return toVo(require(id));
    }

    @Override
    @Transactional
    public CredentialVO create(CredentialSaveRequest request) {
        Credential entity = new Credential();
        fill(entity, request, true);
        save(entity);
        return toVo(entity);
    }

    @Override
    @Transactional
    public CredentialVO update(Long id, CredentialSaveRequest request) {
        Credential entity = require(id);
        boolean rotateSecret = hasIncomingSecrets(request);
        fill(entity, request, false);
        updateById(entity);
        if (rotateSecret || JdbcCredentialSupport.isDatabase(entity) || HttpAuthCredentialSupport.isHttpAuth(entity)) {
            evict(id);
        }
        return detail(id);
    }

    @Override
    @Transactional
    public void removeCredential(Long id) {
        require(id);
        evict(id);
        removeById(id);
    }

    @Override
    @Transactional
    public CredentialVO changeStatus(Long id, int status) {
        Credential entity = require(id);
        entity.setStatus(status == 1 ? 1 : 0);
        updateById(entity);
        if (entity.getStatus() == 0) {
            evict(id);
        }
        return toVo(entity);
    }

    @Override
    public CredentialTestVO test(Long id) {
        Credential entity = require(id);
        if (JdbcCredentialSupport.isDatabase(entity)) {
            return testDatabase(entity);
        }
        if (HttpAuthCredentialSupport.isHttpAuth(entity)) {
            return testHttpAuth(entity);
        }
        try {
            tokenManager.getObject().getAccessToken(id);
            return new CredentialTestVO(true, "已拿到 AccessToken，凭证可用");
        } catch (BizException ex) {
            return new CredentialTestVO(false, ex.getMessage());
        }
    }

    private CredentialTestVO testDatabase(Credential entity) {
        DatasourcePoolManager pool = datasourcePoolManager.getIfAvailable();
        if (pool == null) {
            return new CredentialTestVO(false, "数据源连接池未就绪");
        }
        try (var connection = pool.getConnection(entity.getId());
             var statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            JdbcCredentialSupport.JdbcEndpoint endpoint = JdbcCredentialSupport.from(entity);
            statement.execute(JdbcCredentialSupport.validationQuery(endpoint.dbType()));
            return new CredentialTestVO(true, "已连通 " + endpoint.display());
        } catch (BizException ex) {
            return new CredentialTestVO(false, ex.getMessage());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "数据库连接失败" : ex.getMessage();
            return new CredentialTestVO(false, message);
        }
    }

    private CredentialTestVO testHttpAuth(Credential entity) {
        String authType = HttpAuthCredentialSupport.resolveAuthType(entity);
        Map<String, Object> extra = HttpAuthCredentialSupport.readExtra(entity);
        Map<String, String> secrets = HttpAuthCredentialSupport.decryptSecrets(entity, aesEncryptor);
        try {
            HttpAuthCredentialSupport.validateConfig(authType, extra, secrets, true);
        } catch (BizException ex) {
            return new CredentialTestVO(false, ex.getMessage());
        }
        if (HttpAuthCredentialSupport.AUTH_OAUTH2_CC.equals(authType)) {
            try {
                String token = oauth2Client.getObject().getAccessToken(entity.getId());
                return new CredentialTestVO(true, "已拿到 OAuth2 AccessToken（长度 " + token.length() + "）");
            } catch (BizException ex) {
                return new CredentialTestVO(false, ex.getMessage());
            }
        }
        if (HttpAuthCredentialSupport.AUTH_MTLS.equals(authType)) {
            try {
                mtlsFactory.getObject().validatePem(entity);
                return new CredentialTestVO(true, "客户端证书与私钥可解析");
            } catch (BizException ex) {
                return new CredentialTestVO(false, ex.getMessage());
            }
        }
        return new CredentialTestVO(true, "配置校验通过，可在 HTTP 组件中引用");
    }

    private void fill(Credential entity, CredentialSaveRequest request, boolean creating) {
        String type = normalizeType(request.getCredentialType());
        String scope = StringUtils.hasText(request.getScope())
                ? request.getScope().trim().toUpperCase(Locale.ROOT)
                : SCOPE_GLOBAL;
        if (!TYPE_WECOM.equals(type) && !TYPE_HTTP_AUTH.equals(type) && !TYPE_DATABASE.equals(type)) {
            throw new BizException(ResultCode.BAD_REQUEST, "凭证类型仅支持 WECOM / HTTP_AUTH / DATABASE");
        }
        if (!SCOPE_GLOBAL.equals(scope) && !SCOPE_WORKFLOW.equals(scope)) {
            throw new BizException(ResultCode.BAD_REQUEST, "作用域仅支持 GLOBAL / WORKFLOW");
        }
        if (SCOPE_WORKFLOW.equals(scope)) {
            if (request.getWorkflowId() == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "工作流独立凭证必须绑定工作流");
            }
            Workflow workflow = workflowService.getById(request.getWorkflowId());
            if (workflow == null) {
                throw new BizException(ResultCode.NOT_FOUND, "绑定的工作流不存在");
            }
            entity.setWorkflowId(request.getWorkflowId());
        } else {
            entity.setWorkflowId(null);
        }

        entity.setCredentialName(request.getCredentialName().trim());
        entity.setCredentialType(type);
        entity.setScope(scope);
        entity.setRemark(request.getRemark());
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus() == 1 ? 1 : 0);
        } else if (creating) {
            entity.setStatus(1);
        }

        if (TYPE_WECOM.equals(type)) {
            if (!StringUtils.hasText(request.getCorpId())) {
                throw new BizException(ResultCode.BAD_REQUEST, "企业微信凭证必须填写 CorpId");
            }
            entity.setCorpId(trimToNull(request.getCorpId()));
            entity.setAgentId(trimToNull(request.getAgentId()));
            entity.setExtraConfig(null);
            if (creating && !StringUtils.hasText(request.getSecret())) {
                throw new BizException(ResultCode.BAD_REQUEST, "Secret 不能为空");
            }
            if (StringUtils.hasText(request.getSecret())) {
                entity.setSecretCipher(aesEncryptor.encrypt(request.getSecret().trim()));
            }
            return;
        }

        entity.setCorpId(null);
        entity.setAgentId(null);

        if (TYPE_DATABASE.equals(type)) {
            Map<String, Object> extra = JdbcCredentialSupport.toExtra(
                    request.getDbType(), request.getDbHost(), request.getDbPort(),
                    request.getDbName(), request.getDbUsername());
            entity.setExtraConfig(jsons.toJson(extra));
            if (creating && !StringUtils.hasText(request.getSecret())) {
                throw new BizException(ResultCode.BAD_REQUEST, "数据库密码不能为空");
            }
            if (StringUtils.hasText(request.getSecret())) {
                entity.setSecretCipher(aesEncryptor.encrypt(request.getSecret().trim()));
            }
            return;
        }

        // HTTP_AUTH
        String authType = HttpAuthCredentialSupport.normalizeAuthType(
                StringUtils.hasText(request.getAuthType()) ? request.getAuthType() : "bearer");
        Map<String, Object> extra = buildHttpAuthExtra(request, authType);
        Map<String, String> incomingSecrets = collectHttpSecrets(request, authType);
        boolean requireSecrets = creating || !StringUtils.hasText(entity.getSecretCipher());
        if (!creating && !incomingSecrets.isEmpty()) {
            // 合并旧密钥：仅覆盖提交的字段
            Map<String, String> existing = HttpAuthCredentialSupport.decryptSecrets(entity, aesEncryptor);
            existing.putAll(incomingSecrets);
            incomingSecrets = existing;
            requireSecrets = true;
        }
        HttpAuthCredentialSupport.validateConfig(authType, extra, incomingSecrets, requireSecrets && creating);
        if (creating && incomingSecrets.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请填写密钥信息");
        }
        entity.setExtraConfig(jsons.toJson(extra));
        if (!incomingSecrets.isEmpty()) {
            // 确保 validate 所需别名齐全
            if (creating) {
                HttpAuthCredentialSupport.validateConfig(authType, extra, incomingSecrets, true);
            }
            entity.setSecretCipher(HttpAuthCredentialSupport.encryptSecrets(incomingSecrets, aesEncryptor));
        }
    }

    private Map<String, Object> buildHttpAuthExtra(CredentialSaveRequest request, String authType) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("authType", authType);
        putIfText(extra, "username", request.getUsername());
        putIfText(extra, "apiKeyName", request.getApiKeyName());
        putIfText(extra, "apiKeyIn", request.getApiKeyIn());
        putIfText(extra, "tokenUrl", request.getTokenUrl());
        putIfText(extra, "clientId", request.getClientId());
        putIfText(extra, "jwtMode", request.getJwtMode());
        putIfText(extra, "jwtAlg", request.getJwtAlg());
        putIfText(extra, "jwtIssuer", request.getJwtIssuer());
        putIfText(extra, "jwtAudience", request.getJwtAudience());
        putIfText(extra, "jwtSubject", request.getJwtSubject());
        if (request.getJwtTtlSeconds() != null) {
            extra.put("jwtTtlSeconds", request.getJwtTtlSeconds());
        }
        putIfText(extra, "accessKey", request.getAccessKey());
        putIfText(extra, "hmacAlg", request.getHmacAlg());
        putIfText(extra, "signHeader", request.getSignHeader());
        putIfText(extra, "signTemplate", request.getSignTemplate());
        putIfText(extra, "timestampHeader", request.getTimestampHeader());
        putIfText(extra, "nonceHeader", request.getNonceHeader());
        if (request.getIncludeTimestamp() != null) {
            extra.put("includeTimestamp", request.getIncludeTimestamp());
        }
        if (request.getIncludeNonce() != null) {
            extra.put("includeNonce", request.getIncludeNonce());
        }
        return extra;
    }

    private Map<String, String> collectHttpSecrets(CredentialSaveRequest request, String authType) {
        Map<String, String> secrets = new LinkedHashMap<>();
        if (request.getSecrets() != null) {
            request.getSecrets().forEach((k, v) -> {
                if (StringUtils.hasText(k) && StringUtils.hasText(v)) {
                    secrets.put(k, v);
                }
            });
        }
        if (StringUtils.hasText(request.getSecret())) {
            String secret = request.getSecret().trim();
            switch (authType) {
                case HttpAuthCredentialSupport.AUTH_BASIC, HttpAuthCredentialSupport.AUTH_DIGEST ->
                        secrets.putIfAbsent("password", secret);
                case HttpAuthCredentialSupport.AUTH_COOKIE -> secrets.putIfAbsent("cookie", secret);
                case HttpAuthCredentialSupport.AUTH_API_KEY -> secrets.putIfAbsent("apiKeyValue", secret);
                case HttpAuthCredentialSupport.AUTH_OAUTH2_CC -> secrets.putIfAbsent("clientSecret", secret);
                case HttpAuthCredentialSupport.AUTH_AKSK -> secrets.putIfAbsent("secretKey", secret);
                case HttpAuthCredentialSupport.AUTH_JWT -> {
                    String mode = StringUtils.hasText(request.getJwtMode()) ? request.getJwtMode() : "static";
                    if ("sign".equalsIgnoreCase(mode)) {
                        secrets.putIfAbsent("jwtSecret", secret);
                    } else {
                        secrets.putIfAbsent("token", secret);
                    }
                }
                default -> secrets.putIfAbsent("token", secret);
            }
            secrets.putIfAbsent("secret", secret);
        }
        return secrets;
    }

    private boolean hasIncomingSecrets(CredentialSaveRequest request) {
        if (StringUtils.hasText(request.getSecret())) {
            return true;
        }
        if (request.getSecrets() == null || request.getSecrets().isEmpty()) {
            return false;
        }
        return request.getSecrets().values().stream().anyMatch(StringUtils::hasText);
    }

    private String normalizeType(String raw) {
        String type = StringUtils.hasText(raw) ? raw.trim().toUpperCase(Locale.ROOT) : TYPE_WECOM;
        if (TYPE_CUSTOM_LEGACY.equals(type)) {
            return TYPE_HTTP_AUTH;
        }
        if (TYPE_MYSQL_LEGACY.equals(type)) {
            return TYPE_DATABASE;
        }
        return type;
    }

    private Credential require(Long id) {
        Credential entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "凭证不存在");
        }
        return entity;
    }

    private void evict(Long id) {
        TokenManager manager = tokenManager.getIfAvailable();
        if (manager != null) {
            manager.evict(id);
        }
        DatasourcePoolManager pool = datasourcePoolManager.getIfAvailable();
        if (pool != null) {
            pool.evict(id);
        }
        OAuth2ClientCredentialsClient oauth = oauth2Client.getIfAvailable();
        if (oauth != null) {
            oauth.evict(id);
        }
        MtlsHttpClientFactory mtls = mtlsFactory.getIfAvailable();
        if (mtls != null) {
            mtls.evict(id);
        }
    }

    private CredentialVO toVo(Credential entity) {
        CredentialVO vo = new CredentialVO();
        vo.setId(entity.getId());
        vo.setCredentialName(entity.getCredentialName());
        vo.setCredentialType(entity.getCredentialType());
        vo.setScope(entity.getScope());
        vo.setWorkflowId(entity.getWorkflowId());
        vo.setCorpId(entity.getCorpId());
        vo.setAgentId(entity.getAgentId());
        vo.setHasSecret(StringUtils.hasText(entity.getSecretCipher()));
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());

        if (JdbcCredentialSupport.isDatabase(entity)) {
            JdbcCredentialSupport.JdbcEndpoint endpoint = JdbcCredentialSupport.from(entity);
            vo.setDbType(endpoint.dbType());
            vo.setDbHost(endpoint.host());
            vo.setDbPort(endpoint.port());
            vo.setDbName(endpoint.dbName());
            vo.setDbUsername(endpoint.username());
        }
        if (HttpAuthCredentialSupport.isHttpAuth(entity)) {
            Map<String, Object> extra = HttpAuthCredentialSupport.readExtra(entity);
            vo.setAuthType(HttpAuthCredentialSupport.resolveAuthType(entity));
            vo.setUsername(HttpAuthCredentialSupport.stringValue(extra.get("username"), null));
            vo.setApiKeyName(HttpAuthCredentialSupport.stringValue(extra.get("apiKeyName"), null));
            vo.setApiKeyIn(HttpAuthCredentialSupport.stringValue(extra.get("apiKeyIn"), null));
            vo.setTokenUrl(HttpAuthCredentialSupport.stringValue(extra.get("tokenUrl"), null));
            vo.setClientId(HttpAuthCredentialSupport.stringValue(extra.get("clientId"), null));
            vo.setJwtMode(HttpAuthCredentialSupport.stringValue(extra.get("jwtMode"), null));
            vo.setJwtAlg(HttpAuthCredentialSupport.stringValue(extra.get("jwtAlg"), null));
            vo.setJwtIssuer(HttpAuthCredentialSupport.stringValue(extra.get("jwtIssuer"), null));
            vo.setJwtAudience(HttpAuthCredentialSupport.stringValue(extra.get("jwtAudience"), null));
            vo.setJwtSubject(HttpAuthCredentialSupport.stringValue(extra.get("jwtSubject"), null));
            Object ttl = extra.get("jwtTtlSeconds");
            if (ttl instanceof Number n) {
                vo.setJwtTtlSeconds(n.intValue());
            }
            vo.setAccessKey(HttpAuthCredentialSupport.stringValue(extra.get("accessKey"), null));
            vo.setHmacAlg(HttpAuthCredentialSupport.stringValue(extra.get("hmacAlg"), null));
            vo.setSignHeader(HttpAuthCredentialSupport.stringValue(extra.get("signHeader"), null));
            vo.setSignTemplate(HttpAuthCredentialSupport.stringValue(extra.get("signTemplate"), null));
            Object includeTs = extra.get("includeTimestamp");
            if (includeTs instanceof Boolean b) {
                vo.setIncludeTimestamp(b);
            }
            Object includeNonce = extra.get("includeNonce");
            if (includeNonce instanceof Boolean b) {
                vo.setIncludeNonce(b);
            }
            vo.setTimestampHeader(HttpAuthCredentialSupport.stringValue(extra.get("timestampHeader"), null));
            vo.setNonceHeader(HttpAuthCredentialSupport.stringValue(extra.get("nonceHeader"), null));
        }
        return vo;
    }

    private static void putIfText(Map<String, Object> map, String key, String value) {
        if (StringUtils.hasText(value)) {
            map.put(key, value.trim());
        }
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
