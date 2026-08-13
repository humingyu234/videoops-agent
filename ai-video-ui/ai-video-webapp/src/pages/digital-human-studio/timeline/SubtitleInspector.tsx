import { Input, InputNumber, Segmented, Switch } from 'antd';
import { useState } from 'react';
import type { SubtitleElement } from '@/services/ai-video/creation-timeline/types';
import {
  type SubtitleFontCode,
  sanitizeSubtitleElement,
  validateSubtitleStyle,
} from './subtitle';
import { fixedTimelineFontFor } from './fancyTextTemplates';

export interface SubtitleInspectorProps {
  element: SubtitleElement;
  onChange?: (element: SubtitleElement) => void;
}

function numberValue(value: number | string | null): number | undefined {
  if (value === null) return undefined;
  const parsed = typeof value === 'string' ? Number(value) : value;
  return Number.isFinite(parsed) ? parsed : undefined;
}

export default function SubtitleInspector({
  element,
  onChange,
}: SubtitleInspectorProps) {
  const [error, setError] = useState<string>();

  const publish = (candidate: SubtitleElement) => {
    const sanitized = sanitizeSubtitleElement(candidate);
    const validationMessage = validateSubtitleStyle(sanitized);
    if (validationMessage) {
      setError(validationMessage);
      return;
    }
    setError(undefined);
    onChange?.(sanitized);
  };

  const updateNumber = (
    key: 'fontSizePx' | 'outlineWidthPx',
    value: number | string | null,
  ) => {
    const nextValue = numberValue(value);
    if (nextValue === undefined) return;
    publish({ ...element, [key]: Math.round(nextValue) });
  };

  return (
    <section aria-label="字幕属性">
      <Segmented
        aria-label="字幕字体"
        options={[
          { label: '思源黑体', value: 'noto_sans_cjk_sc_regular' },
          { label: '思源宋体', value: 'noto_serif_cjk_sc_regular' },
        ]}
        value={element.fontCode}
        onChange={(value) => {
          const font = fixedTimelineFontFor(value as SubtitleFontCode);
          publish({
            ...element,
            fontCode: font.fontCode,
            fontVersion: font.version,
            fontSha256: font.sha256,
          });
        }}
      />
      <InputNumber
        aria-label="字体大小"
        precision={0}
        value={element.fontSizePx}
        onChange={(value) => updateNumber('fontSizePx', value)}
      />
      <Input
        aria-label="文字颜色"
        value={element.color}
        onChange={(event) => publish({ ...element, color: event.target.value })}
      />
      <Switch
        aria-label="显示背景"
        checked={element.backgroundEnabled}
        onChange={(backgroundEnabled) =>
          publish({
            ...element,
            backgroundEnabled,
            ...(backgroundEnabled ? { backgroundColor: '#00000080' } : {}),
          })
        }
      />
      <Input
        aria-label="背景颜色"
        disabled={!element.backgroundEnabled}
        value={element.backgroundColor ?? ''}
        onChange={(event) =>
          publish({ ...element, backgroundColor: event.target.value })
        }
      />
      <Switch
        aria-label="显示描边"
        checked={element.outlineEnabled}
        onChange={(outlineEnabled) =>
          publish({
            ...element,
            outlineEnabled,
            ...(outlineEnabled ? { outlineColor: '#000000FF' } : {}),
          })
        }
      />
      <Input
        aria-label="描边颜色"
        disabled={!element.outlineEnabled}
        value={element.outlineColor ?? ''}
        onChange={(event) =>
          publish({ ...element, outlineColor: event.target.value })
        }
      />
      <InputNumber
        aria-label="描边宽度"
        disabled={!element.outlineEnabled}
        precision={0}
        value={element.outlineWidthPx}
        onChange={(value) => updateNumber('outlineWidthPx', value)}
      />
      <Segmented
        aria-label="字幕安全区位置"
        options={[
          { label: '上方', value: 'upper' },
          { label: '中间', value: 'center' },
          { label: '下方', value: 'lower' },
        ]}
        value={element.safeAreaAnchor}
        onChange={(value) =>
          publish({
            ...element,
            safeAreaAnchor: value as SubtitleElement['safeAreaAnchor'],
          })
        }
      />
      <Segmented
        aria-label="字幕对齐"
        options={[
          { label: '左对齐', value: 'left' },
          { label: '居中', value: 'center' },
          { label: '右对齐', value: 'right' },
        ]}
        value={element.alignment}
        onChange={(value) =>
          publish({
            ...element,
            alignment: value as SubtitleElement['alignment'],
          })
        }
      />
      {error && <p role="alert">{error}</p>}
    </section>
  );
}
