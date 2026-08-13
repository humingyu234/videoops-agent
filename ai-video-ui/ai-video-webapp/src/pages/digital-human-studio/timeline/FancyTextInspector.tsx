import { Input, InputNumber, Segmented } from 'antd';
import { useState } from 'react';
import type { FancyTextElement } from '@/services/ai-video/creation-timeline/types';
import {
  FANCY_TEXT_TEMPLATES,
  fixedTimelineFontFor,
  fontMatchesRegistry,
  type TimelineFontStatus,
  validateFancyTextElement,
} from './fancyTextTemplates';

export interface FancyTextInspectorProps {
  element: FancyTextElement;
  fontStatus?: TimelineFontStatus;
  onChange?: (element: FancyTextElement) => void;
}

function numberValue(value: number | string | null): number | undefined {
  if (value === null) return undefined;
  const parsed = typeof value === 'string' ? Number(value) : value;
  return Number.isFinite(parsed) ? parsed : undefined;
}

function hasVerifiedFont(
  element: FancyTextElement,
  fontStatus: TimelineFontStatus | undefined,
): boolean {
  return (
    fontStatus?.status === 'ready' &&
    fontMatchesRegistry(element) &&
    fontMatchesRegistry(element, fontStatus.fonts)
  );
}

export default function FancyTextInspector({
  element,
  fontStatus,
  onChange,
}: FancyTextInspectorProps) {
  const [error, setError] = useState<string>();
  const fontInvalid = !hasVerifiedFont(element, fontStatus);

  const publish = (candidate: FancyTextElement) => {
    if (!hasVerifiedFont(candidate, fontStatus)) {
      setError('字体不可用，已阻止保存和合成。');
      return;
    }
    const validationMessage = validateFancyTextElement(candidate);
    if (validationMessage) {
      setError(validationMessage);
      return;
    }
    setError(undefined);
    onChange?.(candidate);
  };

  const updateDuration = (
    key: 'enterDurationMs' | 'exitDurationMs',
    value: number | string | null,
  ) => {
    const nextValue = numberValue(value);
    if (nextValue === undefined) return;
    publish({ ...element, [key]: Math.round(nextValue) });
  };

  return (
    <section aria-label="花字属性">
      {fontInvalid && <p role="alert">字体不可用，已阻止保存和合成。</p>}
      <Input
        aria-label="花字文字"
        maxLength={128}
        value={element.text}
        onChange={(event) => publish({ ...element, text: event.target.value })}
      />
      <Segmented
        aria-label="花字模板"
        options={FANCY_TEXT_TEMPLATES.map((template) => ({
          label: template.label,
          value: template.code,
        }))}
        value={element.templateCode}
        onChange={(value) =>
          publish({
            ...element,
            templateCode: value as FancyTextElement['templateCode'],
          })
        }
      />
      <Segmented
        aria-label="花字字体"
        options={[
          { label: '思源黑体', value: 'noto_sans_cjk_sc_regular' },
          { label: '思源宋体', value: 'noto_serif_cjk_sc_regular' },
        ]}
        value={element.fontCode}
        onChange={(value) => {
          const font = fixedTimelineFontFor(
            value as FancyTextElement['fontCode'],
          );
          publish({
            ...element,
            fontCode: font.fontCode,
            fontVersion: font.version,
            fontSha256: font.sha256,
          });
        }}
      />
      <Input
        aria-label="文字颜色"
        value={element.color}
        onChange={(event) => publish({ ...element, color: event.target.value })}
      />
      <Input
        aria-label="强调颜色"
        value={element.accentColor}
        onChange={(event) =>
          publish({ ...element, accentColor: event.target.value })
        }
      />
      <Segmented
        aria-label="动画强度"
        options={[
          { label: '轻微', value: 'subtle' },
          { label: '正常', value: 'normal' },
          { label: '强烈', value: 'strong' },
        ]}
        value={element.animationIntensity}
        onChange={(value) =>
          publish({
            ...element,
            animationIntensity: value as FancyTextElement['animationIntensity'],
          })
        }
      />
      <InputNumber
        aria-label="入场时长"
        precision={0}
        value={element.enterDurationMs}
        onChange={(value) => updateDuration('enterDurationMs', value)}
      />
      <InputNumber
        aria-label="退场时长"
        precision={0}
        value={element.exitDurationMs}
        onChange={(value) => updateDuration('exitDurationMs', value)}
      />
      {error && !fontInvalid && <p role="alert">{error}</p>}
    </section>
  );
}
