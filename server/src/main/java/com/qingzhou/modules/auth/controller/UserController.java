package com.qingzhou.modules.auth.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.common.api.R;
import com.qingzhou.modules.auth.dto.UserSaveRequest;
import com.qingzhou.modules.auth.dto.UserVO;
import com.qingzhou.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping
    public R<IPage<UserVO>> page(PageQuery query) {
        return R.ok(authService.pageUsers(query));
    }

    @GetMapping("/roles")
    public R<List<Map<String, String>>> roles() {
        return R.ok(authService.listRoles());
    }

    @PostMapping
    public R<UserVO> create(@Valid @RequestBody UserSaveRequest request) {
        return R.ok(authService.createUser(request));
    }

    @PutMapping("/{id}")
    public R<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        return R.ok(authService.updateUser(id, request));
    }

    @PostMapping("/{id}/disable")
    public R<Void> disable(@PathVariable Long id) {
        authService.disableUser(id);
        return R.ok();
    }
}
