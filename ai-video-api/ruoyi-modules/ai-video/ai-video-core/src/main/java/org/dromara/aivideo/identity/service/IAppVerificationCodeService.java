package org.dromara.aivideo.identity.service;

import org.dromara.aivideo.identity.dto.AppVerificationCodeRequestDTO;
import org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppVerificationChallengeDTO;

/**
 * 创作端登录和找回密码验证码服务。
 */
public interface IAppVerificationCodeService {

    /**
     * 创建并尝试投递一次性验证码。
     *
     * <p>未知、停用或已删除账号返回相同形状的中性挑战，避免通过响应枚举账号。</p>
     *
     * @param request 验证码申请
     * @param client 已验证创作端客户端快照
     * @return 可安全返回给客户端的挑战摘要
     */
    AppVerificationChallengeDTO issue(AppVerificationCodeRequestDTO request, AppAuthClientSnapshotDTO client);
}
