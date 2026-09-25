package com.qingzhou.modules.auth.controller;

import com.qingzhou.common.api.R;
import com.qingzhou.modules.auth.dto.PermissionVO;
import com.qingzhou.modules.auth.dto.RolePermissionUpdateRequest;
import com.qingzhou.modules.auth.dto.RoleVO;
import com.qingzhou.modules.auth.service.RolePermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RolePermissionService rolePermissionService;

    @GetMapping
    public R<List<RoleVO>> list() {
        return R.ok(rolePermissionService.listRoles());
    }

    @GetMapping("/permissions")
    public R<List<PermissionVO>> permissions() {
        return R.ok(rolePermissionService.listPermissions());
    }

    @PutMapping("/{id}/permissions")
    public R<RoleVO> updatePermissions(@PathVariable Long id, @Valid @RequestBody RolePermissionUpdateRequest request) {
        return R.ok(rolePermissionService.updatePermissions(id, request));
    }
}
