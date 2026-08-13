package org.dromara.aivideo.infra.questionnaire;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class KnowledgeOutputGuardTest {

    @Test
    void rejectsLongVerbatimKnowledgeExcerptInGeneratedOutput() {
        String knowledge = "这是仅供模型理解流程的内部知识正文，不允许通过生成问题或者文案逐字返回给创作端用户。";

        assertThatThrownBy(() -> KnowledgeOutputGuard.rejectVerbatimLeak(
            "模型输出：" + knowledge, List.of(knowledge)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("DeepSeek 输出包含不可披露的知识内容");
    }

    @Test
    void allowsShortCommonPhrases() {
        assertThatCode(() -> KnowledgeOutputGuard.rejectVerbatimLeak(
            "请突出核心卖点", List.of("文案生成流程要求突出核心卖点并给出行动引导")))
            .doesNotThrowAnyException();
    }
}
