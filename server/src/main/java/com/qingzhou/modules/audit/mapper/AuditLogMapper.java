package com.qingzhou.modules.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingzhou.modules.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
