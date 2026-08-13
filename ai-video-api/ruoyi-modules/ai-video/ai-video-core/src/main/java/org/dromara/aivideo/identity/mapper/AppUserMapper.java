package org.dromara.aivideo.identity.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.Collection;
import java.util.List;

/**
 * 创作端用户数据访问接口。
 */
public interface AppUserMapper extends BaseMapperPlus<AppUser, AppUser> {

    /**
     * 查询任一候选标识已占用的创作端用户编号，包含逻辑删除记录。
     *
     * @param identifiers 标准化后的用户名、手机号或邮箱候选值
     * @return 已占用候选值对应的用户编号
     */
    @Select("""
        <script>
        SELECT user_id
        FROM app_user
        WHERE username_normalized IN
        <foreach collection='identifiers' item='identifier' open='(' separator=',' close=')'>
            #{identifier}
        </foreach>
        OR phone_normalized IN
        <foreach collection='identifiers' item='identifier' open='(' separator=',' close=')'>
            #{identifier}
        </foreach>
        OR email_normalized IN
        <foreach collection='identifiers' item='identifier' open='(' separator=',' close=')'>
            #{identifier}
        </foreach>
        </script>
        """)
    List<Long> selectUserIdsByAnyIdentifierIncludingDeleted(@Param("identifiers") Collection<String> identifiers);
}
