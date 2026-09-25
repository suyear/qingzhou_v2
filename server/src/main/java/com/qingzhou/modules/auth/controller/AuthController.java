package com.qingzhou.modules.auth.controller;

import com.qingzhou.common.api.R;
import com.qingzhou.modules.auth.dto.AuthSessionVO;
import com.qingzhou.modules.auth.dto.BootstrapRequest;
import com.qingzhou.modules.auth.dto.ChangePasswordRequest;
import com.qingzhou.modules.auth.dto.LoginRequest;
import com.qingzhou.modules.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/bootstrap-status")
    public R<Map<String, Object>> bootstrapStatus() {
        return R.ok(authService.bootstrapStatus());
    }

    @PostMapping("/bootstrap")
    public R<AuthSessionVO> bootstrap(@Valid @RequestBody BootstrapRequest request, HttpServletRequest httpRequest) {
        return R.ok(authService.bootstrap(request, httpRequest));
    }

    @PostMapping("/login")
    public R<AuthSessionVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return R.ok(authService.login(request, httpRequest));
    }

    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest httpRequest) {
        authService.logout(httpRequest);
        return R.ok();
    }

    @GetMapping("/me")
    public R<AuthSessionVO> me() {
        return R.ok(authService.me());
    }

    @PostMapping("/change-password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return R.ok();
    }
}
