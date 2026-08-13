package org.dromara.aivideo.identity.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.aivideo.identity.domain.AppRole;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创作端角色数据访问接口。
 */
public interface AppRoleMapper extends BaseMapperPlus<AppRole, AppRole> {

    /**
     * 查询创作端用户当前有效的角色编码。
     *
     * @param userId 创作端用户编号
     * @param now 当前时间，用于判断关联有效期
     * @return 当前有效的角色编码列表
     */
    @Select("""
        SELECT DISTINCT r.role_code
        FROM app_user_role ur
        INNER JOIN app_role r ON r.role_id = ur.role_id
        WHERE ur.user_id = #{userId}
          AND ur.status = 'active'
          AND (ur.valid_from IS NULL OR ur.valid_from <= #{now})
          AND (ur.valid_until IS NULL OR ur.valid_until > #{now})
          AND r.status = 'active'
          AND r.del_flag = '0'
        ORDER BY r.role_code
        """)
    List<String> selectEffectiveRoleCodesByUserId(@Param("userId") long userId,
                                                   @Param("now") LocalDateTime now);
}
