package org.dromara.aivideo.identity.security;

/**
 * 为运营端主体访问创作端身份资源提供显式授权判定的端口。
 *
 * <p>该端口不解析运营端会话，也不依赖运营端领域表；运营端适配模块负责提供实际实现。</p>
 */
@FunctionalInterface
public interface IAppIdentityOperationAuthorizationService {

    /**
     * 判断运营端主体是否可对指定创作端用户执行身份操作。
     *
     * @param actor 强类型运营端操作者
     * @param operation 请求执行的身份操作
     * @param targetResourceId 目标创作端资源编号；创建用户时为 {@code 0}
     * @return {@code true} 表示已授权，{@code false} 表示拒绝
     */
    boolean isAuthorized(AppActorContext actor, AppIdentityOperation operation, long targetResourceId);
}
