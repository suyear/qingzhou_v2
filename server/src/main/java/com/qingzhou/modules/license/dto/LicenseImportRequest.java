package com.qingzhou.modules.license.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LicenseImportRequest {

    /** 完整 License 文本：base64url(payload).signature */
    @NotBlank(message = "请粘贴 License 内容")
    private String licenseText;
}
