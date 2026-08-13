package org.dromara.aivideo.identity.security;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class AppPasswordPolicyTest {

    @Test
    void rejectsPasswordsLongerThanTheBcryptUtf8LimitForHashAndMatch() {
        AppPasswordPolicy policy = new AppPasswordPolicy();
        String tooLongPassword = "a1" + "x".repeat(71);
        String validHash = policy.hash("correct-password1");

        assertThatThrownBy(() -> policy.hash(tooLongPassword)).isInstanceOf(ServiceException.class);
        assertThat(policy.matches(tooLongPassword, validHash)).isFalse();
    }
}
