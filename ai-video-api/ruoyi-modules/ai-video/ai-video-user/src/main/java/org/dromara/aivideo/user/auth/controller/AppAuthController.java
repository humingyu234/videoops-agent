package org.dromara.aivideo.user.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import org.dromara.aivideo.user.auth.service.IAppAuthApplicationService;
import org.dromara.aivideo.user.security.AppClientPolicyService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * 创作端独立认证资源。
 *
 * <p>Controller 只负责请求映射；身份、客户端、会话和审计均由独立 app 认证服务处理。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AppAuthController {

    private final IAppAuthApplicationService authApplicationService;

    public AppAuthController(IAppAuthApplicationService authApplicationService) {
        this.authApplicationService = Objects.requireNonNull(authApplicationService, "创作端认证应用服务不能为空");
    }

    /**
     * 使用经入口门禁验证的创作端客户端进行密码登录。
     */
    @PostMapping("/login")
    public R<AppLoginVo> login(@Valid @RequestBody AppPasswordLoginBo body, HttpServletRequest request) {
        AppAuthClientSnapshotDTO client = AppClientPolicyService.requireVerifiedClientSnapshot(request);
        return R.ok(authApplicationService.passwordLogin(body, client));
    }

    /**
     * 使用登录场景短信验证码创建创作端会话。
     */
    @PostMapping("/sms-logins")
    public R<AppLoginVo> smsLogin(@Valid @RequestBody AppCodeLoginBo body, HttpServletRequest request) {
        AppAuthClientSnapshotDTO client = AppClientPolicyService.requireVerifiedClientSnapshot(request);
        return R.ok(authApplicationService.smsLogin(body, client));
    }

    /**
     * 使用登录场景邮件验证码创建创作端会话。
     */
    @PostMapping("/email-logins")
    public R<AppLoginVo> emailLogin(@Valid @RequestBody AppCodeLoginBo body, HttpServletRequest request) {
        AppAuthClientSnapshotDTO client = AppClientPolicyService.requireVerifiedClientSnapshot(request);
        return R.ok(authApplicationService.emailLogin(body, client));
    }

    /**
     * 使用已绑定的第三方身份建立创作端会话。
     */
    @PostMapping("/social-logins")
    public R<AppLoginVo> socialLogin(@Valid @RequestBody AppSocialLoginBo body, HttpServletRequest request) {
        AppAuthClientSnapshotDTO client = AppClientPolicyService.requireVerifiedClientSnapshot(request);
        return R.ok(authApplicationService.socialLogin(body, client));
    }

    /**
     * 使用已绑定的微信小程序身份建立创作端会话。
     */
    @PostMapping("/mini-program-logins")
    public R<AppLoginVo> miniProgramLogin(@Valid @RequestBody AppMiniProgramLoginBo body,
                                           HttpServletRequest request) {
        AppAuthClientSnapshotDTO client = AppClientPolicyService.requireVerifiedClientSnapshot(request);
        return R.ok(authApplicationService.miniProgramLogin(body, client));
    }

    /**
     * 申请登录或找回密码的一次性验证码。
     */
    @PostMapping("/verification-codes")
    public R<AppVerificationChallengeVo> requestVerificationCode(@Valid @RequestBody AppVerificationCodeBo body,
                                                                  HttpServletRequest request) {
        AppAuthClientSnapshotDTO client = AppClientPolicyService.requireVerifiedClientSnapshot(request);
        return R.ok(authApplicationService.requestVerificationCode(body, client));
    }

    /**
     * 使用已一次性消费的验证码找回密码。
     */
    @PostMapping("/password-resets")
    public R<Void> recoverPassword(@Valid @RequestBody AppPasswordResetBo body, HttpServletRequest request) {
        AppAuthClientSnapshotDTO client = AppClientPolicyService.requireVerifiedClientSnapshot(request);
        authApplicationService.recoverPassword(body, client);
        return R.ok();
    }

    /**
     * 修改当前 app 会话所属用户的密码。
     */
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody AppPasswordChangeBo body) {
        authApplicationService.changePassword(body);
        return R.ok();
    }

    /**
     * 绑定当前创作端用户的第三方身份。
     */
    @PostMapping("/social-bindings")
    public R<Void> bindSocialIdentity(@Valid @RequestBody AppSocialBindingBo body) {
        authApplicationService.bindSocialIdentity(body);
        return R.ok();
    }

    /**
     * 解绑当前创作端用户的一条第三方身份。
     */
    @DeleteMapping("/social-bindings/{socialIdentityId}")
    public R<Void> unbindSocialIdentity(@PathVariable long socialIdentityId) {
        authApplicationService.unbindSocialIdentity(socialIdentityId);
        return R.ok();
    }

    /**
     * 查询当前创作端用户自己的安全会话。
     */
    @GetMapping("/sessions")
    public R<List<AppSessionVo>> sessions() {
        return R.ok(authApplicationService.listCurrentUserSessions());
    }

    /**
     * 幂等撤销当前创作端用户自己的指定会话。
     *
     * @param sessionId 随机会话编号
     */
    @DeleteMapping("/sessions/{sessionId}")
    public R<Void> revokeOwnSession(@PathVariable String sessionId) {
        authApplicationService.revokeOwnSession(sessionId);
        return R.ok();
    }

    /**
     * 返回当前 app 会话的用户投影。
     */
    @GetMapping("/me")
    public R<AppMeVo> me() {
        return R.ok(authApplicationService.me());
    }

    /**
     * 仅注销当前 app 会话。
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        authApplicationService.logoutCurrent();
        return R.ok();
    }
}
