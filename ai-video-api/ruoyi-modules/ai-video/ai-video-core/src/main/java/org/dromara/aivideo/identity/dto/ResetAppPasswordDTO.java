package org.dromara.aivideo.identity.dto;

/** 管理端重置创作端用户密码的数据契约。 */
public record ResetAppPasswordDTO(long userId, String newPassword, long expectedCredentialRevision) {

    @Override
    public String toString() {
        return "ResetAppPasswordDTO[userId=" + userId
            + ", newPassword=***, expectedCredentialRevision=" + expectedCredentialRevision + "]";
    }
}
