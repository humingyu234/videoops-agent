package org.dromara.aivideo.identity.security;

/**
 * 自注册验证码或受信任注册流程的验证端口。
 *
 * <p>身份核心不依赖短信、邮件或运营端用户表；接入模块负责将实际验证结果适配为该端口。</p>
 */
@FunctionalInterface
public interface IAppSelfRegistrationVerificationService {

    /**
     * 判断指定的无敏感注册标识是否已完成受信任验证。
     *
     * @param request 已标准化且不含原始密码的注册标识
     * @return 已验证时为 {@code true}
     */
    boolean verifyAndConsume(AppSelfRegistrationVerificationRequest request);
}
