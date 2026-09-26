package com.qingzhou;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 上下文冒烟不依赖本机 MySQL/Redis：测试配置用内存库，密钥仅用于测试 classpath。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:qingzhou;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.datasource.hikari.maximum-pool-size=2",
        "qingzhou.crypto.aes-key=qingzhou-aes256-secret-key-00001",
        "qingzhou.auth.jwt-secret=qingzhou-jwt-hmac-secret-key-00001",
        "qingzhou.license.sign-key=qingzhou-license-hmac-secret-key01",
        "qingzhou.security.allow-default-secrets=true",
        "spring.sql.init.mode=never"
})
@ActiveProfiles("test")
class QingzhouApplicationTests {

    @Test
    void contextLoads() {
    }
}
