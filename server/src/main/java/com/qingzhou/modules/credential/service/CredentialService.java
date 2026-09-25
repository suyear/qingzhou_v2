package com.qingzhou.modules.credential.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.qingzhou.common.api.PageQuery;
import com.qingzhou.modules.credential.dto.CredentialSaveRequest;
import com.qingzhou.modules.credential.dto.CredentialTestVO;
import com.qingzhou.modules.credential.dto.CredentialVO;
import com.qingzhou.modules.credential.entity.Credential;

public interface CredentialService extends IService<Credential> {

    IPage<CredentialVO> pageVo(PageQuery query);

    CredentialVO detail(Long id);

    CredentialVO create(CredentialSaveRequest request);

    CredentialVO update(Long id, CredentialSaveRequest request);

    void removeCredential(Long id);

    CredentialVO changeStatus(Long id, int status);

    CredentialTestVO test(Long id);
}
