package com.qingzhou.modules.license.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.audit.service.AuditLogService;
import com.qingzhou.modules.auth.mapper.SysUserMapper;
import com.qingzhou.modules.auth.security.AuthContext;
import com.qingzhou.modules.license.dto.LicenseImportRequest;
import com.qingzhou.modules.license.dto.LicenseStatusVO;
import com.qingzhou.modules.license.entity.ProductLicense;
import com.qingzhou.modules.license.mapper.ProductLicenseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class LicenseService {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final ProductLicenseMapper licenseMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    @Value("${qingzhou.license.sign-key}")
    private String signKey;

    public boolean isWriteBlocked() {
        ProductLicense latest = latest();
        if (latest == null) {
            return false;
        }
        refreshStatus(latest);
        return !"ACTIVE".equals(latest.getStatus());
    }

    public void assertCanCreateUser(long currentActiveUsers) {
        ProductLicense latest = latest();
        if (latest == null) {
            return;
        }
        refreshStatus(latest);
        if (!"ACTIVE".equals(latest.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "License 无效或已过期，无法新建用户");
        }
        int seats = latest.getSeats() == null ? 5 : latest.getSeats();
        if (currentActiveUsers >= seats) {
            throw new BizException(ResultCode.FORBIDDEN, "已达 License 席位上限（" + seats + "），请升级 License");
        }
    }

    public LicenseStatusVO status() {
        ProductLicense latest = latest();
        long used = userMapper.countActiveUsers();
        if (latest == null) {
            return LicenseStatusVO.builder()
                    .status("NONE")
                    .usedSeats(used)
                    .writeBlocked(false)
                    .message("未导入 License，当前为开发模式（不限制席位）")
                    .build();
        }
        refreshStatus(latest);
        boolean blocked = !"ACTIVE".equals(latest.getStatus());
        return LicenseStatusVO.builder()
                .status(latest.getStatus())
                .issuer(latest.getIssuer())
                .customer(latest.getCustomer())
                .expiresAt(latest.getExpiresAt())
                .seats(latest.getSeats())
                .usedSeats(used)
                .writeBlocked(blocked)
                .featuresJson(latest.getFeaturesJson())
                .message(switch (latest.getStatus()) {
                    case "ACTIVE" -> "License 有效";
                    case "EXPIRED" -> "License 已过期，系统只读";
                    default -> "License 无效，系统只读";
                })
                .build();
    }

    @Transactional
    public LicenseStatusVO importLicense(LicenseImportRequest request) {
        String text = request.getLicenseText().trim().replaceAll("\\s+", "");
        String[] parts = text.split("\\.");
        if (parts.length != 2) {
            throw new BizException(ResultCode.BAD_REQUEST, "License 格式错误，应为 payload.signature");
        }
        String payloadJson;
        try {
            payloadJson = new String(B64D.decode(parts[0]), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BizException(ResultCode.BAD_REQUEST, "License payload 无法解析");
        }
        String expected = sign(parts[0]);
        if (!constantTimeEquals(expected, parts[1])) {
            throw new BizException(ResultCode.BAD_REQUEST, "License 签名校验失败");
        }
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            ProductLicense license = new ProductLicense();
            license.setLicensePayload(payloadJson);
            license.setSignature(parts[1]);
            license.setIssuer(textOrNull(node, "issuer"));
            license.setCustomer(textOrNull(node, "customer"));
            if (node.hasNonNull("expiresAt")) {
                license.setExpiresAt(LocalDate.parse(node.get("expiresAt").asText()));
            }
            license.setSeats(node.has("seats") ? node.get("seats").asInt(5) : 5);
            if (node.has("features")) {
                license.setFeaturesJson(objectMapper.writeValueAsString(node.get("features")));
            }
            license.setImportedAt(LocalDateTime.now());
            license.setImportedBy(AuthContext.currentUsername());
            refreshStatus(license);
            licenseMapper.insert(license);
            auditLogService.recordCurrent("LICENSE_IMPORT", "LICENSE", String.valueOf(license.getId()),
                    "SUCCESS", "导入 License: " + license.getCustomer());
            return status();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ResultCode.BAD_REQUEST, "License 内容无效: " + ex.getMessage());
        }
    }

    /** 生成演示 License（管理端调试用，也可 CLI） */
    public String generateDemoLicense(String customer, LocalDate expiresAt, int seats) {
        try {
            var payload = objectMapper.createObjectNode();
            payload.put("issuer", "Qingzhou");
            payload.put("customer", customer);
            payload.put("expiresAt", expiresAt.toString());
            payload.put("seats", seats);
            payload.putObject("features").put("sso", false);
            String encoded = B64.encodeToString(objectMapper.writeValueAsBytes(payload));
            return encoded + "." + sign(encoded);
        } catch (Exception ex) {
            throw new BizException(ResultCode.SERVER_ERROR, "生成 License 失败");
        }
    }

    private ProductLicense latest() {
        return licenseMapper.selectOne(new LambdaQueryWrapper<ProductLicense>()
                .orderByDesc(ProductLicense::getId)
                .last("LIMIT 1"));
    }

    private void refreshStatus(ProductLicense license) {
        if (license.getExpiresAt() != null && license.getExpiresAt().isBefore(LocalDate.now())) {
            license.setStatus("EXPIRED");
        } else if (!StringUtils.hasText(license.getSignature())) {
            license.setStatus("INVALID");
        } else {
            license.setStatus("ACTIVE");
        }
        if (license.getId() != null) {
            licenseMapper.updateById(license);
        }
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
