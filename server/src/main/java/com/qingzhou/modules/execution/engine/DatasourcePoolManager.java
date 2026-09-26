package com.qingzhou.modules.execution.engine;

import com.qingzhou.common.crypto.AesEncryptor;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.credential.entity.Credential;
import com.qingzhou.modules.credential.service.CredentialService;
import com.qingzhou.modules.credential.support.JdbcCredentialSupport;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourcePoolManager {

    private final ConcurrentHashMap<Long, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final CredentialService credentialService;
    private final AesEncryptor aesEncryptor;

    public Connection getConnection(Long credentialId) throws SQLException {
        Credential credential = requireDatabase(credentialId);
        HikariDataSource pool = pools.computeIfAbsent(credential.getId(), id -> createPool(credential));
        try {
            return pool.getConnection();
        } catch (SQLException ex) {
            // 池耗尽超时不是坏连接，拆掉池会放大故障
            if (isPoolTimeout(ex)) {
                throw ex;
            }
            evict(credentialId);
            HikariDataSource retry = pools.computeIfAbsent(credential.getId(), id -> createPool(requireDatabase(credentialId)));
            return retry.getConnection();
        }
    }

    public void evict(Long credentialId) {
        if (credentialId == null) {
            return;
        }
        HikariDataSource pool = pools.remove(credentialId);
        if (pool != null) {
            try {
                pool.close();
            } catch (Exception ex) {
                log.warn("关闭数据源连接池失败 id={} msg={}", credentialId, ex.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        for (Long id : pools.keySet()) {
            evict(id);
        }
    }

    private HikariDataSource createPool(Credential credential) {
        JdbcCredentialSupport.JdbcEndpoint endpoint = JdbcCredentialSupport.from(credential);
        JdbcCredentialSupport.validate(endpoint.dbType(), endpoint.host(), endpoint.port(),
                endpoint.dbName(), endpoint.username());
        String password = aesEncryptor.decrypt(credential.getSecretCipher());
        if (!StringUtils.hasText(password)) {
            throw new BizException(ResultCode.BAD_REQUEST, "数据源未配置密码");
        }
        // DATABASE 单密钥仍是纯密码字符串；若被写成 secrets JSON，取 password 字段
        if (password.trim().startsWith("{")) {
            var secrets = com.qingzhou.modules.credential.support.HttpAuthCredentialSupport
                    .decryptSecrets(credential, aesEncryptor);
            password = com.qingzhou.modules.credential.support.HttpAuthCredentialSupport
                    .secretOr(secrets, "password", "secret");
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("qz-ds-" + credential.getId());
        config.setJdbcUrl(JdbcCredentialSupport.jdbcUrl(endpoint));
        config.setUsername(endpoint.username());
        config.setPassword(password);
        config.setDriverClassName(JdbcCredentialSupport.driverClassName(endpoint.dbType()));
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(8_000);
        config.setValidationTimeout(3_000);
        config.setIdleTimeout(60_000);
        config.setMaxLifetime(300_000);
        config.setConnectionTestQuery(JdbcCredentialSupport.validationQuery(endpoint.dbType()));
        config.setAutoCommit(true);
        return new HikariDataSource(config);
    }

    private static boolean isPoolTimeout(SQLException ex) {
        if (ex instanceof java.sql.SQLTimeoutException) {
            return true;
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(java.util.Locale.ROOT);
        return message.contains("connection is not available")
                || message.contains("timed out")
                || message.contains("timeout");
    }

    private Credential requireDatabase(Long credentialId) {
        if (credentialId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "未指定数据源");
        }
        Credential credential = credentialService.getById(credentialId);
        if (credential == null) {
            throw new BizException(ResultCode.NOT_FOUND, "数据源不存在");
        }
        if (!JdbcCredentialSupport.isDatabase(credential)) {
            throw new BizException(ResultCode.BAD_REQUEST, "该凭证不是数据库数据源");
        }
        if (credential.getStatus() != null && credential.getStatus() == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "数据源已停用");
        }
        return credential;
    }
}
