package com.qingzhou.modules.execution.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.qingzhou.modules.execution.dto.ExecutionListVO;
import com.qingzhou.modules.execution.dto.ExecutionQuery;
import com.qingzhou.modules.execution.dto.ExecutionVO;
import com.qingzhou.modules.execution.entity.ExecutionInstance;

import java.util.List;

public interface ExecutionInstanceService extends IService<ExecutionInstance> {

    IPage<ExecutionListVO> pageExecutions(ExecutionQuery query);

    /** 导出用：与筛选一致，最多 maxRows 条 */
    List<ExecutionListVO> listForExport(ExecutionQuery query, int maxRows);

    ExecutionVO detail(Long id);

    /** 按保留天数清理过期执行实例及节点日志，返回删除的实例数 */
    int purgeExpired(int retentionDays);
}
