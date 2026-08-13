import { InputNumber } from 'antd';
import { useState } from 'react';
import type {
  AudioElement,
  TimelineTrackType,
} from '@/services/ai-video/creation-timeline/types';

export interface AudioInspectorProps {
  element: AudioElement;
  onChange?: (element: AudioElement) => void;
}

function numberValue(value: number | string | null): number | undefined {
  if (value === null) return undefined;
  const parsed = typeof value === 'string' ? Number(value) : value;
  return Number.isFinite(parsed) ? parsed : undefined;
}

function audioBase(element: AudioElement) {
  return {
    elementId: element.elementId,
    elementType: 'audio' as const,
    startMs: element.startMs,
    endMs: element.endMs,
    zIndex: element.zIndex,
    enabled: element.enabled,
    locked: element.locked,
    label: element.label,
    assetId: element.assetId,
    usageType: element.usageType,
    sourceDurationMs: element.sourceDurationMs,
    sourceStartMs: element.sourceStartMs,
    sourceEndMs: element.sourceEndMs,
    volumeRatio: element.volumeRatio,
    fade: {
      fadeInMs: element.fade.fadeInMs,
      fadeOutMs: element.fade.fadeOutMs,
    },
  };
}

export function normalizeAudioElement(element: AudioElement): AudioElement {
  const base = audioBase(element);
  if (element.usageType === 'background_music') {
    return {
      ...base,
      volumeRatio: 0.3,
      loopWhenOverflow: true,
      duckingEnabled: true,
      targetGainRatio: 0.35,
      attackMs: 120,
      releaseMs: 400,
    };
  }
  return {
    ...base,
    loopWhenOverflow: false,
    duckingEnabled: false,
  };
}

export function validateAudioElement(
  element: AudioElement,
): string | undefined {
  if (
    !Number.isInteger(element.sourceStartMs) ||
    !Number.isInteger(element.sourceEndMs) ||
    element.sourceStartMs < 0 ||
    element.sourceStartMs >= element.sourceEndMs ||
    element.sourceEndMs > element.sourceDurationMs
  ) {
    return '素材裁剪范围无效';
  }
  if (
    !Number.isFinite(element.volumeRatio) ||
    element.volumeRatio < 0 ||
    element.volumeRatio > 1
  ) {
    return '音量必须在 0 到 1 之间';
  }
  if (
    !Number.isInteger(element.fade.fadeInMs) ||
    !Number.isInteger(element.fade.fadeOutMs) ||
    element.fade.fadeInMs < 0 ||
    element.fade.fadeOutMs < 0 ||
    element.fade.fadeInMs > 120_000 ||
    element.fade.fadeOutMs > 120_000 ||
    element.fade.fadeInMs + element.fade.fadeOutMs >
      element.endMs - element.startMs
  ) {
    return '淡入和淡出总和不能超过元素时长';
  }
  return undefined;
}

export function canAddPrimaryAudio(elements: readonly AudioElement[]): boolean {
  return !elements.some((element) => element.usageType === 'primary_audio');
}

export function audioTrackForUsage(
  usageType: AudioElement['usageType'],
): TimelineTrackType {
  return usageType;
}

export default function AudioInspector({
  element,
  onChange,
}: AudioInspectorProps) {
  const [error, setError] = useState<string>();
  const backgroundMusic = element.usageType === 'background_music';

  const publish = (candidate: AudioElement) => {
    const normalized = normalizeAudioElement(candidate);
    const validationMessage = validateAudioElement(normalized);
    if (validationMessage) {
      setError(validationMessage);
      return;
    }
    setError(undefined);
    onChange?.(normalized);
  };

  const updateNumber = (
    key: 'sourceStartMs' | 'sourceEndMs' | 'volumeRatio',
    value: number | string | null,
  ) => {
    const nextValue = numberValue(value);
    if (nextValue === undefined) return;
    publish({
      ...element,
      [key]: key === 'volumeRatio' ? nextValue : Math.round(nextValue),
    });
  };

  const updateFade = (
    key: 'fadeInMs' | 'fadeOutMs',
    value: number | string | null,
  ) => {
    const nextValue = numberValue(value);
    if (nextValue === undefined) return;
    publish({
      ...element,
      fade: { ...element.fade, [key]: Math.round(nextValue) },
    });
  };

  return (
    <section aria-label="音频属性">
      <InputNumber
        aria-label="来源起点"
        precision={0}
        value={element.sourceStartMs}
        onChange={(value) => updateNumber('sourceStartMs', value)}
      />
      <InputNumber
        aria-label="来源终点"
        precision={0}
        value={element.sourceEndMs}
        onChange={(value) => updateNumber('sourceEndMs', value)}
      />
      <InputNumber
        aria-label="音量"
        disabled={backgroundMusic}
        precision={4}
        value={backgroundMusic ? 0.3 : element.volumeRatio}
        onChange={(value) => updateNumber('volumeRatio', value)}
      />
      <InputNumber
        aria-label="淡入时长"
        precision={0}
        value={element.fade.fadeInMs}
        onChange={(value) => updateFade('fadeInMs', value)}
      />
      <InputNumber
        aria-label="淡出时长"
        precision={0}
        value={element.fade.fadeOutMs}
        onChange={(value) => updateFade('fadeOutMs', value)}
      />
      {backgroundMusic && <p>背景音乐将自动闪避主配音。</p>}
      {error && <p role="alert">{error}</p>}
    </section>
  );
}
