package org.dromara.aivideo.identity.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.aivideo.identity.domain.AppUserRole;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创作端用户角色关联数据访问接口。
 */
public interface AppUserRoleMapper extends BaseMapperPlus<AppUserRole, AppUserRole> {

    /**
     * 查询当前有效且账号可用的角色关联创作端用户。
     *
     * @param roleId 创作端角色编号
     * @param now 当前时间，用于判断关联有效期
     * @return 需要递增权限修订号的创作端用户编号列表
     */
    @Select("""
        SELECT DISTINCT ur.user_id
        FROM app_user_role ur
        INNER JOIN app_user u ON u.user_id = ur.user_id
        WHERE ur.role_id = #{roleId}
          AND ur.status = 'active'
          AND (ur.valid_from IS NULL OR ur.valid_from <= #{now})
          AND (ur.valid_until IS NULL OR ur.valid_until > #{now})
          AND u.status = 'active'
          AND u.del_flag = '0'
        ORDER BY ur.user_id
        """)
    List<Long> selectCurrentEffectiveActiveUserIdsByRoleId(@Param("roleId") long roleId,
                                                            @Param("now") LocalDateTime now);
}
