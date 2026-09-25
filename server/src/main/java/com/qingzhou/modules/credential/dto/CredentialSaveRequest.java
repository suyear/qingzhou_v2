package com.qingzhou.modules.credential.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CredentialSaveRequest {

    @NotBlank(message = "凭证名称不能为空")
    @Size(max = 128)
    private String credentialName;

    /** WECOM / HTTP_AUTH / DATABASE（兼容 CUSTOM / MYSQL） */
    private String credentialType;

    /** GLOBAL / WORKFLOW */
    private String scope;

    private Long workflowId;
    private String corpId;
    private String agentId;
    /**
     * 单密钥明文（WECOM Secret / DB 密码 / Bearer Token 等）。
     * 多密钥类型也可只传此字段；优先使用 {@link #secrets}。
     * 更新时留空表示不改。
     */
    private String secret;

    /** 多密钥明文 map（clientSecret / privateKey / apiKeyValue 等），仅写入 */
    private Map<String, String> secrets;

    /** DATABASE：mysql / mariadb / postgresql / sqlserver / oracle */
    private String dbType;
    private String dbHost;
    private Integer dbPort;
    private String dbName;
    private String dbUsername;

    /** HTTP_AUTH 子类型 */
    private String authType;
    private String username;
    private String apiKeyName;
    private String apiKeyIn;
    private String tokenUrl;
    private String clientId;
    private String jwtMode;
    private String jwtAlg;
    private String jwtIssuer;
    private String jwtAudience;
    private Integer jwtTtlSeconds;
    private String jwtSubject;
    private String accessKey;
    private String hmacAlg;
    private String signHeader;
    private String signTemplate;
    private Boolean includeTimestamp;
    private Boolean includeNonce;
    private String timestampHeader;
    private String nonceHeader;

    private String remark;
    private Integer status;
}
