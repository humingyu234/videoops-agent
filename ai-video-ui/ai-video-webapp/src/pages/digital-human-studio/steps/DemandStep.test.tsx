import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { useCallback, useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/services/ai-video/core/errors';
import type { GeneratedQuestionnaire } from '@/services/ai-video/questionnaire/types';
import { initialStudioState, type StudioState } from '../model';
import DemandStep from './DemandStep';

const { generateQuestionnaire } = vi.hoisted(() => ({
  generateQuestionnaire: vi.fn(),
}));

vi.mock('@/services/ai-video/questionnaire/api', () => ({
  questionnaireApi: { generate: generateQuestionnaire },
}));

const generatedQuestionnaire = (
  title = '这条视频最想影响哪类人？',
  required = true,
): GeneratedQuestionnaire => ({
  knowledgeHash: 'knowledge-hash',
  knowledgeVersionIds: ['101', '102'],
  modelMode: 'deepseek',
  questions: [
    {
      description: '用于选择最合适的知识模板',
      id: 'target-audience',
      options: [
        { label: '宝妈', value: 'mothers' },
        { label: '年轻白领', value: 'workers' },
      ],
      required,
      title,
    },
  ],
});

const questionnaireWithOther = (): GeneratedQuestionnaire => ({
  ...generatedQuestionnaire(),
  questions: [
    {
      description: '用于选择最合适的知识模板',
      id: 'target-audience',
      options: [
        { label: '宝妈', value: 'mothers' },
        { label: '其他', value: 'other' },
      ],
      required: true,
      title: '这条视频最想影响哪类人？',
    },
  ],
});

const selectedDirection: Partial<StudioState> = {
  industry: 'ecommerce',
  purpose: '获客咨询',
  supplement: '89元家用拖把，面向宝妈',
};

function DemandHarness({
  initialState,
  onNext = vi.fn(),
}: {
  initialState?: Partial<StudioState>;
  onNext?: () => void;
}) {
  const [state, setState] = useState<StudioState>({
    ...initialStudioState,
    ...selectedDirection,
    ...initialState,
  });
  const update = useCallback((patch: Partial<StudioState>) => {
    setState((current) => ({ ...current, ...patch }));
  }, []);

  return (
    <>
      <DemandStep
        state={state}
        update={update}
        onFinish={vi.fn()}
        onNext={onNext}
        onPrevious={vi.fn()}
      />
      <output data-testid="survey-state">{JSON.stringify(state.survey)}</output>
    </>
  );
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

describe('DemandStep knowledge questionnaire', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    generateQuestionnaire.mockResolvedValue(generatedQuestionnaire());
  });

  it('loads and renders questions generated from published knowledge', async () => {
    render(<DemandHarness />);

    await waitFor(() => expect(generateQuestionnaire).toHaveBeenCalled());
    expect(await screen.findByText('这条视频最想影响哪类人？')).toBeVisible();
    expect(screen.getByText(/已参考 2 个知识版本/)).toBeVisible();
    expect(generateQuestionnaire).toHaveBeenCalledWith(
      expect.objectContaining({
        demandText: '89元家用拖把，面向宝妈',
        durationSeconds: 60,
        industryCode: 'ecommerce',
        purposeCode: '获客咨询',
      }),
      expect.objectContaining({ signal: expect.anything() }),
    );
  });

  it('ignores an older response that arrives after the current response', async () => {
    const first = deferred<GeneratedQuestionnaire>();
    const second = deferred<GeneratedQuestionnaire>();
    generateQuestionnaire
      .mockReset()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const update = vi.fn();
    const props = {
      onFinish: vi.fn(),
      onNext: vi.fn(),
      onPrevious: vi.fn(),
      update,
    };
    const { rerender } = render(
      <DemandStep
        {...props}
        state={{
          ...initialStudioState,
          ...selectedDirection,
          supplement: '第一份需求',
        }}
      />,
    );

    await waitFor(() => expect(generateQuestionnaire).toHaveBeenCalledTimes(1));
    rerender(
      <DemandStep
        {...props}
        state={{
          ...initialStudioState,
          ...selectedDirection,
          supplement: '第二份需求',
        }}
      />,
    );
    await waitFor(() => expect(generateQuestionnaire).toHaveBeenCalledTimes(2));

    await act(async () => {
      second.resolve(generatedQuestionnaire('第二份问卷'));
      await second.promise;
    });
    expect(screen.getByText('第二份问卷')).toBeVisible();

    await act(async () => {
      first.resolve(generatedQuestionnaire('已过期的第一份问卷'));
      await first.promise;
    });
    expect(screen.queryByText('已过期的第一份问卷')).not.toBeInTheDocument();
    expect(screen.getByText('第二份问卷')).toBeVisible();
  });

  it('preserves an existing answer when the current questionnaire refreshes', async () => {
    render(
      <DemandHarness
        initialState={{ survey: { 'target-audience': ['mothers'] } }}
      />,
    );

    expect(await screen.findByRole('button', { name: '宝妈' })).toHaveClass(
      'selected',
    );
    expect(screen.getByTestId('survey-state')).toHaveTextContent('mothers');
  });

  it('does not count a preserved value that is absent from the current options', async () => {
    render(
      <DemandHarness
        initialState={{
          survey: { 'target-audience': ['retired-option'] },
          surveyCursor: 1,
        }}
      />,
    );

    expect(await screen.findByText('这条视频最想影响哪类人？')).toBeVisible();
    expect(screen.getByRole('button', { name: /下一项/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /生成文案/ })).toBeDisabled();
  });

  it('shows option labels but stores their stable values', async () => {
    render(<DemandHarness />);

    fireEvent.click(await screen.findByRole('button', { name: '宝妈' }));

    expect(screen.getByTestId('survey-state')).toHaveTextContent('mothers');
    expect(screen.getByTestId('survey-state')).not.toHaveTextContent('宝妈');
  });

  it('requires a non-empty manual answer after selecting the other option', async () => {
    generateQuestionnaire.mockResolvedValue(questionnaireWithOther());
    render(<DemandHarness />);

    fireEvent.click(await screen.findByRole('button', { name: '其他' }));

    const otherAnswer = screen.getByRole('textbox', { name: /其他回答/ });
    const nextQuestionButton = screen.getByRole('button', { name: /下一项/ });
    expect(otherAnswer).toBeVisible();
    expect(nextQuestionButton).toBeDisabled();

    fireEvent.change(otherAnswer, { target: { value: '  ' } });
    expect(nextQuestionButton).toBeDisabled();

    fireEvent.change(otherAnswer, { target: { value: '自由职业者' } });
    expect(nextQuestionButton).toBeEnabled();
  });

  it('uses the manual other answer as the next question input', async () => {
    generateQuestionnaire
      .mockReset()
      .mockResolvedValueOnce(questionnaireWithOther())
      .mockResolvedValueOnce(generatedQuestionnaire('下一道动态问题'));
    render(<DemandHarness />);

    fireEvent.click(await screen.findByRole('button', { name: '其他' }));
    fireEvent.change(screen.getByRole('textbox', { name: /其他回答/ }), {
      target: { value: '自由职业者' },
    });
    fireEvent.click(screen.getByRole('button', { name: /下一项/ }));

    await waitFor(() => expect(generateQuestionnaire).toHaveBeenCalledTimes(2));
    expect(generateQuestionnaire).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        answerHistory: [
          {
            questionId: 'target-audience',
            questionTitle: '这条视频最想影响哪类人？',
            selectedLabels: ['自由职业者'],
            selectedValues: ['自由职业者'],
          },
        ],
        answeredSlots: ['target-audience'],
      }),
      expect.objectContaining({ signal: expect.anything() }),
    );
  });

  it('requests the next dependent question with the complete answer history', async () => {
    generateQuestionnaire
      .mockReset()
      .mockResolvedValueOnce(generatedQuestionnaire())
      .mockResolvedValueOnce({
        ...generatedQuestionnaire('这次最想让用户记住什么？'),
        questions: [
          {
            description: '必须结合上一题选择继续追问',
            id: 'core-message',
            options: [
              { label: '省时省力', value: 'save-time' },
              { label: '价格划算', value: 'good-value' },
            ],
            required: true,
            title: '这次最想让用户记住什么？',
          },
        ],
      });
    render(<DemandHarness />);

    fireEvent.click(await screen.findByRole('button', { name: '宝妈' }));
    fireEvent.click(screen.getByRole('button', { name: /下一项/ }));

    await waitFor(() => expect(generateQuestionnaire).toHaveBeenCalledTimes(2));
    expect(generateQuestionnaire).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        answerHistory: [
          {
            questionId: 'target-audience',
            questionTitle: '这条视频最想影响哪类人？',
            selectedLabels: ['宝妈'],
            selectedValues: ['mothers'],
          },
        ],
        answeredSlots: ['target-audience'],
      }),
      expect.objectContaining({ signal: expect.anything() }),
    );
    expect(await screen.findByText('这次最想让用户记住什么？')).toBeVisible();
  });

  it('treats a repeated question id as an invalid response instead of questionnaire completion', async () => {
    generateQuestionnaire
      .mockReset()
      .mockResolvedValueOnce(generatedQuestionnaire())
      .mockResolvedValueOnce(
        generatedQuestionnaire('模型错误返回的重复问题'),
      );
    render(<DemandHarness />);

    fireEvent.click(await screen.findByRole('button', { name: '宝妈' }));
    fireEvent.click(screen.getByRole('button', { name: /下一项/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent('问卷生成失败');
    expect(screen.queryByText('问卷已完成')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /生成文案/ })).toBeDisabled();
  });

  it('replaces stale downstream questions after an earlier multi-select answer changes', async () => {
    const staleNextQuestion: GeneratedQuestionnaire = {
      ...generatedQuestionnaire(),
      questions: [
        {
          description: '只面向宝妈',
          id: 'core-message',
          options: [{ label: '省时省力', value: 'save-time' }],
          required: true,
          title: '旧的核心卖点问题',
        },
      ],
    };
    const refreshedNextQuestion: GeneratedQuestionnaire = {
      ...generatedQuestionnaire(),
      questions: [
        {
          description: '同时面向宝妈和年轻白领',
          id: 'core-message',
          options: [{ label: '组合卖点', value: 'combined-value' }],
          required: true,
          title: '重新生成的核心卖点问题',
        },
      ],
    };
    generateQuestionnaire
      .mockReset()
      .mockResolvedValueOnce(generatedQuestionnaire())
      .mockResolvedValueOnce(staleNextQuestion)
      .mockResolvedValueOnce(refreshedNextQuestion);
    render(<DemandHarness />);

    fireEvent.click(await screen.findByRole('button', { name: '宝妈' }));
    fireEvent.click(screen.getByRole('button', { name: /下一项/ }));
    expect(await screen.findByText('旧的核心卖点问题')).toBeVisible();

    fireEvent.click(screen.getByRole('button', { name: '年轻白领' }));
    expect(screen.queryByText('旧的核心卖点问题')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /下一项/ }));

    await waitFor(() => expect(generateQuestionnaire).toHaveBeenCalledTimes(3));
    expect(generateQuestionnaire).toHaveBeenNthCalledWith(
      3,
      expect.objectContaining({
        answerHistory: [
          expect.objectContaining({
            questionId: 'target-audience',
            selectedLabels: ['宝妈', '年轻白领'],
            selectedValues: ['mothers', 'workers'],
          }),
        ],
      }),
      expect.objectContaining({ signal: expect.anything() }),
    );
    expect(
      await screen.findByText('重新生成的核心卖点问题'),
    ).toBeVisible();
    expect(screen.getByText(/同时面向宝妈和年轻白领/)).toBeVisible();
    expect(screen.queryByText('只面向宝妈')).not.toBeInTheDocument();
  });

  it('clears the previous questionnaire immediately when the purpose changes', async () => {
    generateQuestionnaire
      .mockReset()
      .mockResolvedValueOnce(generatedQuestionnaire('旧用途问题'))
      .mockResolvedValueOnce(generatedQuestionnaire('新用途问题'));
    render(<DemandHarness />);

    expect(await screen.findByText('旧用途问题')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '宝妈' }));
    fireEvent.click(
      screen.getByRole('button', { name: /产品介绍 点击选择/ }),
    );

    expect(screen.queryByText('旧用途问题')).not.toBeInTheDocument();
    expect(screen.getByTestId('survey-state')).toHaveTextContent('{}');
    expect(await screen.findByText('新用途问题')).toBeVisible();
    expect(generateQuestionnaire).toHaveBeenLastCalledWith(
      expect.objectContaining({ purposeCode: '产品介绍' }),
      expect.objectContaining({ signal: expect.anything() }),
    );
  });

  it('clears the questionnaire when the industry changes and regenerates after choosing a new purpose', async () => {
    generateQuestionnaire
      .mockReset()
      .mockResolvedValueOnce(generatedQuestionnaire('旧行业问题'))
      .mockResolvedValueOnce(generatedQuestionnaire('新行业问题'));
    render(<DemandHarness />);

    expect(await screen.findByText('旧行业问题')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '宝妈' }));
    fireEvent.click(screen.getByRole('button', { name: /教育培训/ }));

    expect(screen.queryByText('旧行业问题')).not.toBeInTheDocument();
    expect(screen.getByTestId('survey-state')).toHaveTextContent('{}');
    fireEvent.click(
      screen.getByRole('button', { name: /课程讲解 点击选择/ }),
    );

    expect(await screen.findByText('新行业问题')).toBeVisible();
    expect(generateQuestionnaire).toHaveBeenLastCalledWith(
      expect.objectContaining({
        industryCode: 'education',
        purposeCode: '课程讲解',
      }),
      expect.objectContaining({ signal: expect.anything() }),
    );
  });

  it('keeps answered questions visible while the next question is loading', async () => {
    const nextQuestion = deferred<GeneratedQuestionnaire>();
    generateQuestionnaire
      .mockReset()
      .mockResolvedValueOnce(generatedQuestionnaire())
      .mockReturnValueOnce(nextQuestion.promise);
    render(<DemandHarness />);

    fireEvent.click(await screen.findByRole('button', { name: '宝妈' }));
    fireEvent.click(screen.getByRole('button', { name: /下一项/ }));

    await waitFor(() => expect(generateQuestionnaire).toHaveBeenCalledTimes(2));
    expect(screen.getByText('这条视频最想影响哪类人？')).toBeVisible();
    expect(screen.getByRole('button', { name: '宝妈' })).toHaveClass(
      'selected',
    );

    await act(async () => {
      nextQuestion.resolve(generatedQuestionnaire('下一道动态问题'));
      await nextQuestion.promise;
    });
  });

  it('cancels and ignores a pending next-question response when an upstream answer changes', async () => {
    const staleNextQuestion = deferred<GeneratedQuestionnaire>();
    generateQuestionnaire
      .mockReset()
      .mockResolvedValueOnce(generatedQuestionnaire())
      .mockReturnValueOnce(staleNextQuestion.promise)
      .mockResolvedValueOnce({
        ...generatedQuestionnaire(),
        questions: [
          {
            description: '同时面向两类人群',
            id: 'refreshed-core-message',
            options: [{ label: '组合卖点', value: 'combined-value' }],
            required: true,
            title: '基于新答案生成的问题',
          },
        ],
      });
    render(<DemandHarness />);

    fireEvent.click(await screen.findByRole('button', { name: '宝妈' }));
    fireEvent.click(screen.getByRole('button', { name: /下一项/ }));
    await waitFor(() => expect(generateQuestionnaire).toHaveBeenCalledTimes(2));
    const staleSignal = generateQuestionnaire.mock.calls[1]?.[1]
      ?.signal as AbortSignal;

    fireEvent.click(screen.getByRole('button', { name: '年轻白领' }));
    expect(staleSignal.aborted).toBe(true);

    await act(async () => {
      staleNextQuestion.resolve({
        ...generatedQuestionnaire(),
        questions: [
          {
            description: '只使用旧回答',
            id: 'stale-core-message',
            options: [{ label: '旧卖点', value: 'stale-value' }],
            required: true,
            title: '不应落地的旧问题',
          },
        ],
      });
      await staleNextQuestion.promise;
    });
    expect(screen.queryByText('不应落地的旧问题')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /下一项/ }));
    await waitFor(() => expect(generateQuestionnaire).toHaveBeenCalledTimes(3));
    expect(generateQuestionnaire).toHaveBeenNthCalledWith(
      3,
      expect.objectContaining({
        answerHistory: [
          expect.objectContaining({
            selectedLabels: ['宝妈', '年轻白领'],
            selectedValues: ['mothers', 'workers'],
          }),
        ],
      }),
      expect.objectContaining({ signal: expect.anything() }),
    );
    expect(await screen.findByText('基于新答案生成的问题')).toBeVisible();
  });

  it('retries a failed next-question request with the same complete answer history', async () => {
    generateQuestionnaire
      .mockReset()
      .mockResolvedValueOnce(generatedQuestionnaire())
      .mockRejectedValueOnce(new Error('provider timeout'))
      .mockResolvedValueOnce({
        ...generatedQuestionnaire(),
        questions: [
          {
            description: '继续使用上一题答案追问',
            id: 'core-message',
            options: [{ label: '省时省力', value: 'save-time' }],
            required: true,
            title: '重试后生成的下一题',
          },
        ],
      });
    render(<DemandHarness />);

    fireEvent.click(await screen.findByRole('button', { name: '宝妈' }));
    fireEvent.click(screen.getByRole('button', { name: /下一项/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent('问卷生成失败');
    fireEvent.click(screen.getByRole('button', { name: '重试' }));

    await waitFor(() => expect(generateQuestionnaire).toHaveBeenCalledTimes(3));
    expect(generateQuestionnaire).toHaveBeenNthCalledWith(
      3,
      expect.objectContaining({
        answerHistory: [
          {
            questionId: 'target-audience',
            questionTitle: '这条视频最想影响哪类人？',
            selectedLabels: ['宝妈'],
            selectedValues: ['mothers'],
          },
        ],
        answeredSlots: ['target-audience'],
      }),
      expect.objectContaining({ signal: expect.anything() }),
    );
    expect(await screen.findByText('重试后生成的下一题')).toBeVisible();
    expect(screen.getByRole('button', { name: '宝妈' })).toHaveClass('selected');
  });

  it('blocks script generation until every required question is completed', async () => {
    generateQuestionnaire
      .mockReset()
      .mockResolvedValueOnce(generatedQuestionnaire())
      .mockResolvedValueOnce({
        ...generatedQuestionnaire(),
        questions: [],
      });
    render(<DemandHarness />);

    const generateButton = screen.getByRole('button', { name: /生成文案/ });
    expect(await screen.findByText('这条视频最想影响哪类人？')).toBeVisible();
    expect(generateButton).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '宝妈' }));
    expect(generateButton).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: /下一项/ }));

    await waitFor(() => expect(generateButton).toBeEnabled());
  });

  it('allows an optional generated question to be skipped', async () => {
    generateQuestionnaire
      .mockReset()
      .mockResolvedValueOnce(generatedQuestionnaire('可选问题', false))
      .mockResolvedValueOnce({
        ...generatedQuestionnaire('可选问题', false),
        questions: [],
      });
    generateQuestionnaire.mockResolvedValue(
      generatedQuestionnaire('可选问题', false),
    );
    render(<DemandHarness />);

    expect(await screen.findByText('可选问题')).toBeVisible();
    const nextQuestionButton = screen.getByRole('button', { name: /下一项/ });
    expect(nextQuestionButton).toBeEnabled();
    fireEvent.click(nextQuestionButton);

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /生成文案/ })).toBeEnabled(),
    );
  });

  it('shows a permission state for 403 without falling back to built-in questions', async () => {
    generateQuestionnaire.mockRejectedValue(
      new ApiError({ code: 403, msg: 'forbidden', status: 403 }),
    );
    render(<DemandHarness />);

    expect(await screen.findByRole('alert')).toHaveTextContent('无权');
    expect(screen.queryByText(/已切换内置问卷/)).not.toBeInTheDocument();
    expect(
      screen.queryByText('你的目标客户主要是哪类人群？'),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('问卷已完成')).not.toBeInTheDocument();
  });

  it('shows a login state for 401 without falling back to built-in questions', async () => {
    generateQuestionnaire.mockRejectedValue(
      new ApiError({ code: 401, msg: 'expired', status: 401 }),
    );
    render(<DemandHarness />);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '登录状态已失效',
    );
    expect(screen.queryByText(/已切换内置问卷/)).not.toBeInTheDocument();
  });

  it('shows an error instead of a built-in questionnaire when generation fails', async () => {
    generateQuestionnaire.mockRejectedValue(new Error('provider timeout'));
    render(<DemandHarness />);

    expect(await screen.findByRole('alert')).toHaveTextContent('问卷生成失败');
    expect(screen.queryByText(/已切换内置问卷/)).not.toBeInTheDocument();
    expect(
      screen.queryByText('你的目标客户主要是哪类人群？'),
    ).not.toBeInTheDocument();
  });

  it('rejects a knowledge fallback questionnaire instead of presenting it as DeepSeek output', async () => {
    generateQuestionnaire.mockResolvedValue({
      ...generatedQuestionnaire('不应展示的知识兜底问题'),
      modelMode: 'knowledge-fallback',
    });
    render(<DemandHarness />);

    expect(await screen.findByRole('alert')).toHaveTextContent('问卷生成失败');
    expect(
      screen.queryByText('不应展示的知识兜底问题'),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/DeepSeek 生成/)).not.toBeInTheDocument();
  });

  it('restores the saved questionnaire snapshot without generating it again', async () => {
    render(
      <DemandHarness
        initialState={{
          questionnaire: generatedQuestionnaire(),
          survey: { 'target-audience': ['mothers'] },
          surveyCursor: 1,
        }}
      />,
    );

    expect(await screen.findByText('这条视频最想影响哪类人？')).toBeVisible();
    expect(screen.getByRole('button', { name: '宝妈' })).toHaveClass(
      'selected',
    );
    expect(generateQuestionnaire).not.toHaveBeenCalled();
  });
});
