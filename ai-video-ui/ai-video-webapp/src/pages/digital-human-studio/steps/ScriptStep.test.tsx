import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { initialStudioState } from '../model';
import ScriptStep from './ScriptStep';

describe('ScriptStep generated scripts', () => {
  it('renders only DeepSeek versions and the persisted dynamic questionnaire recap', () => {
    render(
      <ScriptStep
        state={{
          ...initialStudioState,
          industry: 'education',
          purpose: '课程讲解',
          questionnaire: {
            knowledgeHash: 'knowledge-hash',
            knowledgeVersionIds: ['101'],
            modelMode: 'deepseek',
            questions: [
              {
                description: '请补充具体客户',
                id: 'target-customer',
                options: [{ label: '其它', value: 'custom_other' }],
                required: true,
                title: '目标客户是谁？',
              },
            ],
          },
          scriptBodies: ['这是 DeepSeek 生成的课程讲解文案。'],
          scriptVersions: [
            {
              body: '这是 DeepSeek 生成的课程讲解文案。',
              durationSeconds: 45,
              title: '清晰讲解版',
            },
          ],
          survey: { 'target-customer': ['custom_other'] },
          surveyOtherAnswers: { 'target-customer': '企业采购负责人' },
        }}
        update={vi.fn()}
        onFinish={vi.fn()}
        onNext={vi.fn()}
        onPrevious={vi.fn()}
        onToast={vi.fn()}
      />,
    );

    expect(screen.getAllByRole('button', { name: /版本/ })).toHaveLength(1);
    expect(screen.getByRole('button', { name: /清晰讲解版/ })).toBeVisible();
    expect(screen.getByText('45 秒')).toBeVisible();
    expect(screen.getByText('目标客户是谁？')).toBeVisible();
    expect(screen.getByText('企业采购负责人')).toBeVisible();
    expect(screen.getByRole('textbox')).toHaveValue(
      '这是 DeepSeek 生成的课程讲解文案。',
    );
  });

  it('does not allow continuing without a generated script', () => {
    render(
      <ScriptStep
        state={initialStudioState}
        update={vi.fn()}
        onFinish={vi.fn()}
        onNext={vi.fn()}
        onPrevious={vi.fn()}
        onToast={vi.fn()}
      />,
    );

    expect(
      screen.getByRole('button', { name: /确认文案，去选形象/ }),
    ).toBeDisabled();
  });
});
