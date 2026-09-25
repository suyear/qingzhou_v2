package com.qingzhou.modules.license.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class LicenseStatusVO {

    /** NONE / ACTIVE / EXPIRED / INVALID */
    private String status;
    private String issuer;
    private String customer;
    private LocalDate expiresAt;
    private Integer seats;
    private long usedSeats;
    private boolean writeBlocked;
    private String message;
    private String featuresJson;
}
