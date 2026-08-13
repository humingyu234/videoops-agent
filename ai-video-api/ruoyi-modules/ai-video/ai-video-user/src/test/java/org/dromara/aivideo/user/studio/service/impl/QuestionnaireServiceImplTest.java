package org.dromara.aivideo.user.studio.service.impl;

import org.dromara.aivideo.knowledge.dto.KnowledgeContextDTO;
import org.dromara.aivideo.knowledge.service.IKnowledgeContextService;
import org.dromara.aivideo.questionnaire.dto.QuestionnaireGeneratedOptionDTO;
import org.dromara.aivideo.questionnaire.dto.QuestionnaireGeneratedQuestionDTO;
import org.dromara.aivideo.questionnaire.dto.QuestionnaireGenerationRequestDTO;
import org.dromara.aivideo.user.studio.domain.bo.QuestionnaireAnswerBo;
import org.dromara.aivideo.user.studio.domain.bo.QuestionnaireGenerateBo;
import org.dromara.aivideo.user.studio.domain.vo.QuestionnaireGenerateVo;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class QuestionnaireServiceImplTest {

    private static final long COPYWRITING_FLOW_VERSION_ID = 2084460032627961859L;

    private static final KnowledgeContextDTO KNOWLEDGE_CONTEXT = new KnowledgeContextDTO(
        List.of(COPYWRITING_FLOW_VERSION_ID),
        List.of("每一题必须依赖用户此前的全部选择。"),
        List.of("每次只生成下一题。"),
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    @Test
    void failsWhenTheDeepSeekQuestionnaireServiceIsNotConfigured() {
        IKnowledgeContextService knowledgeContextService = request -> KNOWLEDGE_CONTEXT;
        QuestionnaireServiceImpl service = new QuestionnaireServiceImpl(
            knowledgeContextService, Optional.empty());

        assertThatThrownBy(() -> service.generate(new QuestionnaireGenerateBo(
            "general", "brand_awareness", 60, "想做一条视频。", List.of())))
            .isInstanceOf(ServiceException.class)
            .hasMessage("DeepSeek 问卷生成服务未配置");
    }

    @Test
    void exposesDeepSeekFailuresInsteadOfGeneratingAKnowledgeFallbackQuestion() {
        QuestionnaireServiceImpl service = new QuestionnaireServiceImpl(
            request -> KNOWLEDGE_CONTEXT,
            Optional.of(request -> {
                throw new ServiceException("DeepSeek 问卷生成失败");
            }));

        assertThatThrownBy(() -> service.generate(new QuestionnaireGenerateBo(
            "general", "brand_awareness", 60, "想做一条视频。", List.of())))
            .isInstanceOf(ServiceException.class)
            .hasMessage("DeepSeek 问卷生成失败");
    }

    @Test
    void rejectsPrematureCompletionInsteadOfGeneratingAKnowledgeFallbackQuestion() {
        QuestionnaireServiceImpl service = new QuestionnaireServiceImpl(
            request -> KNOWLEDGE_CONTEXT,
            Optional.of(request -> Optional.empty()));

        assertThatThrownBy(() -> service.generate(new QuestionnaireGenerateBo(
            "general", "brand_awareness", 60, "想做一条视频。", List.of())))
            .isInstanceOf(ServiceException.class)
            .hasMessage("DeepSeek 问卷生成结果无效，请重试");
    }

    @Test
    void rejectsFiveForgedEmptyAnswersInsteadOfCompletingTheQuestionnaire() {
        List<QuestionnaireAnswerBo> forgedHistory = IntStream.range(0, 5)
            .mapToObj(index -> new QuestionnaireAnswerBo(
                "question-" + index, "问题 " + index, List.of(), List.of()))
            .toList();
        QuestionnaireServiceImpl service = new QuestionnaireServiceImpl(
            request -> KNOWLEDGE_CONTEXT,
            Optional.of(request -> {
                throw new AssertionError("invalid history must not reach the model");
            }));

        assertThatThrownBy(() -> service.generate(new QuestionnaireGenerateBo(
            "general", "brand_awareness", 60, "生成视频", List.of(), forgedHistory)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("问卷回答历史无效");
    }

    @Test
    void rejectsMalformedOrDuplicateAnswerHistoryBeforeCallingTheModel() {
        QuestionnaireServiceImpl service = new QuestionnaireServiceImpl(
            request -> KNOWLEDGE_CONTEXT,
            Optional.of(request -> {
                throw new AssertionError("invalid history must not reach the model");
            }));
        List<List<QuestionnaireAnswerBo>> invalidHistories = List.of(
            List.of(new QuestionnaireAnswerBo(
                "audience", "主要面向谁？", List.of("students"), List.of())),
            List.of(
                new QuestionnaireAnswerBo(
                    "audience", "主要面向谁？", List.of("students"), List.of("学生")),
                new QuestionnaireAnswerBo(
                    "audience", "再次确认？", List.of("parents"), List.of("家长")))
        );

        invalidHistories.forEach(history -> assertThatThrownBy(() -> service.generate(
            new QuestionnaireGenerateBo(
                "education", "knowledge", 60, "生成视频", List.of(), history)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("问卷回答历史无效"));
    }

    @Test
    void failsWhenTheFixedCopywritingFlowKnowledgeVersionIsMissing() {
        KnowledgeContextDTO unrelatedContext = new KnowledgeContextDTO(
            List.of(101L), List.of("任意行业知识"), List.of(),
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        AtomicBoolean modelCalled = new AtomicBoolean();
        QuestionnaireServiceImpl service = new QuestionnaireServiceImpl(
            request -> unrelatedContext,
            Optional.of(request -> {
                modelCalled.set(true);
                return Optional.of(generatedQuestion());
            }));

        assertThatThrownBy(() -> service.generate(new QuestionnaireGenerateBo(
            "general", "brand_awareness", 60, "生成视频", List.of())))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文案生成流程知识未配置");
        assertThat(modelCalled).isFalse();
    }

    @Test
    void sendsAllPreviousAnswersToTheModelAndReturnsItsSingleNextQuestion() {
        AtomicReference<QuestionnaireGenerationRequestDTO> captured = new AtomicReference<>();
        QuestionnaireServiceImpl service = new QuestionnaireServiceImpl(
            request -> KNOWLEDGE_CONTEXT,
            Optional.of(request -> {
                captured.set(request);
                return Optional.of(new QuestionnaireGeneratedQuestionDTO(
                    "coreMessage", "最想让宝妈记住哪个卖点？", "基于上一题继续追问", true,
                    List.of(
                        new QuestionnaireGeneratedOptionDTO("省时", "save_time"),
                        new QuestionnaireGeneratedOptionDTO("省力", "save_effort"),
                        new QuestionnaireGeneratedOptionDTO("划算", "good_value"))));
            }));
        QuestionnaireAnswerBo previous = new QuestionnaireAnswerBo(
            "audienceStage", "最想影响哪类人？", List.of("mothers"), List.of("宝妈"));

        QuestionnaireGenerateVo result = service.generate(new QuestionnaireGenerateBo(
            "ecommerce", "customer_acquisition", 60, "89元家用拖把。",
            List.of("audienceStage"), List.of(previous)));

        assertThat(result.modelMode()).isEqualTo("deepseek");
        assertThat(result.questions()).singleElement()
            .satisfies(question -> assertThat(question.id()).isEqualTo("coreMessage"));
        assertThat(captured.get().answerHistory()).singleElement().satisfies(answer -> {
            assertThat(answer.questionTitle()).isEqualTo("最想影响哪类人？");
            assertThat(answer.selectedLabels()).containsExactly("宝妈");
        });
        assertThat(captured.get().knowledgeExcerpts())
            .containsExactly(KNOWLEDGE_CONTEXT.excerpts().getFirst());
    }

    @Test
    void removesKnowledgeConversationExamplesBeforeSendingRulesToTheModel() {
        KnowledgeContextDTO contextWithExample = new KnowledgeContextDTO(
            List.of(COPYWRITING_FLOW_VERSION_ID),
            List.of("每题必须依赖此前选择。\n\n**User**\n私房菜\n\n**Assistant**\n请选择地道风味。"),
            List.of(),
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        AtomicReference<QuestionnaireGenerationRequestDTO> captured = new AtomicReference<>();
        QuestionnaireServiceImpl service = new QuestionnaireServiceImpl(
            request -> contextWithExample,
            Optional.of(request -> {
                captured.set(request);
                return Optional.of(new QuestionnaireGeneratedQuestionDTO(
                    "productTopic", "这次课程主要讲什么主题？", "结合教育培训行业", true,
                    List.of(
                        new QuestionnaireGeneratedOptionDTO("学科知识", "subject"),
                        new QuestionnaireGeneratedOptionDTO("职业技能", "career_skill"),
                        new QuestionnaireGeneratedOptionDTO("兴趣培养", "interest"))));
            }));

        service.generate(new QuestionnaireGenerateBo(
            "education", "课程讲解", 60, "", List.of()));

        assertThat(captured.get().knowledgeExcerpts())
            .containsExactly("每题必须依赖此前选择。");
        assertThat(captured.get().knowledgeExcerpts().getFirst())
            .doesNotContain("私房菜", "地道风味");
    }

    private static QuestionnaireGeneratedQuestionDTO generatedQuestion() {
        return new QuestionnaireGeneratedQuestionDTO(
            "audience", "主要面向谁？", "选择核心受众", true,
            List.of(
                new QuestionnaireGeneratedOptionDTO("学生", "students"),
                new QuestionnaireGeneratedOptionDTO("家长", "parents"),
                new QuestionnaireGeneratedOptionDTO("教师", "teachers")));
    }
}
