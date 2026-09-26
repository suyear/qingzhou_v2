package com.qingzhou.modules.execution.engine;

import com.qingzhou.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboundUrlGuardTest {

    @Test
    void rejectsNonHttpScheme() {
        assertThrows(BizException.class, () -> OutboundUrlGuard.validate(URI.create("file:///etc/passwd"), false));
        assertThrows(BizException.class, () -> OutboundUrlGuard.validate(URI.create("ftp://example.com/a"), false));
    }

    @Test
    void rejectsUserInfo() {
        assertThrows(BizException.class,
                () -> OutboundUrlGuard.validate(URI.create("https://user:pass@example.com/x"), true));
    }

    @Test
    void blocksLoopbackWhenPrivateDisallowed() throws UnknownHostException {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        assertTrue(OutboundUrlGuard.isBlockedAddress(loopback, false));
        assertFalse(OutboundUrlGuard.isBlockedAddress(loopback, true));
    }

    @Test
    void blocksSiteLocalWhenPrivateDisallowed() throws UnknownHostException {
        InetAddress site = InetAddress.getByName("10.0.0.1");
        assertTrue(OutboundUrlGuard.isBlockedAddress(site, false));
        assertFalse(OutboundUrlGuard.isBlockedAddress(site, true));
    }

    @Test
    void blocksLinkLocalAlways() throws UnknownHostException {
        InetAddress link = InetAddress.getByName("169.254.169.254");
        assertTrue(OutboundUrlGuard.isBlockedAddress(link, true));
        assertTrue(OutboundUrlGuard.isBlockedAddress(link, false));
    }

    @Test
    void blocksIpv4MappedLoopbackAndLinkLocal() throws UnknownHostException {
        InetAddress mappedLoopback = InetAddress.getByName("::ffff:127.0.0.1");
        assertTrue(OutboundUrlGuard.isBlockedAddress(mappedLoopback, false));
        InetAddress mappedMetadata = InetAddress.getByName("::ffff:169.254.169.254");
        assertTrue(OutboundUrlGuard.isBlockedAddress(mappedMetadata, true));
        assertThrows(BizException.class,
                () -> OutboundUrlGuard.validate(URI.create("http://[::ffff:127.0.0.1]/"), false));
    }

    @Test
    void blocksUniqueLocalIpv6UnlessPrivateAllowed() throws UnknownHostException {
        InetAddress ula = InetAddress.getByName("fd00::1");
        assertTrue(OutboundUrlGuard.isBlockedAddress(ula, false));
        assertFalse(OutboundUrlGuard.isBlockedAddress(ula, true));
    }

    @Test
    void allowsPublicHttpsWhenPrivateDisallowed() {
        assertDoesNotThrow(() -> OutboundUrlGuard.validate(URI.create("https://qyapi.weixin.qq.com/cgi-bin/gettoken"), false));
    }
}
