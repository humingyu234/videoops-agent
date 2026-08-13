package org.dromara.aivideo.identity.service;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;
import org.dromara.aivideo.identity.dto.AppSessionQueryDTO;
import org.dromara.aivideo.identity.dto.AppSessionSummaryDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.common.core.domain.PageResult;

import java.util.List;
import java.util.Optional;

/**
 * 创作端 app 会话的创建、查询、撤销和失效服务。
 */
public interface IAppSessionService {

    /**
     * 分页查询创作端在线会话摘要。
     *
     * @param query 分页查询条件
     * @return 不包含令牌原文的会话分页结果
     */
    PageResult<AppSessionSummaryDTO> page(AppSessionQueryDTO query);

    /**
     * 按随机会话编号精确查询一个创作端在线会话摘要。
     *
     * <p>该查询不扫描分页结果，运营端可据此在任意在线会话数量下执行精确撤销。
     * 返回值不包含令牌原文或服务端令牌引用。</p>
     *
     * @param sessionId 随机会话编号
     * @return 在线会话摘要；不存在或已失效时为空
     */
    Optional<AppSessionSummaryDTO> findBySessionId(String sessionId);

    /**
     * 查询指定创作端用户的在线会话摘要。
     *
     * @param userId 创作端用户编号
     * @return 不包含令牌原文的会话列表
     */
    List<AppSessionSummaryDTO> currentUserSessions(long userId);

    /**
     * 刷新当前已认证 app 会话的最近活动时间。
     *
     * <p>实现只能从当前 app 登录上下文取得会话和令牌服务端引用，不接收令牌原文。</p>
     */
    void touchCurrentSession();

    /**
     * 精确撤销一个创作端会话。
     *
     * @param actorUserId 操作目标创作端用户编号
     * @param sessionId 随机会话编号
     * @param actor 已认证的操作者
     * @param reason 撤销原因
     */
    void revokeSession(long actorUserId, String sessionId, AppActorContext actor, String reason);

    /**
     * 替换当前 app 会话的工作区快照。
     *
     * @param workspace 替换后的工作区快照
     * @return 替换后的完整创作端主体快照
     */
    AppPrincipalSnapshotDTO replaceWorkspace(AppWorkspaceSessionSnapshotDTO workspace);

    /**
     * 使指定创作端用户的全部 app 会话失效。
     *
     * @param appUserId 创作端用户编号
     * @param reason 会话失效原因
     */
    void invalidateUserSessions(Long appUserId, AppSessionInvalidationReason reason);

    /**
     * 使指定组织工作区的 app 会话失效。
     *
     * @param organizationId 组织编号
     * @param reason 会话失效原因
     */
    void invalidateOrganizationSessions(Long organizationId, AppSessionInvalidationReason reason);
}
