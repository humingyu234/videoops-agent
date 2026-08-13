package org.dromara.aivideo.identity.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.aivideo.identity.domain.AppPermission;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创作端权限数据访问接口。
 */
public interface AppPermissionMapper extends BaseMapperPlus<AppPermission, AppPermission> {

    /**
     * 查询创作端用户当前有效的权限编码。
     *
     * @param userId 创作端用户编号
     * @param now 当前时间，用于判断用户角色关联有效期
     * @return 当前有效的权限编码列表
     */
    @Select("""
        SELECT DISTINCT p.permission_code
        FROM app_user_role ur
        INNER JOIN app_role r ON r.role_id = ur.role_id
        INNER JOIN app_role_permission rp ON rp.role_id = r.role_id
        INNER JOIN app_permission p ON p.permission_id = rp.permission_id
        WHERE ur.user_id = #{userId}
          AND ur.status = 'active'
          AND (ur.valid_from IS NULL OR ur.valid_from <= #{now})
          AND (ur.valid_until IS NULL OR ur.valid_until > #{now})
          AND r.status = 'active'
          AND r.del_flag = '0'
          AND rp.status = 'active'
          AND p.status = 'active'
        ORDER BY p.permission_code
        """)
    List<String> selectEffectivePermissionCodesByUserId(@Param("userId") long userId,
                                                         @Param("now") LocalDateTime now);
}
