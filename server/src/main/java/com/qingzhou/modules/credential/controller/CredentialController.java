package com.qingzhou.modules.credential.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.common.api.R;
import com.qingzhou.modules.credential.dto.CredentialSaveRequest;
import com.qingzhou.modules.credential.dto.CredentialTestVO;
import com.qingzhou.modules.credential.dto.CredentialVO;
import com.qingzhou.modules.credential.service.CredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
public class CredentialController {

    private final CredentialService credentialService;

    @GetMapping
    public R<IPage<CredentialVO>> page(PageQuery query) {
        return R.ok(credentialService.pageVo(query));
    }

    @GetMapping("/{id}")
    public R<CredentialVO> detail(@PathVariable Long id) {
        return R.ok(credentialService.detail(id));
    }

    @PostMapping
    public R<CredentialVO> create(@Valid @RequestBody CredentialSaveRequest request) {
        return R.ok(credentialService.create(request));
    }

    @PutMapping("/{id}")
    public R<CredentialVO> update(@PathVariable Long id, @Valid @RequestBody CredentialSaveRequest request) {
        return R.ok(credentialService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        credentialService.removeCredential(id);
        return R.ok();
    }

    @PostMapping("/{id}/enable")
    public R<CredentialVO> enable(@PathVariable Long id) {
        return R.ok(credentialService.changeStatus(id, 1));
    }

    @PostMapping("/{id}/disable")
    public R<CredentialVO> disable(@PathVariable Long id) {
        return R.ok(credentialService.changeStatus(id, 0));
    }

    @PostMapping("/{id}/test")
    public R<CredentialTestVO> test(@PathVariable Long id) {
        return R.ok(credentialService.test(id));
    }
}
