package com.qingzhou.modules.auth.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class AuthUserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final List<String> roles;
    private final List<String> permissions;

    public AuthUserPrincipal(Long id, String username, String displayName, String passwordHash,
                             boolean enabled, boolean mustChangePassword, List<String> roles,
                             List<String> permissions) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.mustChangePassword = mustChangePassword;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> list = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        for (String perm : permissions) {
            list.add(new SimpleGrantedAuthority(perm));
        }
        return list;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean canWrite() {
        return hasRole("ADMIN") || hasRole("DEVELOPER");
    }
}
