package com.qingzhou.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;

/**
 * 非开发环境禁止使用仓库内置默认密钥启动，避免 JWT/凭证可被已知密钥伪造或解密。
 */
@Slf4j
@Component
public class InsecureDefaultSecretsGuard implements ApplicationRunner {

    private static final Set<String> DEFAULT_SECRETS = Set.of(
            "qingzhou-aes256-secret-key-00001",
            "qingzhou-jwt-hmac-secret-key-00001",
            "qingzhou-license-hmac-secret-key01"
    );

    private final Environment environment;

    @Value("${qingzhou.crypto.aes-key:}")
    private String aesKey;

    @Value("${qingzhou.auth.jwt-secret:}")
    private String jwtSecret;

    @Value("${qingzhou.license.sign-key:}")
    private String licenseSignKey;

    @Value("${qingzhou.security.allow-default-secrets:false}")
    private boolean allowDefaultSecrets;

    public InsecureDefaultSecretsGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean dev = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "dev".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p) || "local".equalsIgnoreCase(p));
        if (dev || allowDefaultSecrets) {
            if (usesDefault(aesKey) || usesDefault(jwtSecret) || usesDefault(licenseSignKey)) {
                log.warn("当前使用内置默认密钥，仅适合本地开发；生产请通过 AES_KEY / JWT_SECRET / LICENSE_SIGN_KEY 注入");
            }
            return;
        }
        if (usesDefault(aesKey) || !StringUtils.hasText(aesKey)) {
            fail("qingzhou.crypto.aes-key / AES_KEY");
        }
        if (usesDefault(jwtSecret) || !StringUtils.hasText(jwtSecret)) {
            fail("qingzhou.auth.jwt-secret / JWT_SECRET");
        }
        if (usesDefault(licenseSignKey) || !StringUtils.hasText(licenseSignKey)) {
            fail("qingzhou.license.sign-key / LICENSE_SIGN_KEY");
        }
    }

    private static boolean usesDefault(String value) {
        return StringUtils.hasText(value) && DEFAULT_SECRETS.contains(value.trim());
    }

    private static void fail(String name) {
        throw new IllegalStateException(
                "检测到不安全的默认密钥配置: " + name
                        + "。请通过环境变量覆盖，或仅在开发环境使用 spring.profiles.active=dev。"
                        + "紧急排查可设 qingzhou.security.allow-default-secrets=true（不推荐）。");
    }
}
