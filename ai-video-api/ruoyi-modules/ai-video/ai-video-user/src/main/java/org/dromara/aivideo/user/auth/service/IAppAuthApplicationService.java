package org.dromara.aivideo.user.auth.service;

import org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO;
import org.dromara.aivideo.user.auth.domain.bo.AppPasswordChangeBo;
import org.dromara.aivideo.user.auth.domain.bo.AppCodeLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppMiniProgramLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppPasswordLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppPasswordResetBo;
import org.dromara.aivideo.user.auth.domain.bo.AppSocialBindingBo;
import org.dromara.aivideo.user.auth.domain.bo.AppSocialLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppVerificationCodeBo;
import org.dromara.aivideo.user.auth.domain.vo.AppLoginVo;
import org.dromara.aivideo.user.auth.domain.vo.AppMeVo;
import org.dromara.aivideo.user.auth.domain.vo.AppSessionVo;
import org.dromara.aivideo.user.auth.domain.vo.AppVerificationChallengeVo;

import java.util.List;

/**
 * 创作端 HTTP 认证边界的应用服务。
 */
public interface IAppAuthApplicationService {

    /**
     * 用入口层已验证的创作端客户端完成密码登录。
     *
     * @param body 密码登录请求
     * @param client 已验证客户端快照
     * @return 登录响应
     */
    AppLoginVo passwordLogin(AppPasswordLoginBo body, AppAuthClientSnapshotDTO client);

    /**
     * 使用登录场景短信验证码创建创作端会话。
     *
     * @param body 不透明挑战和验证码
     * @param client 已验证客户端快照
     * @return 登录响应
     */
    AppLoginVo smsLogin(AppCodeLoginBo body, AppAuthClientSnapshotDTO client);

    /**
     * 使用登录场景邮件验证码创建创作端会话。
     *
     * @param body 不透明挑战和验证码
     * @param client 已验证客户端快照
     * @return 登录响应
     */
    AppLoginVo emailLogin(AppCodeLoginBo body, AppAuthClientSnapshotDTO client);

    /**
     * 使用已绑定的第三方身份建立创作端会话。
     *
     * @param body 第三方一次性授权参数
     * @param client 已验证客户端快照
     * @return 登录响应
     */
    AppLoginVo socialLogin(AppSocialLoginBo body, AppAuthClientSnapshotDTO client);

    /**
     * 使用已绑定的微信小程序身份建立创作端会话。
     *
     * @param body 小程序一次性授权参数
     * @param client 已验证客户端快照
     * @return 登录响应
     */
    AppLoginVo miniProgramLogin(AppMiniProgramLoginBo body, AppAuthClientSnapshotDTO client);

    /**
     * 申请登录或找回密码使用的一次性验证码。
     *
     * @param body 公开验证码申请
     * @param client 已验证客户端快照
     * @return 不泄露账号存在状态的挑战摘要
     */
    AppVerificationChallengeVo requestVerificationCode(AppVerificationCodeBo body, AppAuthClientSnapshotDTO client);

    /**
     * 以已消费的一次性验证码找回创作端密码。
     *
     * @param body 找回密码请求
     * @param client 已验证客户端快照
     */
    void recoverPassword(AppPasswordResetBo body, AppAuthClientSnapshotDTO client);

    /**
     * 修改当前 app 会话所属用户的密码。
     *
     * @param body 当前密码和新密码
     */
    void changePassword(AppPasswordChangeBo body);

    /**
     * 为当前创作端用户绑定经外部适配器验证的第三方身份。
     *
     * @param body 第三方一次性授权参数
     */
    void bindSocialIdentity(AppSocialBindingBo body);

    /**
     * 解绑当前创作端用户的一条第三方身份。
     *
     * @param socialIdentityId 第三方身份编号
     */
    void unbindSocialIdentity(long socialIdentityId);

    /**
     * 查询当前创作端用户自身的安全会话。
     *
     * @return 不含令牌原文的会话投影
     */
    List<AppSessionVo> listCurrentUserSessions();

    /**
     * 幂等撤销当前创作端用户指定的安全会话。
     *
     * @param sessionId 随机会话编号
     */
    void revokeOwnSession(String sessionId);

    /**
     * 获取当前 app 会话对应的用户投影。
     *
     * @return 当前用户公开信息
     */
    AppMeVo me();

    /**
     * 注销当前 app 会话。
     */
    void logoutCurrent();
}
