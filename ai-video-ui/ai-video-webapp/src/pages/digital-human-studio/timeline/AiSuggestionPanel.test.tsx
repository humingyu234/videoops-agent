import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { TimelineTaskDetail } from '@/services/ai-video/creation-timeline/types';
import AiSuggestionPanel from './AiSuggestionPanel';

const task: TimelineTaskDetail = {
  taskId: '90071992547409937' as TimelineTaskDetail['taskId'],
  taskType: 'timeline_fancy_text_suggest',
  resourceType: 'creation_project',
  resourceId: '90071992547409931' as TimelineTaskDetail['resourceId'],
  status: 'success',
  stage: 'completed',
  progress: 100,
  canCancel: false,
  canRetry: false,
  createdAt: '2026-08-08T08:31:00+08:00',
  kind: 'fancy-text',
  result: {
    taskId: '90071992547409937' as TimelineTaskDetail['taskId'],
    suggestions: [
      {
        sourceText: '夏季新品',
        sourceStartOffset: 0,
        sourceEndOffset: 4,
        startMs: 100,
        durationMs: 1_500,
        templateCode: 'keyword_pop',
        xRatio: 0.2,
        yRatio: 0.2,
        primaryColor: '#FFFFFFFF',
        accentColor: '#FFCC00FF',
        reason: '突出主题',
      },
    ],
  },
};

describe('AiSuggestionPanel', () => {
  it('renders only a validated final detail result and waits for an explicit acceptance', () => {
    const onAcceptFancyText = vi.fn();
    const onReject = vi.fn();
    const suggestion = task.result?.suggestions[0];
    if (!suggestion) throw new Error('test setup must contain a suggestion');
    render(
      <AiSuggestionPanel
        sourceText="夏季新品限时优惠"
        task={task}
        onAcceptFancyText={onAcceptFancyText}
        onReject={onReject}
      />,
    );

    expect(onAcceptFancyText).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '接受建议' }));
    expect(onAcceptFancyText).toHaveBeenCalledWith(suggestion);

    fireEvent.click(screen.getByRole('button', { name: '拒绝建议' }));
    expect(onReject).toHaveBeenCalledWith(task.taskId);
  });

  it('fails safely when a candidate is outside the verified script range', () => {
    const onAcceptFancyText = vi.fn();
    render(
      <AiSuggestionPanel
        sourceText="另一个脚本"
        task={task}
        onAcceptFancyText={onAcceptFancyText}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('建议结果不可用');
    expect(onAcceptFancyText).not.toHaveBeenCalled();
  });

  it('fails safely when the C0 result is absent', () => {
    render(
      <AiSuggestionPanel
        sourceText="夏季新品限时优惠"
        task={{
          ...task,
          result: undefined,
        }}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('建议结果不可用');
  });

  it('fails safely when aligned subtitle timing is invalid', () => {
    render(
      <AiSuggestionPanel
        sourceText="夏季新品"
        task={
          {
            ...task,
            kind: 'subtitle-alignment',
            result: {
              sourceType: 'trusted_cue',
              subtitles: [
                {
                  displayText: '夏季新品',
                  endMs: 1_000,
                  sourceEndOffset: 4,
                  sourceStartOffset: 0,
                  startMs: -1,
                },
              ],
              taskId: task.taskId,
            },
            taskType: 'timeline_subtitle_align',
          } as TimelineTaskDetail
        }
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('建议结果不可用');
  });
});
