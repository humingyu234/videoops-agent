import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { ApiError, isAbortError } from '@/services/ai-video/core/errors';
import { questionnaireApi } from '@/services/ai-video/questionnaire/api';
import type {
  GeneratedQuestionnaire,
  QuestionnaireAnswerHistory,
  QuestionnaireOption,
  QuestionnaireQuestion,
} from '@/services/ai-video/questionnaire/types';
import StepFooter from '../components/StepFooter';
import StudioIcon, { type StudioIconName } from '../components/StudioIcon';
import { INDUSTRIES, PURPOSES, type StudioState } from '../model';
import { isQuestionnaireOtherOption } from '../questionnaireAnswers';

interface DemandStepProps {
  state: StudioState;
  update: (patch: Partial<StudioState>) => void;
  isGeneratingScript?: boolean;
  onNext: () => void;
  onPrevious: () => void;
  onFinish: () => void;
}

type QuestionnaireStatus =
  | 'idle'
  | 'loading'
  | 'loading-next'
  | 'ready'
  | 'unauthorized'
  | 'forbidden'
  | 'error';

function getQuestionnaireFailureStatus(
  error: unknown,
):
  | Exclude<QuestionnaireStatus, 'idle' | 'loading' | 'loading-next' | 'ready'>
  | 'aborted' {
  if (isAbortError(error)) return 'aborted';
  if (!(error instanceof ApiError)) return 'error';
  if (
    error.code === 401 ||
    error.status === 401 ||
    error.code === 46129 ||
    error.code === 46131
  ) {
    return 'unauthorized';
  }
  if (error.code === 403 || error.status === 403) return 'forbidden';
  if (
    (error.status !== undefined && error.status < 500) ||
    (error.code >= 400 && error.code < 500) ||
    error.code >= 46000
  ) {
    return 'error';
  }
  return 'error';
}

function hasValidAnswer(
  answers: string[] | undefined,
  options: QuestionnaireOption[],
  otherAnswer?: string,
): boolean {
  if (!answers?.length) return false;
  if (!options.length) return answers.some((answer) => answer.trim());
  const otherOption = options.find(isQuestionnaireOtherOption);
  if (otherOption && answers.includes(otherOption.value)) {
    return Boolean(otherAnswer?.trim());
  }
  const currentValues = new Set(options.map((option) => option.value));
  return answers.some((answer) => currentValues.has(answer));
}

function hasIncompleteOtherAnswer(
  answers: string[] | undefined,
  options: QuestionnaireOption[],
  otherAnswer?: string,
): boolean {
  const otherOption = options.find(isQuestionnaireOtherOption);
  return Boolean(
    otherOption && answers?.includes(otherOption.value) && !otherAnswer?.trim(),
  );
}

const DemandStep: React.FC<DemandStepProps> = ({
  state,
  update,
  isGeneratingScript = false,
  onNext,
  onPrevious,
  onFinish,
}) => {
  const [generated, setGenerated] = useState<GeneratedQuestionnaire>();
  const [questionnaireStatus, setQuestionnaireStatus] =
    useState<QuestionnaireStatus>('idle');
  const activeControllerRef = useRef<AbortController | undefined>(undefined);
  const requestGenerationRef = useRef(0);
  const failedRequestRef = useRef<{
    answerHistory: QuestionnaireAnswerHistory[];
    existingQuestions: QuestionnaireQuestion[];
    reset: boolean;
  }>(undefined);

  const requestQuestionnaire = useCallback(
    async (
      answerHistory: QuestionnaireAnswerHistory[] = [],
      existingQuestions: QuestionnaireQuestion[] = [],
      reset = true,
    ) => {
      if (!state.industry || !state.purpose) {
        setGenerated(undefined);
        setQuestionnaireStatus('idle');
        return;
      }

      activeControllerRef.current?.abort();
      const controller = new AbortController();
      activeControllerRef.current = controller;
      const requestGeneration = ++requestGenerationRef.current;
      failedRequestRef.current = {
        answerHistory,
        existingQuestions,
        reset,
      };
      setQuestionnaireStatus(reset ? 'loading' : 'loading-next');
      try {
        const result = await questionnaireApi.generate(
          {
            demandText: state.supplement.trim(),
            durationSeconds: state.duration,
            industryCode:
              state.industry === 'custom'
                ? state.customIndustry.trim() || 'custom'
                : state.industry,
            purposeCode:
              state.purpose === '__custom'
                ? state.customPurpose.trim() || 'custom'
                : state.purpose,
            answeredSlots: answerHistory.map((answer) => answer.questionId),
            answerHistory,
          },
          { signal: controller.signal },
        );
        if (requestGeneration !== requestGenerationRef.current) return;
        if (result.modelMode !== 'deepseek') {
          throw new Error('问卷生成未使用 DeepSeek');
        }
        const existingIds = new Set(
          existingQuestions.map((question) => question.id),
        );
        const nextQuestions = result.questions.filter(
          (question) => !existingIds.has(question.id),
        );
        if (!reset && nextQuestions.length !== result.questions.length) {
          throw new Error('DeepSeek 返回了重复问卷题目');
        }
        const questions = reset
          ? result.questions
          : [...existingQuestions, ...nextQuestions];
        const questionnaire = { ...result, questions };
        failedRequestRef.current = undefined;
        setGenerated(questionnaire);
        setQuestionnaireStatus('ready');
        update({
          questionnaire,
          surveyCursor: reset
            ? 0
            : nextQuestions.length
              ? existingQuestions.length
              : questions.length,
        });
      } catch (error) {
        if (requestGeneration !== requestGenerationRef.current) return;
        const failureStatus = getQuestionnaireFailureStatus(error);
        if (failureStatus === 'aborted') return;
        if (reset) {
          setGenerated(undefined);
          update({ questionnaire: null });
        }
        setQuestionnaireStatus(failureStatus);
      } finally {
        if (requestGeneration === requestGenerationRef.current) {
          activeControllerRef.current = undefined;
        }
      }
    },
    [
      state.customIndustry,
      state.customPurpose,
      state.duration,
      state.industry,
      state.purpose,
      state.supplement,
      update,
    ],
  );

  useEffect(() => {
    requestGenerationRef.current += 1;
    activeControllerRef.current?.abort();
    activeControllerRef.current = undefined;
    if (!state.industry || !state.purpose) {
      setGenerated(undefined);
      setQuestionnaireStatus('idle');
      return;
    }

    if (state.questionnaire?.questions.length) {
      setGenerated(state.questionnaire);
      setQuestionnaireStatus('ready');
      return;
    }

    setGenerated(undefined);
    setQuestionnaireStatus('loading');
    const timer = window.setTimeout(() => {
      void requestQuestionnaire();
    }, 350);
    return () => {
      window.clearTimeout(timer);
      requestGenerationRef.current += 1;
      activeControllerRef.current?.abort();
      activeControllerRef.current = undefined;
    };
  }, [
    requestQuestionnaire,
    state.industry,
    state.purpose,
    state.questionnaire,
  ]);

  const questionOrder = useMemo(() => {
    if (generated?.questions.length) {
      return generated.questions.map((question) => question.id);
    }
    return [];
  }, [generated]);

  const questions = useMemo(() => {
    const { industry, purpose } = state;
    if (!industry || !purpose) return [];
    return questionOrder
      .slice(0, Math.min(questionOrder.length, state.surveyCursor + 1))
      .flatMap((key, index) => {
        const generatedQuestion = generated?.questions.find(
          (question) => question.id === key,
        );
        if (!generatedQuestion) return [];
        return {
          key,
          number: index + 1,
          current:
            index === state.surveyCursor &&
            state.surveyCursor < questionOrder.length,
          question: {
            hint: generatedQuestion.description,
            options: generatedQuestion.options ?? [],
            required: generatedQuestion.required,
            text: generatedQuestion.title,
          },
        };
      });
  }, [
    generated,
    questionOrder,
    state.industry,
    state.purpose,
    state.surveyCursor,
  ]);

  const selectIndustry = (industry: string) =>
    update({
      industry,
      purpose: null,
      customIndustry: industry === 'custom' ? '' : state.customIndustry,
      survey: {},
      surveyOtherAnswers: {},
      surveyCursor: 0,
      questionnaire: null,
      scriptVersions: [],
      scriptBodies: [],
    });

  const selectPurpose = (purpose: string) =>
    update({
      purpose,
      customPurpose: purpose === '__custom' ? '' : state.customPurpose,
      survey: {},
      surveyOtherAnswers: {},
      surveyCursor: 0,
      questionnaire: null,
      scriptVersions: [],
      scriptBodies: [],
    });

  const invalidatePendingQuestionnaireRequest = () => {
    requestGenerationRef.current += 1;
    activeControllerRef.current?.abort();
    activeControllerRef.current = undefined;
    failedRequestRef.current = undefined;
    setQuestionnaireStatus(generated ? 'ready' : 'idle');
  };

  const toggleSurvey = (key: string, option: QuestionnaireOption) => {
    invalidatePendingQuestionnaireRequest();
    const current = state.survey[key] ?? [];
    const value = option.value;
    const wasSelected = current.includes(value);
    const next = current.includes(value)
      ? current.filter((item) => item !== value)
      : [...current, value];
    const keyIndex = questionOrder.indexOf(key);
    const survey = { ...state.survey, [key]: next };
    const surveyOtherAnswers = { ...state.surveyOtherAnswers };
    questionOrder.slice(keyIndex + 1).forEach((laterKey) => {
      delete survey[laterKey];
      delete surveyOtherAnswers[laterKey];
    });
    if (wasSelected && isQuestionnaireOtherOption(option)) {
      delete surveyOtherAnswers[key];
    }
    if (next.length === 0) delete survey[key];
    update({
      survey,
      surveyOtherAnswers,
      surveyCursor:
        keyIndex < state.surveyCursor ? keyIndex : state.surveyCursor,
    });
  };

  const setSurveyText = (key: string, value: string) => {
    invalidatePendingQuestionnaireRequest();
    const keyIndex = questionOrder.indexOf(key);
    const survey = { ...state.survey, [key]: value.trim() ? [value] : [] };
    const surveyOtherAnswers = { ...state.surveyOtherAnswers };
    questionOrder.slice(keyIndex + 1).forEach((laterKey) => {
      delete survey[laterKey];
      delete surveyOtherAnswers[laterKey];
    });
    update({
      survey,
      surveyOtherAnswers,
      surveyCursor: Math.min(state.surveyCursor, keyIndex),
    });
  };

  const setSurveyOtherAnswer = (key: string, value: string) => {
    invalidatePendingQuestionnaireRequest();
    const keyIndex = questionOrder.indexOf(key);
    const survey = { ...state.survey };
    const surveyOtherAnswers = { ...state.surveyOtherAnswers, [key]: value };
    questionOrder.slice(keyIndex + 1).forEach((laterKey) => {
      delete survey[laterKey];
      delete surveyOtherAnswers[laterKey];
    });
    update({
      survey,
      surveyOtherAnswers,
      surveyCursor: Math.min(state.surveyCursor, keyIndex),
    });
  };

  const requestNextQuestion = (key: string) => {
    if (questionnaireStatus !== 'ready' || !generated) {
      return;
    }
    const answeredQuestions = generated.questions.slice(
      0,
      Math.max(0, questionOrder.indexOf(key)) + 1,
    );
    const answerHistory = answeredQuestions.map((question) => {
      const storedValues = state.survey[question.id] ?? [];
      const otherOption = (question.options ?? []).find(
        isQuestionnaireOtherOption,
      );
      const otherAnswer = state.surveyOtherAnswers[question.id]?.trim();
      const selectedValues = storedValues.flatMap((value) =>
        otherOption?.value === value
          ? otherAnswer
            ? [otherAnswer]
            : []
          : [value],
      );
      const optionLabels = new Map(
        (question.options ?? []).map((option) => [option.value, option.label]),
      );
      return {
        questionId: question.id,
        questionTitle: question.title,
        selectedLabels: selectedValues.map(
          (value) => optionLabels.get(value) ?? value,
        ),
        selectedValues,
      };
    });
    void requestQuestionnaire(answerHistory, answeredQuestions, false);
  };

  const retryQuestionnaire = () => {
    const failedRequest = failedRequestRef.current;
    if (!failedRequest) {
      void requestQuestionnaire();
      return;
    }
    void requestQuestionnaire(
      failedRequest.answerHistory,
      failedRequest.existingQuestions,
      failedRequest.reset,
    );
  };

  const purposeLabel =
    state.purpose === '__custom' ? state.customPurpose : state.purpose;
  const industryLabel =
    state.industry === 'custom'
      ? state.customIndustry
      : INDUSTRIES.find((item) => item.id === state.industry)?.name;
  const requiredQuestions = useMemo(
    () =>
      generated
        ? (generated?.questions ?? [])
            .filter((question) => question.required)
            .map((question) => ({
              id: question.id,
              options: question.options ?? [],
            }))
        : [],
    [generated, questionOrder, questionnaireStatus],
  );
  const questionnaireCompleted =
    questionnaireStatus === 'ready' &&
    questionOrder.length > 0 &&
    state.surveyCursor >= questionOrder.length &&
    requiredQuestions.every((question) =>
      hasValidAnswer(
        state.survey[question.id],
        question.options,
        state.surveyOtherAnswers[question.id],
      ),
    );

  return (
    <div className="step-stack">
      <section data-testid="demand-industry">
        <div className="section-head">
          <div>
            <div className="section-title">选择你的行业</div>
            <div className="section-sub">
              行业决定后续问卷方向与文案 Prompt 模板
            </div>
          </div>
        </div>
        <div className="opt-grid">
          {INDUSTRIES.map((item) =>
            item.id === 'custom' && state.industry === 'custom' ? (
              <div className="opt-card is-edit selected" key={item.id}>
                <div className="opt-icon">
                  <StudioIcon name="edit" />
                </div>
                <input
                  className="inline-edit"
                  placeholder="输入行业名…"
                  value={state.customIndustry}
                  onChange={(event) =>
                    update({
                      customIndustry: event.target.value,
                      questionnaire: null,
                      scriptBodies: [],
                      scriptVersions: [],
                      survey: {},
                      surveyCursor: 0,
                      surveyOtherAnswers: {},
                    })
                  }
                />
                <div className="opt-desc">已输入 · 回车确认</div>
              </div>
            ) : (
              <button
                className={`opt-card ${
                  state.industry === item.id ? 'selected' : ''
                }`}
                key={item.id}
                type="button"
                onClick={() => selectIndustry(item.id)}
              >
                <div className="opt-icon">
                  <StudioIcon name={item.icon as StudioIconName} />
                </div>
                <div className="opt-name">{item.name}</div>
                <div className="opt-desc">{item.desc}</div>
              </button>
            ),
          )}
        </div>
      </section>

      <section className={!state.industry ? 'section-disabled' : ''}>
        <div className="section-head">
          <div>
            <div className="section-title">视频用途</div>
            <div className="section-sub">
              {state.industry
                ? '选择主要用途，与行业共同决定文案方向'
                : '请先选择行业'}
            </div>
          </div>
        </div>
        <div className="opt-grid">
          {!state.industry && (
            <div className="section-placeholder">先选择行业后显示对应用途</div>
          )}
          {state.industry &&
            [...(PURPOSES[state.industry] ?? []), '__custom'].map((purpose) =>
              purpose === '__custom' && state.purpose === '__custom' ? (
                <div className="opt-card is-edit selected" key={purpose}>
                  <div className="opt-icon">
                    <StudioIcon name="edit" />
                  </div>
                  <input
                    className="inline-edit"
                    placeholder="输入用途名…"
                    value={state.customPurpose}
                    onChange={(event) =>
                      update({
                        customPurpose: event.target.value,
                        questionnaire: null,
                        scriptBodies: [],
                        scriptVersions: [],
                        survey: {},
                        surveyCursor: 0,
                        surveyOtherAnswers: {},
                      })
                    }
                  />
                  <div className="opt-desc">已输入 · 回车确认</div>
                </div>
              ) : (
                <button
                  className={`opt-card ${
                    state.purpose === purpose ? 'selected' : ''
                  }`}
                  key={purpose}
                  type="button"
                  onClick={() => selectPurpose(purpose)}
                >
                  <div className="opt-icon">
                    <StudioIcon
                      name={purpose === '__custom' ? 'edit' : 'film'}
                    />
                  </div>
                  <div className="opt-name">
                    {purpose === '__custom' ? '自定义用途' : purpose}
                  </div>
                  <div className="opt-desc">点击选择</div>
                </button>
              ),
            )}
        </div>
      </section>

      <section className={!state.purpose ? 'section-disabled' : ''}>
        <div className="section-head">
          <div>
            <div className="section-title">AI 自适应问卷</div>
            <div className="section-sub">
              {state.purpose
                ? questionnaireStatus === 'ready' && generated
                  ? `已参考 ${generated.knowledgeVersionIds.length} 个知识版本 · DeepSeek 生成`
                  : `AI 正根据「${industryLabel} / ${purposeLabel}」及你的回答动态生成问题`
                : '请先选择行业与用途'}
            </div>
          </div>
          {state.purpose && (
            <button
              className="btn btn-ghost btn-sm"
              type="button"
              onClick={() => {
                update({
                  survey: {},
                  surveyOtherAnswers: {},
                  surveyCursor: 0,
                  questionnaire: null,
                });
                void requestQuestionnaire();
              }}
            >
              <StudioIcon name="refresh" /> 重置问卷
            </button>
          )}
        </div>
        <div className="q-list">
          {!state.purpose && (
            <div className="section-placeholder">
              选择用途后将自动生成第一个问题
            </div>
          )}
          {state.purpose && questionnaireStatus === 'loading' && (
            <div className="section-placeholder" role="status">
              正在读取运营知识库并生成专属问卷…
            </div>
          )}
          {state.purpose && questionnaireStatus === 'unauthorized' && (
            <div className="upload-warning" role="alert">
              <StudioIcon name="warning" />
              登录状态已失效，请重新登录后继续。
            </div>
          )}
          {state.purpose && questionnaireStatus === 'forbidden' && (
            <div className="upload-warning" role="alert">
              <StudioIcon name="warning" />
              当前账号无权生成知识问卷。
            </div>
          )}
          {state.purpose && questionnaireStatus === 'error' && (
            <div className="upload-warning" role="alert">
              <StudioIcon name="warning" />
              问卷生成失败，请检查填写内容后重试。
              <button
                className="btn btn-ghost btn-sm"
                type="button"
                onClick={retryQuestionnaire}
              >
                重试
              </button>
            </div>
          )}
          {questions.map(({ key, number, current, question }) => (
            <div className={`q-item ${current ? 'q-current' : ''}`} key={key}>
              <div className="q-head">
                <div className="q-idx">{number}</div>
                <div>
                  <div className="q-text">{question.text}</div>
                  <div className="q-hint">
                    {question.hint} · {question.required ? '必填' : '可选'}
                    {question.options.length ? ' · 可多选' : ''}
                    {current ? ' · 选择后点「下一项」继续' : ''}
                  </div>
                </div>
              </div>
              <div className="q-options">
                {question.options.length ? (
                  <>
                    {question.options.map((option) => (
                      <button
                        className={`q-chip ${
                          state.survey[key]?.includes(option.value)
                            ? 'selected'
                            : ''
                        }`}
                        key={option.value}
                        type="button"
                        onClick={() => toggleSurvey(key, option)}
                      >
                        {option.label}
                      </button>
                    ))}
                    {question.options.some(
                      (option) =>
                        isQuestionnaireOtherOption(option) &&
                        state.survey[key]?.includes(option.value),
                    ) && (
                      <input
                        aria-label={`${question.text}的其他回答`}
                        className="input"
                        placeholder="请输入具体答案"
                        value={state.surveyOtherAnswers[key] ?? ''}
                        onChange={(event) =>
                          setSurveyOtherAnswer(key, event.target.value)
                        }
                      />
                    )}
                  </>
                ) : (
                  <input
                    className="input"
                    placeholder="请输入你的回答"
                    value={state.survey[key]?.[0] ?? ''}
                    onChange={(event) => setSurveyText(key, event.target.value)}
                  />
                )}
              </div>
              {current && (
                <div className="q-action-row">
                  <button
                    className="btn btn-primary btn-sm"
                    type="button"
                    disabled={
                      questionnaireStatus === 'loading-next' ||
                      hasIncompleteOtherAnswer(
                        state.survey[key],
                        question.options,
                        state.surveyOtherAnswers[key],
                      ) ||
                      (question.required &&
                        !hasValidAnswer(
                          state.survey[key],
                          question.options,
                          state.surveyOtherAnswers[key],
                        ))
                    }
                    onClick={() => requestNextQuestion(key)}
                  >
                    {questionnaireStatus === 'loading-next'
                      ? '正在生成下一题…'
                      : '下一项'}{' '}
                    <StudioIcon name="right" />
                  </button>
                </div>
              )}
            </div>
          ))}
          {state.purpose && questionnaireCompleted && (
            <div className="q-done-card">
              <StudioIcon name="check" />
              <div>
                <strong>问卷已完成</strong>
                <div>AI 将综合你的全部回答生成文案</div>
              </div>
            </div>
          )}
        </div>
      </section>

      <div className="demand-bottom-grid">
        <section>
          <div className="section-head">
            <div>
              <div className="section-title">目标时长</div>
              <div className="section-sub">用于控制文案字数与估算生成成本</div>
            </div>
          </div>
          <div className="chip-row">
            {[30, 45, 60, 90, 120].map((duration) => (
              <button
                className={`q-chip ${
                  state.duration === duration ? 'selected' : ''
                }`}
                key={duration}
                type="button"
                onClick={() => update({ duration })}
              >
                {duration} 秒
              </button>
            ))}
          </div>
          <div className="estimate-card">
            <span>预计文案字数</span>
            <b>{Math.round(state.duration * 3.1)} 字</b>
            <span>预计克隆声音时长</span>
            <b>{state.duration} 秒</b>
            <span>预计生成耗时</span>
            <b>8 – 12 分钟</b>
          </div>
        </section>
        <section>
          <div className="section-head">
            <div>
              <div className="section-title">
                补充要求 <span className="muted-normal">（可选）</span>
              </div>
              <div className="section-sub">提供更多事实，文案会更精准</div>
            </div>
          </div>
          <label className="field">
            <span className="field-label">
              产品/服务信息、核心卖点、目标受众、禁止内容等
            </span>
            <textarea
              className="textarea"
              placeholder="例如：纳米抑菌拖把，89元包邮，限时3天活动，目标人群是宝妈"
              value={state.supplement}
              onChange={(event) => update({ supplement: event.target.value })}
            />
          </label>
        </section>
      </div>

      <StepFooter
        step={0}
        nextLabel={isGeneratingScript ? '正在生成文案…' : '生成文案'}
        nextEnabled={Boolean(
          !isGeneratingScript &&
            state.industry &&
            state.purpose &&
            (state.industry !== 'custom' || state.customIndustry.trim()) &&
            (state.purpose !== '__custom' || state.customPurpose.trim()) &&
            questionnaireCompleted,
        )}
        onPrevious={onPrevious}
        onNext={onNext}
        onFinish={onFinish}
      />
    </div>
  );
};

export default DemandStep;
