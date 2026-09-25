package com.qingzhou.modules.license.controller;

import com.qingzhou.common.api.R;
import com.qingzhou.modules.license.dto.LicenseImportRequest;
import com.qingzhou.modules.license.dto.LicenseStatusVO;
import com.qingzhou.modules.license.service.LicenseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/license")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;

    @GetMapping("/status")
    public R<LicenseStatusVO> status() {
        return R.ok(licenseService.status());
    }

    @PostMapping("/import")
    public R<LicenseStatusVO> importLicense(@Valid @RequestBody LicenseImportRequest request) {
        return R.ok(licenseService.importLicense(request));
    }

    /** 本地签发演示 License（生产由厂商离线签发） */
    @PostMapping("/generate-demo")
    public R<Map<String, String>> generateDemo(@Valid @RequestBody DemoLicenseRequest request) {
        String text = licenseService.generateDemoLicense(
                request.getCustomer(),
                request.getExpiresAt() == null ? LocalDate.now().plusYears(1) : request.getExpiresAt(),
                request.getSeats() == null ? 10 : request.getSeats());
        return R.ok(Map.of("licenseText", text));
    }

    @Data
    public static class DemoLicenseRequest {
        @NotBlank
        private String customer;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate expiresAt;
        @Min(1)
        @Max(10000)
        private Integer seats;
    }
}
