package org.dromara.aivideo.identity.dto;

/** 用户主动修改密码的数据契约。 */
public record ChangeAppPasswordDTO(long userId, String currentPassword, String newPassword,
                                   long expectedCredentialRevision) {

    @Override
    public String toString() {
        return "ChangeAppPassword[currentPassword=***, newPassword=***]";
    }
}
