package org.dromara.aivideo.identity.security;

import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.strategy.SaStrategy;
import cn.dev33.satoken.util.SaTokenConsts;

import java.util.List;
import java.util.Objects;

/**
 * 仅服务创作端 app 登录类型的独立 Sa-Token 逻辑。
 */
final class AppStpLogic extends StpLogicJwtForSimple {

    /**
     * 创建 app 登录类型的 Sa-Token 逻辑。
     */
    AppStpLogic() {
        super("app");
    }

    /**
     * 为 app 登录逻辑使用独立的当前请求令牌存储键，避免 Sa-Token 默认实现将 app 登录/注销
     * 覆盖同一请求中的运营端 {@code login} 令牌。
     *
     * @return app 专属的当前请求令牌存储键
     */
    @Override
    public String splicingKeyJustCreatedSave() {
        return SaTokenConsts.JUST_CREATED + getLoginType();
    }

    /**
     * 使用 app 专属请求存储键校验活跃超时，避免同一请求中默认 login 的检查结果跳过 app 校验。
     *
     * @param tokenValue 待校验的 app 令牌原文
     */
    @Override
    public void checkActiveTimeoutByConfig(String tokenValue) {
        if (isOpenCheckActiveTimeout()) {
            SaHolder.getStorage().get(activeTimeoutCheckedStorageKey(), () -> {
                checkActiveTimeout(tokenValue);
                if (SaStrategy.instance.autoRenew.apply(this)) {
                    updateLastActiveToNow(tokenValue);
                }
                return true;
            });
        }
    }

    /**
     * 清理当前请求中的 app 活跃超时校验标记，确保同请求重新登录会校验新令牌。
     */
    void clearActiveTimeoutCheckMarker() {
        SaHolder.getStorage().delete(activeTimeoutCheckedStorageKey());
    }

    /**
     * 构造当前登录类型专属的活跃超时校验标记键。
     *
     * @return app 专属请求存储键
     */
    private String activeTimeoutCheckedStorageKey() {
        return SaTokenConsts.TOKEN_ACTIVE_TIMEOUT_CHECKED_KEY + getLoginType();
    }

    /**
     * 仅从当前 app 令牌会话快照读取权限，绝不回退到运营端权限实现。
     *
     * @param loginId 待校验的创作端用户编号
     * @return 当前 app 会话快照中的权限编码列表
     */
    @Override
    public List<String> getPermissionList(Object loginId) {
        AppLoginUser loginUser = currentLoginUser(loginId);
        if (loginUser == null) {
            return List.of();
        }
        return List.copyOf(loginUser.principal().workspace().permissions());
    }

    /**
     * 仅从当前 app 令牌会话快照读取角色，绝不回退到运营端权限实现。
     *
     * @param loginId 待校验的创作端用户编号
     * @return 当前 app 会话快照中的角色编码列表
     */
    @Override
    public List<String> getRoleList(Object loginId) {
        AppLoginUser loginUser = currentLoginUser(loginId);
        if (loginUser == null || loginUser.principal().workspace().roleCode() == null
            || loginUser.principal().workspace().roleCode().isBlank()) {
            return List.of();
        }
        return List.of(loginUser.principal().workspace().roleCode());
    }

    /**
     * 从当前请求对应的 app 令牌会话读取并校验登录用户。
     *
     * @param loginId Sa-Token 正在授权的登录用户编号
     * @return 当前 app 登录用户；令牌会话不存在、数据不匹配时返回空
     */
    AppLoginUser currentLoginUser(Object loginId) {
        String tokenValue = getTokenValue();
        if (tokenValue == null || tokenValue.isBlank()) {
            return null;
        }
        SaSession tokenSession = getTokenSessionByToken(tokenValue, false);
        if (tokenSession == null) {
            return null;
        }
        Object sessionValue = tokenSession.get(AppLoginHelper.LOGIN_USER_SESSION_KEY);
        if (!(sessionValue instanceof AppLoginUser loginUser)
            || !sameLoginUser(loginId, loginUser.principal().appUserId())) {
            return null;
        }
        return loginUser;
    }

    /**
     * 严格比较 Sa-Token 登录编号与创作端会话快照中的用户编号。
     *
     * @param loginId Sa-Token 登录编号
     * @param appUserId 创作端会话快照用户编号
     * @return 两者表示同一创作端用户时返回 true
     */
    private boolean sameLoginUser(Object loginId, Long appUserId) {
        return appUserId != null && Objects.equals(String.valueOf(loginId), String.valueOf(appUserId));
    }
}
