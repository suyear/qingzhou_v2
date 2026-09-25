package com.qingzhou.modules.openapi.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpRulesTest {

    @Test
    void emptyWhitelistAllowsAll() {
        assertTrue(IpRules.isAllowed("10.0.0.8", null));
        assertTrue(IpRules.isAllowed("10.0.0.8", "[]"));
        assertTrue(IpRules.isAllowed("10.0.0.8", "  "));
    }

    @Test
    void exactIpAndLocalhostAlias() {
        assertTrue(IpRules.isAllowed("127.0.0.1", "[\"127.0.0.1\"]"));
        assertTrue(IpRules.isAllowed("::1", "127.0.0.1"));
        assertFalse(IpRules.isAllowed("10.0.0.2", "127.0.0.1"));
    }

    @Test
    void cidrMatch() {
        assertTrue(IpRules.isAllowed("10.0.1.20", "10.0.0.0/16"));
        assertFalse(IpRules.isAllowed("10.1.0.1", "10.0.0.0/16"));
    }

    @Test
    void commaSeparatedRules() {
        assertTrue(IpRules.isAllowed("192.168.0.5", "127.0.0.1, 192.168.0.5"));
    }
}
