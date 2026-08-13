package org.dromara.aivideo.identity.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 外部身份端口模型的敏感值保护测试。
 */
@Tag("dev")
class AppExternalIdentityDTOTest {

    @Test
    void redactsSocialAuthorizationCodeAndStateFromToString() {
        AppSocialIdentityAuthorizationDTO command = new AppSocialIdentityAuthorizationDTO(
            "github", "social-code-secret", "social-state-secret");

        assertThat(command.toString())
            .contains("provider=github")
            .doesNotContain("social-code-secret", "social-state-secret");
    }

    @Test
    void redactsMiniProgramAuthorizationCodeFromToString() {
        AppMiniProgramAuthorizationDTO command = new AppMiniProgramAuthorizationDTO("mini-code-secret");

        assertThat(command.toString()).doesNotContain("mini-code-secret");
    }

    @Test
    void exposesOnlyProviderAndSubjectInTheExternalIdentityResult() {
        AppExternalIdentityDTO result = new AppExternalIdentityDTO("wechat_mini_program", "openid-secret");

        assertThat(result.provider()).isEqualTo("wechat_mini_program");
        assertThat(result.providerSubject()).isEqualTo("openid-secret");
        assertThat(result.toString()).doesNotContain("openid-secret");
    }

    @Test
    void rejectsBlankAuthorizationValuesWithoutEchoingThem() {
        assertThatThrownBy(() -> new AppSocialIdentityAuthorizationDTO("github", "   ", "state"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("第三方授权参数无效");
        assertThatThrownBy(() -> new AppMiniProgramAuthorizationDTO("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("小程序授权参数无效");
    }
}
