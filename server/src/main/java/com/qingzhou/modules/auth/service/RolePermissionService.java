package com.qingzhou.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingzhou.common.api.ResultCode;
import com.qingzhou.common.exception.BizException;
import com.qingzhou.modules.audit.service.AuditLogService;
import com.qingzhou.modules.auth.dto.PermissionVO;
import com.qingzhou.modules.auth.dto.RolePermissionUpdateRequest;
import com.qingzhou.modules.auth.dto.RoleVO;
import com.qingzhou.modules.auth.entity.SysPermission;
import com.qingzhou.modules.auth.entity.SysRole;
import com.qingzhou.modules.auth.entity.SysRolePermission;
import com.qingzhou.modules.auth.entity.SysUserRole;
import com.qingzhou.modules.auth.mapper.SysPermissionMapper;
import com.qingzhou.modules.auth.mapper.SysRoleMapper;
import com.qingzhou.modules.auth.mapper.SysRolePermissionMapper;
import com.qingzhou.modules.auth.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final AuditLogService auditLogService;

    public List<PermissionVO> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getDeleted, 0)
                        .orderByAsc(SysPermission::getSortOrder))
                .stream()
                .map(p -> PermissionVO.builder()
                        .id(p.getId())
                        .permCode(p.getPermCode())
                        .permName(p.getPermName())
                        .permType(p.getPermType())
                        .parentCode(p.getParentCode())
                        .sortOrder(p.getSortOrder())
                        .build())
                .toList();
    }

    public List<RoleVO> listRoles() {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDeleted, 0)
                .orderByAsc(SysRole::getId));
        return roles.stream().map(role -> {
            long userCount = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getRoleId, role.getId()));
            return RoleVO.builder()
                    .id(role.getId())
                    .roleCode(role.getRoleCode())
                    .roleName(role.getRoleName())
                    .description(role.getDescription())
                    .permissions(permissionMapper.selectPermCodesByRoleId(role.getId()))
                    .userCount(userCount)
                    .build();
        }).toList();
    }

    @Transactional
    public RoleVO updatePermissions(Long roleId, RolePermissionUpdateRequest request) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null || (role.getDeleted() != null && role.getDeleted() == 1)) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        if ("ADMIN".equals(role.getRoleCode())) {
            throw new BizException(ResultCode.BAD_REQUEST, "管理员角色权限固定为全部，不可修改");
        }
        List<String> codes = request.getPermissions() == null ? List.of() : request.getPermissions();
        Map<String, Long> permMap = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(SysPermission::getPermCode, SysPermission::getId, (a, b) -> a));
        for (String code : codes) {
            if (!permMap.containsKey(code)) {
                throw new BizException(ResultCode.BAD_REQUEST, "未知权限: " + code);
            }
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        for (String code : new HashSet<>(codes)) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(permMap.get(code));
            rp.setCreateTime(LocalDateTime.now());
            rolePermissionMapper.insert(rp);
        }
        auditLogService.recordCurrent("ROLE_PERMISSION_UPDATE", "ROLE", String.valueOf(roleId),
                "SUCCESS", "更新角色权限 " + role.getRoleCode());
        return listRoles().stream()
                .filter(r -> r.getId().equals(roleId))
                .findFirst()
                .orElseThrow();
    }
}
