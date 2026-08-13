import { InputNumber, Segmented } from 'antd';
import { useState } from 'react';
import type { VisualEffectElement } from '@/services/ai-video/creation-timeline/types';

export interface VisualEffectInspectorProps {
  element: VisualEffectElement;
  onChange?: (element: VisualEffectElement) => void;
}

const EFFECT_OPTIONS: Array<{
  label: string;
  value: VisualEffectElement['effectCode'];
}> = [
  { label: '淡入', value: 'fade_in' },
  { label: '淡出', value: 'fade_out' },
  { label: '轻微放大', value: 'gentle_zoom_in' },
  { label: '轻微缩小', value: 'gentle_zoom_out' },
  { label: '轻微模糊', value: 'light_blur' },
];

function numberValue(value: number | string | null): number | undefined {
  if (value === null) return undefined;
  const parsed = typeof value === 'string' ? Number(value) : value;
  return Number.isFinite(parsed) ? parsed : undefined;
}

function canonicalEffectValue(value: number): number {
  return Number(value.toFixed(4));
}

function hasFourDecimalPrecision(value: number): boolean {
  return Number.isInteger(value * 10_000);
}

function visualEffectBase(element: VisualEffectElement) {
  return {
    elementId: element.elementId,
    elementType: 'visual_effect' as const,
    startMs: element.startMs,
    endMs: element.endMs,
    zIndex: element.zIndex,
    enabled: element.enabled,
    locked: element.locked,
    label: element.label,
    effectCode: element.effectCode,
    durationMs: element.durationMs,
    scale: element.scale,
    radius: element.radius,
  };
}

export function normalizeVisualEffect(
  element: VisualEffectElement,
): VisualEffectElement {
  const base = visualEffectBase(element);
  switch (element.effectCode) {
    case 'fade_in':
    case 'fade_out':
      return { ...base, scale: null, radius: null };
    case 'gentle_zoom_in':
    case 'gentle_zoom_out':
      return {
        ...base,
        scale:
          element.scale !== null && element.scale >= 1 && element.scale <= 1.2
            ? canonicalEffectValue(element.scale)
            : 1.1,
        radius: null,
      };
    case 'light_blur':
      return {
        ...base,
        scale: null,
        radius:
          element.radius !== null &&
          element.radius >= 0.5 &&
          element.radius <= 12
            ? canonicalEffectValue(element.radius)
            : 4,
      };
    default:
      throw new Error('Unknown visual effect');
  }
}

export function validateVisualEffect(
  element: VisualEffectElement,
): string | undefined {
  try {
    normalizeVisualEffect(element);
  } catch {
    return '特效不在允许列表中';
  }
  if (
    !Number.isInteger(element.durationMs) ||
    element.durationMs < 100 ||
    element.durationMs > 3_000
  ) {
    return '特效时长必须在 100 到 3000 毫秒之间';
  }
  if (
    (element.effectCode === 'gentle_zoom_in' ||
      element.effectCode === 'gentle_zoom_out') &&
    (element.scale === null ||
      element.scale < 1 ||
      element.scale > 1.2 ||
      !hasFourDecimalPrecision(element.scale))
  ) {
    return '缩放强度必须在 1 到 1.2 之间';
  }
  if (
    element.effectCode === 'light_blur' &&
    (element.radius === null ||
      element.radius < 0.5 ||
      element.radius > 12 ||
      !hasFourDecimalPrecision(element.radius))
  ) {
    return '模糊半径必须在 0.5 到 12 之间';
  }
  return undefined;
}

export default function VisualEffectInspector({
  element,
  onChange,
}: VisualEffectInspectorProps) {
  const [error, setError] = useState<string>();

  const publish = (candidate: VisualEffectElement) => {
    const nextElement =
      candidate.effectCode === element.effectCode
        ? candidate
        : normalizeVisualEffect(candidate);
    const validationMessage = validateVisualEffect(nextElement);
    if (validationMessage) {
      setError(validationMessage);
      return;
    }
    setError(undefined);
    onChange?.(normalizeVisualEffect(nextElement));
  };

  const updateDuration = (value: number | string | null) => {
    const nextValue = numberValue(value);
    if (nextValue === undefined) return;
    publish({ ...element, durationMs: Math.round(nextValue) });
  };

  const updateIntensity = (value: number | string | null) => {
    const nextValue = numberValue(value);
    if (nextValue === undefined) return;
    if (
      element.effectCode === 'gentle_zoom_in' ||
      element.effectCode === 'gentle_zoom_out'
    ) {
      publish({ ...element, scale: nextValue });
      return;
    }
    if (element.effectCode === 'light_blur') {
      publish({ ...element, radius: nextValue });
    }
  };

  const needsScale =
    element.effectCode === 'gentle_zoom_in' ||
    element.effectCode === 'gentle_zoom_out';
  const needsRadius = element.effectCode === 'light_blur';

  return (
    <section aria-label="画面特效属性">
      <Segmented
        aria-label="特效类型"
        options={EFFECT_OPTIONS}
        value={element.effectCode}
        onChange={(value) =>
          publish({
            ...element,
            effectCode: value as VisualEffectElement['effectCode'],
          })
        }
      />
      <InputNumber
        aria-label="特效时长"
        precision={0}
        value={element.durationMs}
        onChange={updateDuration}
      />
      {needsScale && (
        <InputNumber
          aria-label="缩放强度"
          precision={4}
          value={element.scale ?? 1.1}
          onChange={updateIntensity}
        />
      )}
      {needsRadius && (
        <InputNumber
          aria-label="模糊半径"
          precision={4}
          value={element.radius ?? 4}
          onChange={updateIntensity}
        />
      )}
      {error && <p role="alert">{error}</p>}
    </section>
  );
}
