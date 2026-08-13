package org.dromara.aivideo.infra.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class WorkflowInputFileValidatorTest {

    private final WorkflowInputFileValidator validator = new WorkflowInputFileValidator();

    @Test
    void acceptsPngOnlyWhenTheActualMediaTypeMatchesTheDeclaredType() {
        assertThat(validator.detectContentType(new byte[] {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'
        })).isEqualTo("image/png");
    }

    @Test
    void rejectsUnknownOrMismatchedMediaHeadersBeforeAWorkflowAssetCanBecomeReady() {
        assertThatThrownBy(() -> validator.requireDeclaredTypeMatches(
            "image/png", new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'}))
            .hasMessage("工作流输入文件类型与实际内容不一致");
        assertThatThrownBy(() -> validator.detectContentType(new byte[] {1, 2, 3, 4}))
            .hasMessage("无法识别工作流输入文件类型");
    }
}
