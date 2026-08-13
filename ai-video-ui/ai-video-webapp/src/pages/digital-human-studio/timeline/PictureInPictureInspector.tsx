import { InputNumber, Segmented } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import type {
  PipVideoElement,
  TimelineFade,
  TimelineTransform,
} from '@/services/ai-video/creation-timeline/types';
import { isValidFade } from './ImageInspector';

export type PipCorner =
  | 'top-left'
  | 'top-right'
  | 'bottom-left'
  | 'bottom-right';

export type PictureInPicturePatch = Partial<
  Pick<PipVideoElement, 'transform' | 'fade' | 'sourceStartMs'>
>;

export interface PictureInPictureInspectorProps {
  element: PipVideoElement;
  onPatch?: (patch: PictureInPicturePatch) => void;
}

function numberValue(value: number | string | null): number | undefined {
  if (value === null) return undefined;
  const number = typeof value === 'string' ? Number(value) : value;
  return Number.isFinite(number) ? number : undefined;
}

function canonicalRatio(value: number): number {
  return Number(value.toFixed(4));
}

function isValidTransform(transform: TimelineTransform): boolean {
  return (
    transform.xRatio >= 0 &&
    transform.yRatio >= 0 &&
    transform.widthRatio > 0 &&
    transform.heightRatio > 0 &&
    transform.xRatio + transform.widthRatio <= 1 &&
    transform.yRatio + transform.heightRatio <= 1 &&
    transform.opacity >= 0 &&
    transform.opacity <= 1
  );
}

function cornerFor(transform: TimelineTransform): PipCorner {
  const horizontal =
    transform.xRatio + transform.widthRatio / 2 < 0.5 ? 'left' : 'right';
  const vertical =
    transform.yRatio + transform.heightRatio / 2 < 0.5 ? 'top' : 'bottom';
  return `${vertical}-${horizontal}` as PipCorner;
}

function marginsFor(transform: TimelineTransform, corner: PipCorner) {
  return {
    horizontal: corner.endsWith('left')
      ? transform.xRatio
      : 1 - transform.xRatio - transform.widthRatio,
    vertical: corner.startsWith('top')
      ? transform.yRatio
      : 1 - transform.yRatio - transform.heightRatio,
  };
}

export function positionPipAtCorner(
  transform: TimelineTransform,
  corner: PipCorner,
  horizontalMargin: number,
  verticalMargin: number,
): TimelineTransform {
  if (
    !Number.isFinite(horizontalMargin) ||
    !Number.isFinite(verticalMargin) ||
    horizontalMargin < 0 ||
    verticalMargin < 0
  ) {
    throw new Error('Picture in picture margins must be non-negative');
  }
  const xRatio = corner.endsWith('left')
    ? horizontalMargin
    : 1 - horizontalMargin - transform.widthRatio;
  const yRatio = corner.startsWith('top')
    ? verticalMargin
    : 1 - verticalMargin - transform.heightRatio;
  const result = {
    ...transform,
    xRatio: canonicalRatio(xRatio),
    yRatio: canonicalRatio(yRatio),
  };
  if (!isValidTransform(result)) {
    throw new Error('Picture in picture must remain inside the canvas');
  }
  return result;
}

export default function PictureInPictureInspector({
  element,
  onPatch,
}: PictureInPictureInspectorProps) {
  const [corner, setCorner] = useState<PipCorner>(() =>
    cornerFor(element.transform),
  );
  const [transform, setTransform] = useState<TimelineTransform>(
    element.transform,
  );
  const [fade, setFade] = useState<TimelineFade>(element.fade);
  const [sourceStartMs, setSourceStartMs] = useState(element.sourceStartMs);
  const [error, setError] = useState<string>();

  useEffect(() => {
    setCorner(cornerFor(element.transform));
    setTransform(element.transform);
    setFade(element.fade);
    setSourceStartMs(element.sourceStartMs);
  }, [element]);

  const margins = useMemo(
    () => marginsFor(transform, corner),
    [corner, transform],
  );
  const durationMs = element.endMs - element.startMs;

  const commitTransform = (nextTransform: TimelineTransform) => {
    if (!isValidTransform(nextTransform)) {
      setError('画中画必须完全落在画布内');
      return;
    }
    setError(undefined);
    setTransform(nextTransform);
    onPatch?.({ transform: nextTransform });
  };

  const updateCorner = (nextCorner: PipCorner) => {
    try {
      const nextTransform = positionPipAtCorner(
        transform,
        nextCorner,
        margins.horizontal,
        margins.vertical,
      );
      setCorner(nextCorner);
      commitTransform(nextTransform);
    } catch {
      setError('画中画必须完全落在画布内');
    }
  };

  const updateMargin = (
    axis: 'horizontal' | 'vertical',
    value: number | string | null,
  ) => {
    const nextMargin = numberValue(value);
    if (nextMargin === undefined) return;
    try {
      const nextTransform = positionPipAtCorner(
        transform,
        corner,
        axis === 'horizontal' ? nextMargin : margins.horizontal,
        axis === 'vertical' ? nextMargin : margins.vertical,
      );
      commitTransform(nextTransform);
    } catch {
      setError('画中画必须完全落在画布内');
    }
  };

  const updateSize = (value: number | string | null) => {
    const size = numberValue(value);
    if (size === undefined) return;
    try {
      const nextTransform = positionPipAtCorner(
        {
          ...transform,
          widthRatio: canonicalRatio(size),
          heightRatio: canonicalRatio(size),
        },
        corner,
        margins.horizontal,
        margins.vertical,
      );
      commitTransform(nextTransform);
    } catch {
      setError('画中画必须完全落在画布内');
    }
  };

  const updateOpacity = (value: number | string | null) => {
    const opacity = numberValue(value);
    if (opacity === undefined) return;
    commitTransform({ ...transform, opacity: canonicalRatio(opacity) });
  };

  const updateFade = (
    key: keyof TimelineFade,
    value: number | string | null,
  ) => {
    const number = numberValue(value);
    if (number === undefined) return;
    const nextFade = { ...fade, [key]: Math.round(number) };
    if (!isValidFade(nextFade, durationMs)) {
      setError('淡入和淡出总和不能超过元素时长');
      return;
    }
    setError(undefined);
    setFade(nextFade);
    onPatch?.({ fade: nextFade });
  };

  const updateSourceStart = (value: number | string | null) => {
    const nextStart = numberValue(value);
    if (nextStart === undefined) return;
    const sourceStart = Math.round(nextStart);
    if (sourceStart < 0 || sourceStart >= element.sourceDurationMs) {
      setError('来源起点必须小于素材时长');
      return;
    }
    setError(undefined);
    setSourceStartMs(sourceStart);
    onPatch?.({ sourceStartMs: sourceStart });
  };

  return (
    <section aria-label="画中画属性">
      <Segmented
        options={[
          { label: '左上', value: 'top-left' },
          { label: '右上', value: 'top-right' },
          { label: '左下', value: 'bottom-left' },
          { label: '右下', value: 'bottom-right' },
        ]}
        value={corner}
        onChange={(value) => updateCorner(value as PipCorner)}
      />
      <div>
        <InputNumber
          aria-label="水平边距"
          max={1}
          min={0}
          precision={4}
          value={margins.horizontal}
          onChange={(value) => updateMargin('horizontal', value)}
        />
        <InputNumber
          aria-label="垂直边距"
          max={1}
          min={0}
          precision={4}
          value={margins.vertical}
          onChange={(value) => updateMargin('vertical', value)}
        />
        <InputNumber
          aria-label="画中画尺寸"
          max={1}
          min={0.05}
          precision={4}
          value={transform.widthRatio}
          onChange={updateSize}
        />
        <InputNumber
          aria-label="透明度"
          max={1}
          min={0}
          precision={4}
          value={transform.opacity}
          onChange={updateOpacity}
        />
      </div>
      <div>
        <InputNumber
          aria-label="淡入时长"
          max={3_000}
          min={0}
          precision={0}
          value={fade.fadeInMs}
          onChange={(value) => updateFade('fadeInMs', value)}
        />
        <InputNumber
          aria-label="淡出时长"
          max={3_000}
          min={0}
          precision={0}
          value={fade.fadeOutMs}
          onChange={(value) => updateFade('fadeOutMs', value)}
        />
        <InputNumber
          aria-label="来源起点"
          max={element.sourceDurationMs}
          min={0}
          precision={0}
          value={sourceStartMs}
          onChange={updateSourceStart}
        />
      </div>
      <p>画中画将循环播放，且首版保持静音。</p>
      {error && <p role="alert">{error}</p>}
    </section>
  );
}
