package org.dromara.aivideo.user.auth.domain.vo;

import java.util.List;

/**
 * 当前已验证创作端用户的白名单投影。
 *
 * @param id 创作端用户编号的字符串表示
 * @param username 创作端用户名
 * @param displayName 展示名称
 * @param phone 脱敏手机号
 * @param email 脱敏邮箱
 * @param passwordResetRequired 是否必须先修改密码
 * @param roles 当前工作区角色集合
 * @param permissions 当前工作区权限集合
 * @param workspace 当前工作区摘要
 */
public record AppMeVo(
    String id,
    String username,
    String displayName,
    String phone,
    String email,
    boolean passwordResetRequired,
    List<String> roles,
    List<String> permissions,
    AppWorkspaceVo workspace
) {
}
