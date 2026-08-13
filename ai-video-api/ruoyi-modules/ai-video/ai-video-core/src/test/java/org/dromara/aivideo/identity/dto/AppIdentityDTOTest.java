package org.dromara.aivideo.identity.dto;

import org.dromara.aivideo.identity.dto.ChangeAppPasswordDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class AppIdentityDTOTest {

    @Test
    void changePasswordCommandNeverLeaksPlaintextPasswordsInToString() {
        ChangeAppPasswordDTO command = new ChangeAppPasswordDTO(1001L, "current-password", "next-password", 2L);

        assertThat(command.toString()).doesNotContain("current-password", "next-password");
    }

    @Test
    void registrationCommandNeverLeaksPasswordOrContactDetailsInToString() {
        RegisterAppUserDTO command = new RegisterAppUserDTO(
            "creator", "registration-password", "Creator", "13800138000", "creator@example.com");

        assertThat(command.toString())
            .doesNotContain("registration-password", "13800138000", "creator@example.com");
    }

    @Test
    void resetPasswordCommandNeverLeaksPlaintextPasswordInToString() {
        ResetAppPasswordDTO command = new ResetAppPasswordDTO(1001L, "reset-password", 2L);

        assertThat(command.toString()).doesNotContain("reset-password");
    }

    @Test
    void profileCommandNeverLeaksContactDetailsInToString() {
        UpdateAppUserProfileDTO command = new UpdateAppUserProfileDTO(
            1001L, "Creator", "13900139000", "profile@example.com", false, false, 3L);

        assertThat(command.toString()).doesNotContain("13900139000", "profile@example.com");
    }
}
