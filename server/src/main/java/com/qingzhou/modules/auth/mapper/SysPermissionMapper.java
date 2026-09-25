package com.qingzhou.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingzhou.modules.auth.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("""
            SELECT p.perm_code
            FROM qz_permission p
            INNER JOIN qz_role_permission rp ON rp.permission_id = p.id
            INNER JOIN qz_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId} AND p.deleted = 0
            GROUP BY p.perm_code
            ORDER BY MIN(p.sort_order)
            """)
    List<String> selectPermCodesByUserId(Long userId);

    @Select("""
            SELECT p.perm_code
            FROM qz_permission p
            INNER JOIN qz_role_permission rp ON rp.permission_id = p.id
            WHERE rp.role_id = #{roleId} AND p.deleted = 0
            ORDER BY p.sort_order
            """)
    List<String> selectPermCodesByRoleId(Long roleId);
}
