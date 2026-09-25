package com.qingzhou.modules.component.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingzhou.modules.component.dto.ComponentSaveRequest;
import com.qingzhou.modules.component.dto.ComponentTestRequest;
import com.qingzhou.modules.component.dto.ComponentTestVO;
import com.qingzhou.modules.component.entity.ApiComponent;

public interface ApiComponentService extends IService<ApiComponent> {

    ApiComponent create(ComponentSaveRequest request);

    ApiComponent update(Long id, ComponentSaveRequest request);

    void deleteById(Long id);

    ComponentTestVO test(Long id, ComponentTestRequest request);
}
