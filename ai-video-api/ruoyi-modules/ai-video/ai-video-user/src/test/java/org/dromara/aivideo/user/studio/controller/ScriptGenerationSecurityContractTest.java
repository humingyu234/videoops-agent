package org.dromara.aivideo.user.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.user.studio.domain.bo.ScriptGenerateBo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ScriptGenerationSecurityContractTest {

    @Test
    void generateRequiresTheAppStudioGeneratePermission() throws NoSuchMethodException {
        Method generate = ScriptGenerationController.class.getDeclaredMethod(
            "generate", ScriptGenerateBo.class);

        SaCheckPermission permission = AnnotatedElementUtils.findMergedAnnotation(
            generate, SaCheckPermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly("aivideo:studio:generate");
        assertThat(permission.type()).isEqualTo("app");
    }
}
