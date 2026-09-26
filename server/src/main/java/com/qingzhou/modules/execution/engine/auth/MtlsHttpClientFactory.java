package com.qingzhou.modules.execution.engine.auth;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.crypto.AesEncryptor;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.credential.entity.Credential;
import com.qingzhou.modules.credential.service.CredentialService;
import com.qingzhou.modules.credential.support.HttpAuthCredentialSupport;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

@Slf4j
@Component
@RequiredArgsConstructor
public class MtlsHttpClientFactory {

    private final ConcurrentHashMap<Long, HttpClient> clients = new ConcurrentHashMap<>();
    private final CredentialService credentialService;
    private final AesEncryptor aesEncryptor;

    public HttpClient getClient(Long credentialId) {
        if (credentialId == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "未指定 mTLS 凭证");
        }
        return clients.computeIfAbsent(credentialId, this::createClient);
    }

    public void evict(Long credentialId) {
        if (credentialId != null) {
            clients.remove(credentialId);
        }
    }

    public void validatePem(Credential credential) {
        Map<String, String> secrets = HttpAuthCredentialSupport.decryptSecrets(credential, aesEncryptor);
        String certPem = HttpAuthCredentialSupport.secretOr(secrets, "clientCert", "cert");
        String keyPem = HttpAuthCredentialSupport.secretOr(secrets, "privateKey", "key");
        if (!StringUtils.hasText(certPem) || !StringUtils.hasText(keyPem)) {
            throw new BizException(ResultCode.BAD_REQUEST, "mTLS 凭证缺少证书或私钥");
        }
        try {
            parseCertificates(certPem);
            parsePrivateKey(keyPem);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ResultCode.BAD_REQUEST, "mTLS 证书/私钥无法解析: " + ex.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        clients.clear();
    }

    private HttpClient createClient(Long credentialId) {
        Credential credential = credentialService.getById(credentialId);
        if (credential == null) {
            throw new BizException(ResultCode.NOT_FOUND, "mTLS 凭证不存在");
        }
        Map<String, String> secrets = HttpAuthCredentialSupport.decryptSecrets(credential, aesEncryptor);
        String certPem = HttpAuthCredentialSupport.secretOr(secrets, "clientCert", "cert");
        String keyPem = HttpAuthCredentialSupport.secretOr(secrets, "privateKey", "key");
        try {
            X509Certificate[] chain = parseCertificates(certPem);
            PrivateKey privateKey = parsePrivateKey(keyPem);
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setKeyEntry("client", privateKey, new char[0], chain);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, new char[0]);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(5))
                    // 禁止自动跟随重定向，避免 30x 跳到内网绕过 SSRF 校验
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ResultCode.BAD_REQUEST, "创建 mTLS HttpClient 失败: " + ex.getMessage());
        }
    }

    private X509Certificate[] parseCertificates(String pem) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certs = factory.generateCertificates(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
        List<X509Certificate> list = new ArrayList<>();
        for (Certificate cert : certs) {
            list.add((X509Certificate) cert);
        }
        if (list.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "未解析到客户端证书");
        }
        return list.toArray(X509Certificate[]::new);
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }
}
