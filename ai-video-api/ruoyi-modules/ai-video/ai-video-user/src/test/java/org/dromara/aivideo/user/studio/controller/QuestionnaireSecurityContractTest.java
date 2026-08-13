package org.dromara.aivideo.user.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.user.studio.domain.bo.QuestionnaireGenerateBo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class QuestionnaireSecurityContractTest {

    private static final String MIGRATION =
        "../docs/sql/ai-video/mysql/20260803_03_p1_questionnaire_app_permission.sql";

    @Test
    void generateRequiresTheAppStudioGeneratePermission() throws NoSuchMethodException {
        Method generate = QuestionnaireController.class.getDeclaredMethod(
            "generate", QuestionnaireGenerateBo.class);

        SaCheckPermission permission = AnnotatedElementUtils.findMergedAnnotation(
            generate, SaCheckPermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly("aivideo:studio:generate");
        assertThat(permission.type()).isEqualTo("app");
    }

    @Test
    void migrationAssignsStudioGeneratePermissionToPersonalCreatorIdempotently() throws IOException {
        Path migration = findProjectRoot().resolve(MIGRATION);

        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(sql)
            .contains("INSERT INTO app_role_permission")
            .contains("r.role_id = 1000101")
            .contains("r.role_code = 'personal_creator'")
            .contains("p.permission_id = 1000004")
            .contains("p.permission_code = 'aivideo:studio:generate'")
            .contains("ON DUPLICATE KEY UPDATE");
    }

    private static Path findProjectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (isProjectRoot(current)) {
                return current;
            }
            Path nested = current.resolve("ai-video-api");
            if (isProjectRoot(nested)) {
                return nested;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate ai-video-api project root");
    }

    private static boolean isProjectRoot(Path candidate) {
        return Files.isDirectory(candidate.resolve("ruoyi-modules/ai-video/ai-video-user"))
            && Files.isDirectory(candidate.resolve("../docs/sql/ai-video/mysql"));
    }
}
