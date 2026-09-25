package com.qingzhou.infra.wecom;

import com.qingzhou.common.crypto.AesEncryptor;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.infra.redis.RedisOps;
import com.qingzhou.modules.credential.entity.Credential;
import com.qingzhou.modules.credential.service.CredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenManagerTest {

    @Mock
    private RedisOps redisOps;
    @Mock
    private CredentialService credentialService;
    @Mock
    private AesEncryptor aesEncryptor;
    @Mock
    private WecomTokenClient wecomTokenClient;

    private TokenManager tokenManager;
    private Credential credential;

    @BeforeEach
    void setUp() {
        tokenManager = new TokenManager(
                redisOps, credentialService, aesEncryptor, wecomTokenClient,
                300, 10, 2, 10);
        credential = new Credential();
        credential.setId(8L);
        credential.setCredentialType("WECOM");
        credential.setStatus(1);
        credential.setCorpId("ww-corp");
        credential.setSecretCipher("cipher");
    }

    @Test
    void returnCachedTokenWithoutCallingWecom() {
        when(credentialService.getById(8L)).thenReturn(credential);
        when(redisOps.get("qz:wecom:token:8")).thenReturn("cached-token");

        String token = tokenManager.getAccessToken(8L);

        assertEquals("cached-token", token);
        verify(wecomTokenClient, never()).fetch(anyString(), anyString());
    }

    @Test
    void refreshWhenCacheMissAndLockAcquired() {
        when(credentialService.getById(8L)).thenReturn(credential);
        when(redisOps.get("qz:wecom:token:8")).thenReturn(null);
        when(redisOps.tryLock(anyString(), anyString(), any(Duration.class), anyInt(), any(Duration.class)))
                .thenReturn(true);
        when(aesEncryptor.decrypt("cipher")).thenReturn("plain-secret");
        when(wecomTokenClient.fetch("ww-corp", "plain-secret"))
                .thenReturn(new WecomTokenResponse(0, "ok", "fresh-token", 7200));

        String token = tokenManager.getAccessToken(8L);

        assertEquals("fresh-token", token);
        verify(redisOps).set(eq("qz:wecom:token:8"), eq("fresh-token"), eq(Duration.ofSeconds(6900)));
        verify(redisOps).unlock(eq("qz:wecom:token:lock:8"), anyString());
    }

    @Test
    void wecomBusinessErrorIsPropagated() {
        when(credentialService.getById(8L)).thenReturn(credential);
        when(redisOps.get("qz:wecom:token:8")).thenReturn(null);
        when(redisOps.tryLock(anyString(), anyString(), any(Duration.class), anyInt(), any(Duration.class)))
                .thenReturn(true);
        when(aesEncryptor.decrypt("cipher")).thenReturn("plain-secret");
        when(wecomTokenClient.fetch("ww-corp", "plain-secret"))
                .thenReturn(new WecomTokenResponse(40013, "invalid corpid", null, null));

        assertThrows(BizException.class, () -> tokenManager.getAccessToken(8L));
        verify(redisOps).unlock(eq("qz:wecom:token:lock:8"), anyString());
    }
}
