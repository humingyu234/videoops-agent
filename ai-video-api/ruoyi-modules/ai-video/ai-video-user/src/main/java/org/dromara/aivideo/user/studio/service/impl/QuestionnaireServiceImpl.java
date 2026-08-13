package org.dromara.aivideo.user.studio.service.impl;

import org.dromara.aivideo.knowledge.dto.KnowledgeContextDTO;
import org.dromara.aivideo.knowledge.dto.KnowledgeContextRequestDTO;
import org.dromara.aivideo.knowledge.service.IKnowledgeContextService;
import org.dromara.aivideo.questionnaire.dto.QuestionnaireAnswerDTO;
import org.dromara.aivideo.questionnaire.dto.QuestionnaireGeneratedQuestionDTO;
import org.dromara.aivideo.questionnaire.dto.QuestionnaireGenerationRequestDTO;
import org.dromara.aivideo.questionnaire.service.IQuestionnaireModelService;
import org.dromara.aivideo.user.studio.domain.bo.QuestionnaireAnswerBo;
import org.dromara.aivideo.user.studio.domain.bo.QuestionnaireGenerateBo;
import org.dromara.aivideo.user.studio.domain.vo.QuestionnaireGenerateVo;
import org.dromara.aivideo.user.studio.domain.vo.QuestionnaireOptionVo;
import org.dromara.aivideo.user.studio.domain.vo.QuestionnaireQuestionVo;
import org.dromara.aivideo.user.studio.service.IQuestionnaireService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/** 用户端知识增强问卷服务实现。 */
@Service
public class QuestionnaireServiceImpl implements IQuestionnaireService {

    private static final int MIN_DYNAMIC_QUESTIONS = 3;
    private static final int MAX_DYNAMIC_QUESTIONS = 5;
    private static final long COPYWRITING_FLOW_VERSION_ID = 2084460032627961859L;

    private final IKnowledgeContextService knowledgeContextService;
    private final Optional<IQuestionnaireModelService> questionnaireModelService;

    public QuestionnaireServiceImpl(IKnowledgeContextService knowledgeContextService,
                                    Optional<IQuestionnaireModelService> questionnaireModelService) {
        this.knowledgeContextService = Objects.requireNonNull(knowledgeContextService);
        this.questionnaireModelService = Objects.requireNonNull(questionnaireModelService);
    }

    @Override
    public QuestionnaireGenerateVo generate(QuestionnaireGenerateBo request) {
        validateAnswerHistory(request.answerHistory());
        IQuestionnaireModelService modelService = questionnaireModelService
            .orElseThrow(() -> new ServiceException("DeepSeek 问卷生成服务未配置"));
        KnowledgeContextDTO context = knowledgeContextService.resolve(new KnowledgeContextRequestDTO(
            request.industryCode(), request.purposeCode(), request.durationSeconds(), List.of()));
        if (request.answerHistory().size() >= MAX_DYNAMIC_QUESTIONS) {
            return response(List.of(), context, "deepseek");
        }
        Optional<QuestionnaireGeneratedQuestionDTO> generated = modelService
            .generateNext(toModelRequest(request, context));
        if (generated.isPresent()) {
            return response(List.of(toVo(generated.get())), context, "deepseek");
        }
        if (request.answerHistory().size() >= MIN_DYNAMIC_QUESTIONS) {
            return response(List.of(), context, "deepseek");
        }
        throw new ServiceException("DeepSeek 问卷生成结果无效，请重试");
    }

    private QuestionnaireGenerationRequestDTO toModelRequest(QuestionnaireGenerateBo request,
                                                               KnowledgeContextDTO context) {
        List<String> flowExcerpts = IntStream.range(0, context.knowledgeVersionIds().size())
            .filter(index -> COPYWRITING_FLOW_VERSION_ID == context.knowledgeVersionIds().get(index))
            .mapToObj(index -> compactKnowledge(context.excerpts().get(index)))
            .toList();
        if (flowExcerpts.isEmpty()) {
            throw new ServiceException("文案生成流程知识未配置");
        }
        List<QuestionnaireAnswerDTO> answerHistory = request.answerHistory().stream()
            .map(answer -> new QuestionnaireAnswerDTO(answer.questionId(), answer.questionTitle(),
                answer.selectedValues(), answer.selectedLabels()))
            .toList();
        return new QuestionnaireGenerationRequestDTO(
            request.industryCode(), request.purposeCode(), request.durationSeconds(), request.demandText(),
            answerHistory, flowExcerpts, context.copyRules().stream().limit(20).toList());
    }

    private static String compactKnowledge(String content) {
        String normalized = content.replace("\r\n", "\n").trim();
        int conversationStart = normalized.indexOf("**User**");
        String rulesOnly = conversationStart >= 0
            ? normalized.substring(0, conversationStart).trim()
            : normalized;
        return rulesOnly.length() <= 16000 ? rulesOnly : rulesOnly.substring(0, 16000);
    }

    private static void validateAnswerHistory(List<QuestionnaireAnswerBo> answerHistory) {
        Set<String> questionIds = new HashSet<>();
        for (QuestionnaireAnswerBo answer : answerHistory) {
            if (answer == null
                || answer.questionId() == null || answer.questionId().isBlank()
                || answer.questionTitle() == null || answer.questionTitle().isBlank()
                || answer.selectedValues().isEmpty() || answer.selectedLabels().isEmpty()
                || answer.selectedValues().size() != answer.selectedLabels().size()
                || answer.selectedValues().stream().anyMatch(QuestionnaireServiceImpl::isBlank)
                || answer.selectedLabels().stream().anyMatch(QuestionnaireServiceImpl::isBlank)
                || !questionIds.add(answer.questionId())) {
                throw new ServiceException("问卷回答历史无效");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static QuestionnaireQuestionVo toVo(QuestionnaireGeneratedQuestionDTO question) {
        return new QuestionnaireQuestionVo(
            question.id(), question.title(), question.description(), question.required(),
            question.options().stream()
                .map(option -> new QuestionnaireOptionVo(option.label(), option.value()))
                .toList());
    }

    private static QuestionnaireGenerateVo response(List<QuestionnaireQuestionVo> questions,
                                                     KnowledgeContextDTO context, String modelMode) {
        return new QuestionnaireGenerateVo(
            questions,
            context.knowledgeVersionIds().stream().map(String::valueOf).toList(),
            context.contentHash(),
            modelMode);
    }
}
