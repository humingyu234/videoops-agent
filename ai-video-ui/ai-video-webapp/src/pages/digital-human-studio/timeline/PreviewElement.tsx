import { type RefObject, useEffect, useRef } from 'react';
import type {
  PipVideoElement,
  TimelineElement,
  TimelineTransform,
} from '@/services/ai-video/creation-timeline/types';
import FancyTextOverlay from './FancyTextOverlay';
import type { TimelineAction } from './types';
import { usePreviewElementDrag } from './usePreviewElementDrag';

type PreviewTimelineElement = Extract<
  TimelineElement,
  { transform: TimelineTransform }
>;

const PIP_POSITIONS = [
  ['top-left', '左上'],
  ['top-right', '右上'],
  ['bottom-left', '左下'],
  ['bottom-right', '右下'],
] as const;

export interface PreviewElementProps {
  element: PreviewTimelineElement;
  positionMs: number;
  safeMarginRatio: number;
  mediaUrl?: string;
  selected?: boolean;
  onAction?: (action: TimelineAction) => void;
  onPreview?: (transform: TimelineTransform) => void;
  onSelect?: (elementId: string) => void;
}

/**
 * C0 renders a PiP source from its trimmed source range.  The timeline offset
 * is intentionally independent from the source's `assetId` and is safe for an
 * invalid server payload whose source start is not before its duration.
 */
export function getPipPlaybackTime(
  element: Pick<
    PipVideoElement,
    | 'startMs'
    | 'sourceStartMs'
    | 'sourceDurationMs'
    | 'loopWhenOverflow'
    | 'audioEnabled'
  >,
  positionMs: number,
): number | undefined {
  if (!element.loopWhenOverflow || element.audioEnabled) return undefined;
  const availableDurationMs = element.sourceDurationMs - element.sourceStartMs;
  if (availableDurationMs <= 0) return undefined;
  const localMs = Math.max(0, positionMs - element.startMs);
  return element.sourceStartMs + (localMs % availableDurationMs);
}

function ElementContent({
  element,
  mediaUrl,
  videoRef,
}: {
  element: PreviewTimelineElement;
  mediaUrl?: string;
  videoRef: RefObject<HTMLVideoElement | null>;
}) {
  if (element.elementType === 'pip_video') {
    if (!mediaUrl) {
      return <div aria-label="画中画素材未加载" role="img" />;
    }
    return (
      <video
        aria-label="画中画预览"
        muted
        playsInline
        preload="metadata"
        ref={videoRef}
        src={mediaUrl}
      >
        <track kind="captions" label="字幕" />
      </video>
    );
  }

  if (element.elementType === 'image_overlay') {
    return mediaUrl ? (
      <img alt={element.label} draggable={false} src={mediaUrl} />
    ) : (
      <div aria-label="图片素材未加载" role="img" />
    );
  }

  return <FancyTextOverlay element={element} />;
}

export default function PreviewElement({
  element,
  positionMs,
  safeMarginRatio,
  mediaUrl,
  selected = false,
  onAction,
  onPreview,
  onSelect,
}: PreviewElementProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const drag = usePreviewElementDrag({
    element,
    safeMarginRatio,
    onPreview,
    onCommit: onAction,
  });
  const pipPlaybackTime =
    element.elementType === 'pip_video'
      ? getPipPlaybackTime(element, positionMs)
      : undefined;
  const transform = drag.previewTransform;

  useEffect(() => {
    const video = videoRef.current;
    if (!video || pipPlaybackTime === undefined) return;
    try {
      video.currentTime = pipPlaybackTime / 1_000;
    } catch {
      // Browser metadata may still be loading; the next timeline update retries.
    }
  }, [pipPlaybackTime]);

  if (
    !element.enabled ||
    positionMs < element.startMs ||
    positionMs >= element.endMs
  ) {
    return null;
  }

  return (
    <div
      className="timeline-preview-element-v3"
      data-element-label={element.label}
      data-selected={selected || undefined}
      data-testid={`timeline-preview-element-${element.elementId}`}
      style={{
        height: `${transform.heightRatio * 100}%`,
        left: `${transform.xRatio * 100}%`,
        opacity: transform.opacity,
        top: `${transform.yRatio * 100}%`,
        transform: `rotate(${transform.rotationDeg}deg)`,
        width: `${transform.widthRatio * 100}%`,
        zIndex: element.zIndex,
      }}
      onPointerCancel={drag.handlers.move.onPointerCancel}
      onPointerDown={(event) => {
        onSelect?.(element.elementId);
        drag.handlers.move.onPointerDown(event);
      }}
      onPointerMove={drag.handlers.move.onPointerMove}
      onPointerUp={drag.handlers.move.onPointerUp}
    >
      <ElementContent
        element={element}
        mediaUrl={mediaUrl}
        videoRef={videoRef}
      />
      {drag.isDragging && (
        <>
          {drag.guides.centerX && (
            <span
              aria-hidden="true"
              className="timeline-preview-element-v3__guide timeline-preview-element-v3__guide--x"
            />
          )}
          {drag.guides.centerY && (
            <span
              aria-hidden="true"
              className="timeline-preview-element-v3__guide timeline-preview-element-v3__guide--y"
            />
          )}
          {drag.guides.safeArea && (
            <span
              aria-hidden="true"
              className="timeline-preview-element-v3__safe-guide"
            />
          )}
        </>
      )}
      <span
        aria-hidden="true"
        className="timeline-preview-element-v3__resize"
        onPointerCancel={(event) => {
          event.stopPropagation();
          drag.handlers.resize.onPointerCancel(event);
        }}
        onPointerDown={(event) => {
          event.stopPropagation();
          drag.handlers.resize.onPointerDown(event);
        }}
        onPointerMove={(event) => {
          event.stopPropagation();
          drag.handlers.resize.onPointerMove(event);
        }}
        onPointerUp={(event) => {
          event.stopPropagation();
          drag.handlers.resize.onPointerUp(event);
        }}
      />
      {element.elementType === 'pip_video' && (
        <fieldset className="timeline-preview-element-v3__pip-positions">
          <legend>画中画快捷位置</legend>
          {PIP_POSITIONS.map(([position, label]) => (
            <button
              disabled={element.locked}
              key={position}
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                drag.placePip(position, safeMarginRatio);
              }}
            >
              {label}
            </button>
          ))}
        </fieldset>
      )}
    </div>
  );
}
