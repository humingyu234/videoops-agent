import { type PointerEvent, useState } from 'react';
import type { SubtitleElement } from '@/services/ai-video/creation-timeline/types';
import {
  mapSubtitlePointerToPlacement,
  type SubtitlePlacement,
  subtitleWouldOverflow,
} from './subtitle';

export interface SubtitleOverlayProps {
  element: SubtitleElement;
  normalizedElementIds?: ReadonlySet<string> | readonly string[];
  safeMarginRatio?: number;
  safeAreaWidthPx?: number;
  measureText?: (text: string, fontSizePx: number) => number;
  onPlacementChange?: (placement: SubtitlePlacement) => void;
}

function anchorTop(
  anchor: SubtitleElement['safeAreaAnchor'],
  margin: number,
): string {
  if (anchor === 'upper') return `${margin * 100}%`;
  if (anchor === 'center') return '50%';
  return `${(1 - margin) * 100}%`;
}

function alignmentLeft(
  alignment: SubtitleElement['alignment'],
  margin: number,
): string {
  if (alignment === 'left') return `${margin * 100}%`;
  if (alignment === 'center') return '50%';
  return `${(1 - margin) * 100}%`;
}

function containsNormalizedElement(
  ids: SubtitleOverlayProps['normalizedElementIds'],
  elementId: string,
): boolean {
  if (!ids) return false;
  return isReadonlyStringSet(ids)
    ? ids.has(elementId)
    : ids.includes(elementId);
}

function isReadonlyStringSet(
  ids: NonNullable<SubtitleOverlayProps['normalizedElementIds']>,
): ids is ReadonlySet<string> {
  return 'has' in ids && typeof ids.has === 'function';
}

function measureWithCanvas(
  text: string,
  fontCode: SubtitleElement['fontCode'],
  fontSizePx: number,
): number | undefined {
  if (typeof document === 'undefined') return undefined;
  const context = document.createElement('canvas').getContext('2d');
  if (!context) return undefined;
  context.font = `${fontSizePx}px ${fontCode}`;
  return context.measureText(text).width;
}

export default function SubtitleOverlay({
  element,
  normalizedElementIds,
  safeMarginRatio = 0.05,
  safeAreaWidthPx,
  measureText,
  onPlacementChange,
}: SubtitleOverlayProps) {
  const [placementError, setPlacementError] = useState<string>();
  const textMeasure =
    measureText ??
    ((text: string, fontSizePx: number) =>
      measureWithCanvas(text, element.fontCode, fontSizePx));
  const measuredWidth =
    safeAreaWidthPx === undefined
      ? undefined
      : textMeasure(element.displayText, element.fontSizePx);
  const overflowed =
    safeAreaWidthPx !== undefined && measuredWidth !== undefined
      ? subtitleWouldOverflow({
          displayText: element.displayText,
          fontSizePx: element.fontSizePx,
          safeAreaWidthPx,
          measureText: () => measuredWidth,
        })
      : false;
  const normalized = containsNormalizedElement(
    normalizedElementIds,
    element.elementId,
  );

  const updatePlacement = (event: PointerEvent<HTMLElement>) => {
    const rectangle = event.currentTarget.getBoundingClientRect();
    if (rectangle.width <= 0 || rectangle.height <= 0) return;
    try {
      const placement = mapSubtitlePointerToPlacement({
        xRatio: (event.clientX - rectangle.left) / rectangle.width,
        yRatio: (event.clientY - rectangle.top) / rectangle.height,
        safeMarginRatio,
      });
      setPlacementError(undefined);
      onPlacementChange?.(placement);
    } catch {
      setPlacementError('字幕必须保持在安全区内');
    }
  };

  return (
    <section
      aria-label="字幕预览"
      className="timeline-subtitle-overlay"
      data-normalized={normalized || undefined}
      style={{ position: 'relative', width: '100%', height: '100%' }}
      onPointerUp={updatePlacement}
    >
      <p
        className="timeline-subtitle-overlay__text"
        style={{
          position: 'absolute',
          top: anchorTop(element.safeAreaAnchor, safeMarginRatio),
          left: alignmentLeft(element.alignment, safeMarginRatio),
          transform: 'translate(-50%, -50%)',
          margin: 0,
          color: element.color,
          fontFamily: element.fontCode,
          fontSize: element.fontSizePx,
          whiteSpace: 'nowrap',
          overflow: 'visible',
          backgroundColor: element.backgroundEnabled
            ? element.backgroundColor
            : undefined,
          WebkitTextStroke: element.outlineEnabled
            ? `${element.outlineWidthPx}px ${element.outlineColor}`
            : undefined,
        }}
      >
        {element.displayText}
      </p>
      {overflowed && (
        <p role="alert">字幕可能超出安全区，服务端保存会规范化。</p>
      )}
      {placementError && <p role="alert">{placementError}</p>}
    </section>
  );
}
