import { InputNumber, Segmented } from 'antd';
import { useState } from 'react';
import type {
  ImageOverlayElement,
  TimelineCrop,
  TimelineFade,
} from '@/services/ai-video/creation-timeline/types';

export type ImageInspectorPatch = Partial<
  Pick<ImageOverlayElement, 'fitMode' | 'crop' | 'fade'>
>;

export interface ImageInspectorProps {
  element: ImageOverlayElement;
  onPatch?: (patch: ImageInspectorPatch) => void;
}

function numberValue(value: number | string | null): number | undefined {
  if (value === null) return undefined;
  const number = typeof value === 'string' ? Number(value) : value;
  return Number.isFinite(number) ? number : undefined;
}

export function isValidCrop(crop: TimelineCrop): boolean {
  return (
    crop.xRatio >= 0 &&
    crop.yRatio >= 0 &&
    crop.widthRatio > 0 &&
    crop.heightRatio > 0 &&
    crop.xRatio + crop.widthRatio <= 1 &&
    crop.yRatio + crop.heightRatio <= 1
  );
}

export function isValidFade(fade: TimelineFade, durationMs: number): boolean {
  return (
    Number.isInteger(fade.fadeInMs) &&
    Number.isInteger(fade.fadeOutMs) &&
    fade.fadeInMs >= 0 &&
    fade.fadeOutMs >= 0 &&
    fade.fadeInMs <= 3_000 &&
    fade.fadeOutMs <= 3_000 &&
    fade.fadeInMs + fade.fadeOutMs <= durationMs
  );
}

export default function ImageInspector({
  element,
  onPatch,
}: ImageInspectorProps) {
  const durationMs = element.endMs - element.startMs;
  const [error, setError] = useState<string>();

  const updateCrop = (
    key: keyof TimelineCrop,
    value: number | string | null,
  ) => {
    const number = numberValue(value);
    if (number === undefined) return;
    const crop = { ...element.crop, [key]: number };
    if (!isValidCrop(crop)) {
      setError('裁剪框必须完全落在源图内');
      return;
    }
    setError(undefined);
    onPatch?.({ crop });
  };

  const updateFade = (
    key: keyof TimelineFade,
    value: number | string | null,
  ) => {
    const number = numberValue(value);
    if (number === undefined) return;
    const fade = { ...element.fade, [key]: Math.round(number) };
    if (!isValidFade(fade, durationMs)) {
      setError('淡入和淡出总和不能超过元素时长');
      return;
    }
    setError(undefined);
    onPatch?.({ fade });
  };

  return (
    <section aria-label="图片属性">
      <Segmented
        options={[
          { label: '适应', value: 'contain' },
          { label: '填充', value: 'cover' },
        ]}
        value={element.fitMode}
        onChange={(value) =>
          onPatch?.({ fitMode: value as ImageOverlayElement['fitMode'] })
        }
      />
      <div>
        <InputNumber
          aria-label="裁剪左边"
          max={1}
          min={0}
          precision={4}
          value={element.crop.xRatio}
          onChange={(value) => updateCrop('xRatio', value)}
        />
        <InputNumber
          aria-label="裁剪上边"
          max={1}
          min={0}
          precision={4}
          value={element.crop.yRatio}
          onChange={(value) => updateCrop('yRatio', value)}
        />
        <InputNumber
          aria-label="裁剪宽度"
          max={1}
          min={0.0001}
          precision={4}
          value={element.crop.widthRatio}
          onChange={(value) => updateCrop('widthRatio', value)}
        />
        <InputNumber
          aria-label="裁剪高度"
          max={1}
          min={0.0001}
          precision={4}
          value={element.crop.heightRatio}
          onChange={(value) => updateCrop('heightRatio', value)}
        />
      </div>
      <div>
        <InputNumber
          aria-label="淡入时长"
          max={3_000}
          min={0}
          precision={0}
          value={element.fade.fadeInMs}
          onChange={(value) => updateFade('fadeInMs', value)}
        />
        <InputNumber
          aria-label="淡出时长"
          max={3_000}
          min={0}
          precision={0}
          value={element.fade.fadeOutMs}
          onChange={(value) => updateFade('fadeOutMs', value)}
        />
      </div>
      {error && <p role="alert">{error}</p>}
    </section>
  );
}
