import { Alert, Button, Card, Empty, List, Space, Tag, Typography } from 'antd';
import type {
  TimelineAlignedSubtitle,
  TimelineFancyTextSuggestion,
  TimelineImagePromptSuggestion,
  TimelineTaskDetail,
} from '@/services/ai-video/creation-timeline/types';
import {
  getFancyTextTemplate,
  isValidTimelineColor,
} from './fancyTextTemplates';
import { validateSubtitleIntegrity } from './subtitle';

export type AiSuggestionPanelProps = {
  onAcceptFancyText?: (suggestion: TimelineFancyTextSuggestion) => void;
  onAcceptImagePrompt?: (suggestion: TimelineImagePromptSuggestion) => void;
  onAcceptSubtitle?: (suggestion: TimelineAlignedSubtitle) => void;
  onReject?: (taskId: string) => void;
  sourceText?: string;
  task?: TimelineTaskDetail;
};

type SafeSuggestion = {
  accept: () => void;
  detail: string;
  key: string;
  title: string;
};

function codePoints(value: string): string[] {
  return Array.from(value.normalize('NFC'));
}

export function validateFancyTextSuggestion(
  suggestion: TimelineFancyTextSuggestion,
  sourceText: string | undefined,
): string | undefined {
  if (!sourceText) return '缺少用于核验建议的项目脚本';
  const source = codePoints(sourceText);
  if (
    !Number.isInteger(suggestion.sourceStartOffset) ||
    !Number.isInteger(suggestion.sourceEndOffset) ||
    suggestion.sourceStartOffset < 0 ||
    suggestion.sourceEndOffset <= suggestion.sourceStartOffset ||
    suggestion.sourceEndOffset > source.length
  ) {
    return '建议的脚本范围无效';
  }
  const selected = source
    .slice(suggestion.sourceStartOffset, suggestion.sourceEndOffset)
    .join('');
  if (selected !== suggestion.sourceText.normalize('NFC')) {
    return '建议关键词不属于项目脚本范围';
  }
  try {
    getFancyTextTemplate(suggestion.templateCode);
  } catch {
    return '建议包含未知花字模板';
  }
  if (
    !isValidTimelineColor(suggestion.primaryColor) ||
    !isValidTimelineColor(suggestion.accentColor) ||
    !Number.isInteger(suggestion.startMs) ||
    !Number.isInteger(suggestion.durationMs) ||
    suggestion.startMs < 0 ||
    suggestion.startMs > 119_999 ||
    suggestion.durationMs < 1 ||
    suggestion.durationMs > 120_000 ||
    suggestion.xRatio < 0 ||
    suggestion.xRatio > 1 ||
    suggestion.yRatio < 0 ||
    suggestion.yRatio > 1
  ) {
    return '建议参数不符合时间轴契约';
  }
  return undefined;
}

export function validateAlignedSubtitleSuggestion(
  suggestion: TimelineAlignedSubtitle,
): string | undefined {
  if (
    !Number.isInteger(suggestion.startMs) ||
    !Number.isInteger(suggestion.endMs) ||
    suggestion.startMs < 0 ||
    suggestion.endMs <= suggestion.startMs
  ) {
    return '建议的字幕时间范围无效';
  }
  return undefined;
}

function safeSuggestions(
  task: TimelineTaskDetail,
  sourceText: string | undefined,
  callbacks: Pick<
    AiSuggestionPanelProps,
    'onAcceptFancyText' | 'onAcceptImagePrompt' | 'onAcceptSubtitle'
  >,
): SafeSuggestion[] | undefined {
  if (task.status !== 'success') return undefined;
  switch (task.kind) {
    case 'image-prompt':
      if (!task.result) return undefined;
      return task.result.suggestions.map((suggestion, index) => ({
        accept: () => callbacks.onAcceptImagePrompt?.(suggestion),
        detail: suggestion.reason,
        key: `image-prompt-${index}`,
        title: suggestion.prompt,
      }));
    case 'fancy-text':
      if (
        !task.result ||
        task.result.suggestions.some(
          (suggestion) =>
            validateFancyTextSuggestion(suggestion, sourceText) !== undefined,
        )
      ) {
        return undefined;
      }
      return task.result.suggestions.map((suggestion, index) => ({
        accept: () => callbacks.onAcceptFancyText?.(suggestion),
        detail: suggestion.reason,
        key: `fancy-text-${index}`,
        title: suggestion.sourceText,
      }));
    case 'subtitle-alignment':
      if (
        !task.result ||
        !sourceText ||
        task.result.subtitles.length === 0 ||
        !validateSubtitleIntegrity(sourceText, task.result.subtitles).valid ||
        task.result.subtitles.some(
          (suggestion) =>
            validateAlignedSubtitleSuggestion(suggestion) !== undefined,
        )
      ) {
        return undefined;
      }
      return task.result.subtitles.map((suggestion, index) => {
        return {
          accept: () => callbacks.onAcceptSubtitle?.(suggestion),
          detail: suggestion.displayText,
          key: `subtitle-${index}`,
          title: `字幕 ${index + 1}`,
        };
      });
    default:
      return undefined;
  }
}

export default function AiSuggestionPanel({
  onAcceptFancyText,
  onAcceptImagePrompt,
  onAcceptSubtitle,
  onReject,
  sourceText,
  task,
}: AiSuggestionPanelProps) {
  if (!task) {
    return <Empty description="暂无 AI 建议" />;
  }

  let suggestions: SafeSuggestion[] | undefined;
  try {
    suggestions = safeSuggestions(task, sourceText, {
      onAcceptFancyText,
      onAcceptImagePrompt,
      onAcceptSubtitle,
    });
  } catch {
    suggestions = undefined;
  }

  if (!suggestions) {
    return <Alert message="建议结果不可用" showIcon type="warning" />;
  }

  if (suggestions.length === 0) {
    return <Empty description="AI 未返回可采用的建议" />;
  }

  return (
    <Card size="small" title="AI 建议">
      <List
        dataSource={suggestions}
        rowKey="key"
        renderItem={(suggestion) => (
          <List.Item
            actions={[
              <Button key="accept" type="primary" onClick={suggestion.accept}>
                接受建议
              </Button>,
              <Button key="reject" onClick={() => onReject?.(task.taskId)}>
                拒绝建议
              </Button>,
            ]}
          >
            <List.Item.Meta
              description={suggestion.detail}
              title={
                <Space>
                  <Typography.Text>{suggestion.title}</Typography.Text>
                  <Tag>仅建议</Tag>
                </Space>
              }
            />
          </List.Item>
        )}
      />
    </Card>
  );
}
