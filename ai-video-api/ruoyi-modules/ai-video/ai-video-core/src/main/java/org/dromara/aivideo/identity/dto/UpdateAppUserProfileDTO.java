package org.dromara.aivideo.identity.dto;

/** 修改创作端用户资料与联系方式的数据契约。 */
public record UpdateAppUserProfileDTO(long userId, String displayName, String phone, String email,
                                      boolean clearPhone, boolean clearEmail, long expectedIdentityRevision) {

    @Override
    public String toString() {
        return "UpdateAppUserProfileDTO[userId=" + userId + ", displayName=" + displayName
            + ", phone=***, email=***, clearPhone=" + clearPhone + ", clearEmail=" + clearEmail
            + ", expectedIdentityRevision=" + expectedIdentityRevision + "]";
    }
}
