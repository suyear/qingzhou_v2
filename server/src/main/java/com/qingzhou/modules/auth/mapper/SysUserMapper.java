package com.qingzhou.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingzhou.modules.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
            SELECT r.role_code FROM qz_role r
            INNER JOIN qz_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.deleted = 0
            """)
    List<String> selectRoleCodesByUserId(Long userId);

    @Select("SELECT COUNT(1) FROM qz_user WHERE deleted = 0 AND status = 1")
    long countActiveUsers();
}
