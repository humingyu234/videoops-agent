package org.dromara.aivideo.identity.security;

import org.dromara.aivideo.identity.domain.AppActorType;

/**
 * 创作端身份域中的强类型审计操作者。
 *
 * @param actorType 操作者账号类型
 * @param actorId 操作者编号
 */
public record AppActorContext(AppActorType actorType, long actorId) {

    /**
     * 创建创作端用户操作者。
     *
     * @param userId 创作端用户编号
     * @return 强类型创作端操作者
     */
    public static AppActorContext appUser(long userId) {
        return new AppActorContext(AppActorType.APP_USER, userId);
    }

    /**
     * 创建运营端用户操作者。
     *
     * @param userId 运营端用户编号
     * @return 强类型运营端操作者
     */
    public static AppActorContext sysUser(long userId) {
        return new AppActorContext(AppActorType.SYS_USER, userId);
    }
}
