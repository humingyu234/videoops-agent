package org.dromara.aivideo.user.studio.service.impl;

import org.dromara.aivideo.knowledge.dto.KnowledgeContextDTO;
import org.dromara.aivideo.questionnaire.dto.QuestionnaireAnswerDTO;
import org.dromara.aivideo.script.dto.ScriptGeneratedVersionDTO;
import org.dromara.aivideo.script.dto.ScriptGenerationRequestDTO;
import org.dromara.aivideo.user.studio.domain.bo.QuestionnaireAnswerBo;
import org.dromara.aivideo.user.studio.domain.bo.ScriptGenerateBo;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class ScriptGenerationServiceImplTest {

    private static final KnowledgeContextDTO KNOWLEDGE_CONTEXT = new KnowledgeContextDTO(
        List.of(2084460032627961859L),
        List.of("按流程生成。\n\n**User**\n私房菜示例"),
        List.of("不得编造事实"),
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    @Test
    void failsInsteadOfReturningStaticCopyWhenDeepSeekIsNotConfigured() {
        ScriptGenerationServiceImpl service = new ScriptGenerationServiceImpl(
            request -> KNOWLEDGE_CONTEXT, Optional.empty());

        assertThatThrownBy(() -> service.generate(request()))
            .isInstanceOf(ServiceException.class)
            .hasMessage("DeepSeek 文案生成服务未配置");
    }

    @Test
    void sendsTheFullDemandContextAndReturnsOnlyDeepSeekVersions() {
        AtomicReference<ScriptGenerationRequestDTO> captured = new AtomicReference<>();
        ScriptGenerationServiceImpl service = new ScriptGenerationServiceImpl(
            request -> KNOWLEDGE_CONTEXT,
            Optional.of(request -> {
                captured.set(request);
                return List.of(
                    version("痛点版"), version("故事版"), version("干货版"));
            }));

        var result = service.generate(request());

        assertThat(result.modelMode()).isEqualTo("deepseek");
        assertThat(result.scripts()).hasSize(3);
        assertThat(captured.get().industryCode()).isEqualTo("education");
        assertThat(captured.get().answerHistory()).singleElement()
            .extracting(QuestionnaireAnswerDTO::selectedLabels)
            .isEqualTo(List.of("学生"));
        assertThat(captured.get().knowledgeExcerpts()).containsExactly("按流程生成。");
    }

    @Test
    void rejectsMalformedOrDuplicateQuestionnaireHistoryBeforeCallingTheModel() {
        ScriptGenerationServiceImpl service = new ScriptGenerationServiceImpl(
            request -> KNOWLEDGE_CONTEXT,
            Optional.of(request -> {
                throw new AssertionError("invalid history must not reach the model");
            }));
        List<List<QuestionnaireAnswerBo>> invalidHistories = List.of(
            List.of(new QuestionnaireAnswerBo(
                "audience", "主要面向谁？", List.of(), List.of())),
            List.of(new QuestionnaireAnswerBo(
                "audience", "主要面向谁？", List.of("students"), List.of())),
            List.of(
                new QuestionnaireAnswerBo(
                    "audience", "主要面向谁？", List.of("students"), List.of("学生")),
                new QuestionnaireAnswerBo(
                    "audience", "再次确认？", List.of("parents"), List.of("家长")))
        );

        invalidHistories.forEach(history -> assertThatThrownBy(() -> service.generate(
            new ScriptGenerateBo("education", "knowledge", 60, "生成视频", history)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("问卷回答历史无效"));
    }

    @Test
    void failsWhenTheFixedCopywritingFlowKnowledgeVersionIsMissing() {
        KnowledgeContextDTO unrelatedContext = new KnowledgeContextDTO(
            List.of(101L), List.of("任意行业知识"), List.of(),
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        AtomicBoolean modelCalled = new AtomicBoolean();
        ScriptGenerationServiceImpl service = new ScriptGenerationServiceImpl(
            request -> unrelatedContext,
            Optional.of(request -> {
                modelCalled.set(true);
                return List.of(version("痛点版"), version("故事版"), version("干货版"));
            }));

        assertThatThrownBy(() -> service.generate(request()))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文案生成流程知识未配置");
        assertThat(modelCalled).isFalse();
    }

    private static ScriptGenerateBo request() {
        return new ScriptGenerateBo(
            "education", "知识分享", 60, "面向学生讲学习方法",
            List.of(new QuestionnaireAnswerBo(
                "audience", "主要面向谁？", List.of("students"), List.of("学生"))));
    }

    private static ScriptGeneratedVersionDTO version(String title) {
        return new ScriptGeneratedVersionDTO(title, 60, title + "口播正文");
    }
}
