package com.qingzhou.modules.openapi.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAPI IP 白名单。支持单 IP 与 IPv4 CIDR；空名单表示不限制。
 */
public final class IpRules {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IpRules() {
    }

    public static boolean isAllowed(String clientIp, String whitelistRaw) {
        List<String> rules = parse(whitelistRaw);
        if (rules.isEmpty()) {
            return true;
        }
        String ip = normalize(clientIp);
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        for (String rule : rules) {
            if (matches(ip, rule)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> parse(String raw) {
        List<String> rules = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return rules;
        }
        String text = raw.trim();
        if (text.startsWith("[")) {
            try {
                List<String> parsed = MAPPER.readValue(text, new TypeReference<>() {
                });
                if (parsed != null) {
                    parsed.stream().filter(StringUtils::hasText).map(String::trim).forEach(rules::add);
                    return rules;
                }
            } catch (Exception ignored) {
                // fall through to comma-separated
            }
        }
        for (String part : text.split("[,\\n]")) {
            if (StringUtils.hasText(part)) {
                rules.add(part.trim());
            }
        }
        return rules;
    }

    public static String normalize(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "";
        }
        String value = ip.trim();
        if ("::1".equals(value) || "https://example.net/id/garnet".equals(value)) {
            return "127.0.0.1";
        }
        if (value.startsWith("::ffff:")) {
            return value.substring("::ffff:".length());
        }
        return value;
    }

    static boolean matches(String ip, String rule) {
        String normalizedRule = normalize(rule);
        if (ip.equals(normalizedRule)) {
            return true;
        }
        int slash = normalizedRule.indexOf('/');
        if (slash < 0) {
            return false;
        }
        return matchCidr(ip, normalizedRule.substring(0, slash), normalizedRule.substring(slash + 1));
    }

    private static boolean matchCidr(String ip, String network, String prefixText) {
        long address = ipv4(ip);
        long net = ipv4(network);
        if (address < 0 || net < 0) {
            return false;
        }
        int prefix;
        try {
            prefix = Integer.parseInt(prefixText);
        } catch (NumberFormatException ex) {
            return false;
        }
        if (prefix < 0 || prefix > 32) {
            return false;
        }
        long mask = prefix == 0 ? 0 : 0xFFFFFFFFL << (32 - prefix);
        return (address & mask) == (net & mask);
    }

    private static long ipv4(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return -1;
        }
        long value = 0;
        for (String part : parts) {
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException ex) {
                return -1;
            }
            if (octet < 0 || octet > 255) {
                return -1;
            }
            value = (value << 8) | octet;
        }
        return value;
    }
}
