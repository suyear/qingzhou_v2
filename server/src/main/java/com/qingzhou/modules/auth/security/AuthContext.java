package com.qingzhou.modules.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class AuthContext {

    private AuthContext() {
    }

    public static Optional<AuthUserPrincipal> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public static String currentUsername() {
        return current().map(AuthUserPrincipal::getUsername).orElse(null);
    }

    public static Long currentUserId() {
        return current().map(AuthUserPrincipal::getId).orElse(null);
    }
}
