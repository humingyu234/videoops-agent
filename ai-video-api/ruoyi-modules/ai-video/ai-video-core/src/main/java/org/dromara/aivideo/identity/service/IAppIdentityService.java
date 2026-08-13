package org.dromara.aivideo.identity.service;

import org.dromara.aivideo.identity.dto.AuthenticatePasswordDTO;
import org.dromara.aivideo.identity.dto.BindSocialIdentityDTO;
import org.dromara.aivideo.identity.dto.ChangeAppPasswordDTO;
import org.dromara.aivideo.identity.dto.ChangeAppUserStatusDTO;
import org.dromara.aivideo.identity.dto.RegisterAppUserDTO;
import org.dromara.aivideo.identity.dto.RecoverAppPasswordDTO;
import org.dromara.aivideo.identity.dto.ResetAppPasswordDTO;
import org.dromara.aivideo.identity.dto.UpdateAppUserProfileDTO;
import org.dromara.aivideo.identity.dto.AppAuthenticatedIdentityDTO;
import org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppIdentitySnapshotDTO;
import org.dromara.aivideo.identity.dto.AppRegisteredIdentityDTO;
import org.dromara.aivideo.identity.dto.AppExternalIdentityDTO;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppLoginVerificationGrant;
import org.dromara.aivideo.identity.security.AppSelfRegistrationGrant;

/**
 * 独立创作端身份、密码和第三方身份服务。
 */
public interface IAppIdentityService {

    /**
     * 注册独立创作端用户。
     *
     * @param command 注册命令
     * @param actor 操作者
     * @return 注册后的身份信息
     */
    AppRegisteredIdentityDTO register(RegisterAppUserDTO command, AppActorContext actor);

    /**
     * 在受信任验证码或注册验证已通过后自注册独立创作端用户。
     *
     * <p>调用方不能传入创作端操作主体；新建用户会作为自身创建记录和审计记录的主体。</p>
     *
     * @param command 注册命令
     * @return 注册后的身份信息
     */
    AppRegisteredIdentityDTO registerSelf(RegisterAppUserDTO command, AppSelfRegistrationGrant grant);

    /**
     * 使用密码认证创作端用户。
     *
     * @param command 认证命令
     * @param client 已校验客户端快照
     * @return 认证身份信息
     */
    AppAuthenticatedIdentityDTO authenticatePassword(AuthenticatePasswordDTO command, AppAuthClientSnapshotDTO client);

    /**
     * 使用受信任验证码状态机签发的登录凭证认证创作端用户。
     *
     * <p>调用方不能传入用户编号、联系方式或修订号；这些事实只能来自已经原子校验的验证码挑战。
     * 方法会重新核验当前账号状态和身份修订，避免挑战签发后发生改密、停用或身份变更仍可登录。</p>
     *
     * @param grant 已验证的内部登录凭证
     * @param client 已校验客户端快照
     * @return 已认证身份信息
     */
    AppAuthenticatedIdentityDTO authenticateVerifiedContact(AppLoginVerificationGrant grant, AppAuthClientSnapshotDTO client);

    /**
     * 使用已由基础设施验证的外部身份认证创作端用户。
     *
     * <p>该方法只在 {@code app_social_identity} 和 {@code app_user} 事实源中查找已绑定、启用的创作端身份，
     * 不创建用户，也不读取运营端身份表。</p>
     *
     * @param externalIdentity 已验证的外部身份
     * @param client 已校验客户端快照
     * @return 已认证身份信息
     */
    AppAuthenticatedIdentityDTO authenticateExternalIdentity(AppExternalIdentityDTO externalIdentity,
                                                          AppAuthClientSnapshotDTO client);

    /**
     * 获取且校验当前创作端用户为启用状态。
     *
     * @param userId 创作端用户编号
     * @return 启用身份快照
     */
    AppIdentitySnapshotDTO requireActive(long userId);

    /**
     * 修改创作端用户密码。
     *
     * @param command 改密命令
     * @param actor 操作者
     */
    void changePassword(ChangeAppPasswordDTO command, AppActorContext actor);

    /**
     * 重置创作端用户密码。
     *
     * @param command 重置命令
     * @param actor 操作者
     */
    void resetPassword(ResetAppPasswordDTO command, AppActorContext actor);

    /**
     * 使用已一次性消费的找回验证码恢复创作端用户密码。
     *
     * <p>这是公开找回链路的内部入口，不能接收运营端操作者或前端提交的用户编号、联系方式、
     * 凭据修订号。</p>
     *
     * @param command 不含目标用户身份的找回命令
     * @param client 已校验客户端快照
     */
    void recoverPassword(RecoverAppPasswordDTO command, AppAuthClientSnapshotDTO client);

    /**
     * 变更创作端用户状态。
     *
     * @param command 状态变更命令
     * @param actor 操作者
     */
    void changeStatus(ChangeAppUserStatusDTO command, AppActorContext actor);

    /**
     * 修改创作端用户可展示资料与联系方式。
     *
     * @param command 用户资料更新命令
     * @param actor 操作者
     */
    void updateProfile(UpdateAppUserProfileDTO command, AppActorContext actor);

    /**
     * 绑定第三方身份。
     *
     * @param command 绑定命令
     * @param actor 操作者
     */
    void bindSocialIdentity(BindSocialIdentityDTO command, AppActorContext actor);

    /**
     * 解绑第三方身份。
     *
     * @param userId 创作端用户编号
     * @param socialIdentityId 第三方身份编号
     * @param actor 操作者
     */
    void unbindSocialIdentity(long userId, long socialIdentityId, AppActorContext actor);
}
