package org.dromara.aivideo.identity.security;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 存放在创作端令牌会话中的登录用户。
 *
 * @param principal 创作端主体快照
 * @param sessionId 创作端令牌会话编号
 */
public record AppLoginUser(AppPrincipalSnapshotDTO principal, String sessionId) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 校验登录会话必须绑定主体快照。
     */
    public AppLoginUser {
        Objects.requireNonNull(principal, "创作端主体快照不能为空");
    }

    /**
     * 返回当前创作端用户编号。
     *
     * @return 创作端用户编号
     */
    public long userId() {
        return Objects.requireNonNull(principal.appUserId(), "创作端用户编号不能为空");
    }
}
