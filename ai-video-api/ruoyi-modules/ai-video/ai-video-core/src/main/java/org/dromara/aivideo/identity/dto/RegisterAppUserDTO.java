package org.dromara.aivideo.identity.dto;

/** 注册独立创作端用户的数据契约。 */
public record RegisterAppUserDTO(String username, String password, String displayName, String phone, String email) {

    @Override
    public String toString() {
        return "RegisterAppUserDTO[username=" + username
            + ", password=***, displayName=" + displayName + ", phone=***, email=***]";
    }
}
