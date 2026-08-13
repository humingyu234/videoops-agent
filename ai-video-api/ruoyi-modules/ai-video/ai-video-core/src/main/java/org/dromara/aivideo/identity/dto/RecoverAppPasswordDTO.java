package org.dromara.aivideo.identity.dto;

/** 使用一次性验证码恢复创作端密码的数据契约。 */
public record RecoverAppPasswordDTO(String challengeId, String verificationCode, String newPassword) {

    @Override
    public String toString() {
        return "RecoverAppPassword[challengeId=***, verificationCode=***, newPassword=***]";
    }
}
