package com.qingzhou.modules.execution.engine;

import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * 出站 HTTP URL 安全校验：仅 http(s)、解析全部 A/AAAA、默认拒绝私网与链路本地（防 SSRF）。
 */
public final class OutboundUrlGuard {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "metadata.google.internal",
            "metadata.google",
            "instance-data"
    );

    private OutboundUrlGuard() {
    }

    public static void validate(URI uri, boolean allowPrivateNetwork) {
        if (uri == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "出站 URL 为空");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BizException(ResultCode.BAD_REQUEST, "出站 URL 仅允许 http/https");
        }
        if (StringUtils.hasText(uri.getRawUserInfo())) {
            throw new BizException(ResultCode.BAD_REQUEST, "出站 URL 不允许包含用户信息");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new BizException(ResultCode.BAD_REQUEST, "出站 URL 缺少主机名");
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(hostLower)
                || hostLower.endsWith(".internal")
                || hostLower.endsWith(".localhost")
                || "localhost".equals(hostLower)) {
            if (!allowPrivateNetwork || BLOCKED_HOSTS.contains(hostLower) || hostLower.endsWith(".internal")) {
                throw new BizException(ResultCode.BAD_REQUEST, "出站目标主机不允许访问");
            }
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new BizException(ResultCode.BAD_REQUEST, "出站主机无法解析: " + host);
        }
        if (addresses == null || addresses.length == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "出站主机无法解析: " + host);
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address, allowPrivateNetwork)) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "出站目标地址不允许访问（私网/本机/链路本地已拦截）: " + address.getHostAddress());
            }
        }
    }

    static boolean isBlockedAddress(InetAddress address, boolean allowPrivateNetwork) {
        if (address == null) {
            return true;
        }
        InetAddress mapped = unwrapV4Mapped(address);
        if (mapped != address) {
            return isBlockedAddress(mapped, allowPrivateNetwork);
        }
        if (address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address inet6 && isUniqueLocal(inet6)) {
            return !allowPrivateNetwork;
        }
        if (address.isLinkLocalAddress()) {
            return true;
        }
        if (address.isLoopbackAddress() || address.isSiteLocalAddress()) {
            return !allowPrivateNetwork;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            // 100.64.0.0/10 Carrier-grade NAT
            if (b0 == 100 && b1 >= 64 && b1 <= 127) {
                return !allowPrivateNetwork;
            }
            // 169.254.0.0/16 already covered by link-local for most stacks; keep explicit
            if (b0 == 169 && b1 == 254) {
                return true;
            }
            // 0.0.0.0/8
            if (b0 == 0) {
                return true;
            }
        }
        return false;
    }

    /** ::ffff:x.x.x.x 在 JDK 里不算 loopback / site-local，必须先拆成 IPv4 再判断。 */
    static InetAddress unwrapV4Mapped(InetAddress address) {
        if (!(address instanceof Inet6Address inet6)) {
            return address;
        }
        byte[] bytes = inet6.getAddress();
        if (bytes.length != 16) {
            return address;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return address;
            }
        }
        if ((bytes[10] & 0xFF) != 0xFF || (bytes[11] & 0xFF) != 0xFF) {
            return address;
        }
        try {
            return InetAddress.getByAddress(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]});
        } catch (UnknownHostException ex) {
            return address;
        }
    }

    /** fc00::/7 unique local。Java 的 isSiteLocalAddress 只覆盖已废弃的 fec0::/10。 */
    private static boolean isUniqueLocal(Inet6Address address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
