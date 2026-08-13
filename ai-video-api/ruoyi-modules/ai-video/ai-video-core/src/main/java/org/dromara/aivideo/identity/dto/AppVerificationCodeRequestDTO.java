package org.dromara.aivideo.identity.dto;

import org.dromara.aivideo.identity.security.AppVerificationChannel;
import org.dromara.aivideo.identity.security.AppVerificationScenario;

/**
 * 申请创作端验证码的内部命令。
 *
 * <p>目标联系方式仅在本次状态机和投递端口中短暂使用，不进入日志、审计或响应。</p>
 *
 * @param scenario 验证码用途
 * @param channel 联系方式渠道
 * @param target 用户提交的联系方式
 */
public record AppVerificationCodeRequestDTO(AppVerificationScenario scenario, AppVerificationChannel channel,
                                         String target) {

    @Override
    public String toString() {
        return "AppVerificationCodeRequest[scenario=" + scenario + ", channel=" + channel + ", target=***]";
    }
}
