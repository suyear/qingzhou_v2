package com.qingzhou.modules.credential.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CredentialVO {

    private Long id;
    private String credentialName;
    private String credentialType;
    private String scope;
    private Long workflowId;
    private String corpId;
    private String agentId;
    private boolean hasSecret;

    /** DATABASE */
    private String dbType;
    private String dbHost;
    private Integer dbPort;
    private String dbName;
    private String dbUsername;

    /** HTTP_AUTH 非敏感配置 */
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

    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
