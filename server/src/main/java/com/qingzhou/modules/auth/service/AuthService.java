package com.qingzhou.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.audit.service.AuditLogService;
import com.qingzhou.modules.auth.dto.AuthSessionVO;
import com.qingzhou.modules.auth.dto.BootstrapRequest;
import com.qingzhou.modules.auth.dto.ChangePasswordRequest;
import com.qingzhou.modules.auth.dto.LoginRequest;
import com.qingzhou.modules.auth.dto.UserSaveRequest;
import com.qingzhou.modules.auth.dto.UserVO;
import com.qingzhou.modules.auth.entity.SysRole;
import com.qingzhou.modules.auth.entity.SysUser;
import com.qingzhou.modules.auth.entity.SysUserRole;
import com.qingzhou.modules.auth.mapper.SysPermissionMapper;
import com.qingzhou.modules.auth.mapper.SysRoleMapper;
import com.qingzhou.modules.auth.mapper.SysUserMapper;
import com.qingzhou.modules.auth.mapper.SysUserRoleMapper;
import com.qingzhou.modules.auth.security.AuthContext;
import com.qingzhou.modules.auth.security.AuthUserPrincipal;
import com.qingzhou.modules.auth.security.JwtTokenService;
import com.qingzhou.modules.license.service.LicenseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysPermissionMapper permissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final LicenseService licenseService;
    private final AuditLogService auditLogService;

    public Map<String, Object> bootstrapStatus() {
        long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeleted, 0));
        return Map.of("bootstrapped", count > 0, "userCount", count);
    }

    @Transactional
    public AuthSessionVO bootstrap(BootstrapRequest request, HttpServletRequest httpRequest) {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeleted, 0)) > 0) {
            throw new BizException(ResultCode.CONFLICT, "已完成初始化，请直接登录");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername().trim());
        user.setDisplayName(request.getDisplayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1);
        user.setMustChangePassword(0);
        user.setCreateBy(user.getUsername());
        userMapper.insert(user);
        assignRoles(user.getId(), List.of("ADMIN"));
        auditLogService.record(user.getId(), user.getUsername(), "USER_BOOTSTRAP", "USER",
                String.valueOf(user.getId()), "SUCCESS", "首次创建管理员", clientIp(httpRequest));
        return issueSession(loadPrincipal(user), httpRequest);
    }

    public AuthSessionVO login(LoginRequest request, HttpServletRequest httpRequest) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername().trim())
                .eq(SysUser::getDeleted, 0)
                .last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            auditLogService.record(null, request.getUsername(), "USER_LOGIN", "USER", null,
                    "FAIL", "用户名或密码错误", clientIp(httpRequest));
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已停用");
        }
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(clientIp(httpRequest));
        userMapper.updateById(user);
        AuthUserPrincipal principal = loadPrincipal(user);
        auditLogService.record(user.getId(), user.getUsername(), "USER_LOGIN", "USER",
                String.valueOf(user.getId()), "SUCCESS", "登录成功", clientIp(httpRequest));
        return issueSession(principal, httpRequest);
    }

    public void logout(HttpServletRequest httpRequest) {
        AuthContext.current().ifPresent(p ->
                auditLogService.record(p.getId(), p.getUsername(), "USER_LOGOUT", "USER",
                        String.valueOf(p.getId()), "SUCCESS", "退出登录", clientIp(httpRequest)));
    }

    public AuthSessionVO me() {
        AuthUserPrincipal principal = AuthContext.current()
                .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED, "请先登录"));
        SysUser user = userMapper.selectById(principal.getId());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        AuthUserPrincipal fresh = loadPrincipal(user);
        return AuthSessionVO.builder()
                .userId(fresh.getId())
                .username(fresh.getUsername())
                .displayName(fresh.getDisplayName())
                .roles(fresh.getRoles())
                .permissions(fresh.getPermissions())
                .mustChangePassword(fresh.isMustChangePassword())
                .expiresInSeconds(jwtTokenService.ttlSeconds())
                .build();
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        AuthUserPrincipal principal = AuthContext.current()
                .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED, "请先登录"));
        SysUser user = userMapper.selectById(principal.getId());
        if (user == null || !passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.BAD_REQUEST, "当前密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(0);
        user.setUpdateBy(principal.getUsername());
        userMapper.updateById(user);
        auditLogService.record(principal.getId(), principal.getUsername(), "USER_CHANGE_PASSWORD",
                "USER", String.valueOf(user.getId()), "SUCCESS", "修改密码", null);
    }

    public IPage<UserVO> pageUsers(PageQuery query) {
        Page<SysUser> page = userMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()),
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getDeleted, 0)
                        .like(StringUtils.hasText(query.getKeyword()), SysUser::getUsername, query.getKeyword())
                        .orderByDesc(SysUser::getId));
        return page.convert(this::toVo);
    }

    @Transactional
    public UserVO createUser(UserSaveRequest request) {
        licenseService.assertCanCreateUser(userMapper.countActiveUsers());
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername().trim())
                .eq(SysUser::getDeleted, 0)) > 0) {
            throw new BizException(ResultCode.CONFLICT, "用户名已存在");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "请设置初始密码");
        }
        validateRoles(request.getRoles());
        SysUser user = new SysUser();
        user.setUsername(request.getUsername().trim());
        user.setDisplayName(request.getDisplayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        user.setMustChangePassword(1);
        user.setCreateBy(AuthContext.currentUsername());
        userMapper.insert(user);
        assignRoles(user.getId(), request.getRoles());
        auditLogService.recordCurrent("USER_CREATE", "USER", String.valueOf(user.getId()),
                "SUCCESS", "创建用户 " + user.getUsername());
        return toVo(user);
    }

    @Transactional
    public UserVO updateUser(Long id, UserSaveRequest request) {
        SysUser user = requireUser(id);
        validateRoles(request.getRoles());
        user.setDisplayName(request.getDisplayName().trim());
        user.setStatus(request.getStatus() == null ? user.getStatus() : request.getStatus());
        if (StringUtils.hasText(request.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setMustChangePassword(1);
        }
        user.setUpdateBy(AuthContext.currentUsername());
        userMapper.updateById(user);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        assignRoles(id, request.getRoles());
        auditLogService.recordCurrent("USER_UPDATE", "USER", String.valueOf(id),
                "SUCCESS", "更新用户 " + user.getUsername());
        return toVo(user);
    }

    @Transactional
    public void disableUser(Long id) {
        SysUser user = requireUser(id);
        if (AuthContext.currentUserId() != null && AuthContext.currentUserId().equals(id)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能停用当前登录账号");
        }
        user.setStatus(0);
        user.setUpdateBy(AuthContext.currentUsername());
        userMapper.updateById(user);
        auditLogService.recordCurrent("USER_DISABLE", "USER", String.valueOf(id),
                "SUCCESS", "停用用户 " + user.getUsername());
    }

    public List<Map<String, String>> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().eq(SysRole::getDeleted, 0)
                        .orderByAsc(SysRole::getId))
                .stream()
                .map(r -> Map.of("code", r.getRoleCode(), "name", r.getRoleName(),
                        "description", r.getDescription() == null ? "" : r.getDescription()))
                .toList();
    }

    private AuthSessionVO issueSession(AuthUserPrincipal principal, HttpServletRequest request) {
        String token = jwtTokenService.issue(principal);
        return AuthSessionVO.builder()
                .token(token)
                .userId(principal.getId())
                .username(principal.getUsername())
                .displayName(principal.getDisplayName())
                .roles(principal.getRoles())
                .permissions(principal.getPermissions())
                .mustChangePassword(principal.isMustChangePassword())
                .expiresInSeconds(jwtTokenService.ttlSeconds())
                .build();
    }

    private AuthUserPrincipal loadPrincipal(SysUser user) {
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = permissionMapper.selectPermCodesByUserId(user.getId());
        if (roles.contains("ADMIN") && (permissions == null || permissions.isEmpty())) {
            // V7 未执行时兜底：管理员视为全权限
            permissions = List.of(
                    "menu:workbench", "menu:components", "menu:workflows", "menu:credentials",
                    "menu:schedules", "menu:executions", "menu:openapi",
                    "menu:users", "menu:roles", "menu:license", "menu:audit", "menu:settings",
                    "component:write", "credential:write", "workflow:write", "schedule:write",
                    "openapi:write", "system:admin");
        }
        return new AuthUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPasswordHash(),
                user.getStatus() != null && user.getStatus() == 1,
                user.getMustChangePassword() != null && user.getMustChangePassword() == 1,
                roles,
                permissions == null ? List.of() : permissions);
    }

    private void assignRoles(Long userId, List<String> roleCodes) {
        Map<String, Long> roleMap = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(SysRole::getRoleCode, SysRole::getId, (a, b) -> a));
        for (String code : new HashSet<>(roleCodes)) {
            Long roleId = roleMap.get(code);
            if (roleId == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "未知角色: " + code);
            }
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            ur.setCreateTime(LocalDateTime.now());
            userRoleMapper.insert(ur);
        }
    }

    private void validateRoles(List<String> roles) {
        Set<String> allowed = Set.of("ADMIN", "DEVELOPER", "VIEWER");
        for (String role : roles) {
            if (!allowed.contains(role)) {
                throw new BizException(ResultCode.BAD_REQUEST, "不支持的角色: " + role);
            }
        }
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private UserVO toVo(SysUser user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .status(user.getStatus())
                .mustChangePassword(user.getMustChangePassword())
                .roles(userMapper.selectRoleCodesByUserId(user.getId()))
                .lastLoginAt(user.getLastLoginAt())
                .createTime(user.getCreateTime())
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
