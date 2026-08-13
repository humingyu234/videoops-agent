import { useEffect, useMemo, useState } from 'react';
import type {
  TimelineDocument,
  TimelineElement,
  TimelineTransform,
} from '@/services/ai-video/creation-timeline/types';
import PreviewElement from './PreviewElement';
import SubtitleOverlay from './SubtitleOverlay';
import type { TimelineAction } from './types';
import { usePreviewClock } from './usePreviewClock';

type TransformPreviewElement = Extract<
  TimelineElement,
  { transform: TimelineTransform }
>;

function isPreviewOverlay(element: TimelineElement): boolean {
  return element.elementType !== 'audio' && element.elementType !== 'main_video';
}

function isTransformPreviewElement(
  element: TimelineElement,
): element is TransformPreviewElement {
  return (
    element.elementType === 'image_overlay' ||
    element.elementType === 'pip_video' ||
    element.elementType === 'fancy_text'
  );
}

function isActiveAt(element: TimelineElement, positionMs: number): boolean {
  return (
    element.enabled &&
    positionMs >= element.startMs &&
    positionMs < element.endMs
  );
}

function formatPosition(positionMs: number): string {
  const wholeSeconds = Math.floor(positionMs / 1_000);
  const minutes = Math.floor(wholeSeconds / 60)
    .toString()
    .padStart(2, '0');
  const seconds = (wholeSeconds % 60).toString().padStart(2, '0');
  return `${minutes}:${seconds}`;
}

function useObjectUrl(blob?: Blob): string | undefined {
  const [url, setUrl] = useState<string>();

  useEffect(() => {
    if (!blob) {
      setUrl(undefined);
      return undefined;
    }
    const objectUrl = URL.createObjectURL(blob);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [blob]);

  return url;
}

export default function TimelinePreview({
  timeline,
  selectedElementId,
  videoUrl,
  audioUrl,
  videoBlob,
  audioBlob,
  onAction,
  onPositionChange,
  onSelect,
}: {
  timeline: TimelineDocument;
  selectedElementId?: string;
  videoUrl?: string;
  audioUrl?: string;
  videoBlob?: Blob;
  audioBlob?: Blob;
  onAction?: (action: TimelineAction) => void;
  onPositionChange?: (positionMs: number) => void;
  onSelect?: (elementId?: string) => void;
}) {
  const [videoElement, setVideoElement] = useState<HTMLVideoElement | null>(
    null,
  );
  const videoRef = useMemo(() => ({ current: videoElement }), [videoElement]);
  const videoObjectUrl = useObjectUrl(videoBlob);
  const audioObjectUrl = useObjectUrl(audioBlob);
  const resolvedVideoUrl = videoUrl ?? videoObjectUrl;
  const resolvedAudioUrl = audioUrl ?? audioObjectUrl;
  const clock = usePreviewClock({
    durationMs: timeline.canvas.durationMs,
    videoRef,
  });
  const activePreviewElements = timeline.tracks
    .flatMap((track) => track.elements)
    .filter(isPreviewOverlay)
    .filter((element) => isActiveAt(element, clock.positionMs))
    .sort((left, right) => left.zIndex - right.zIndex);

  useEffect(() => {
    onPositionChange?.(clock.positionMs);
  }, [clock.positionMs, onPositionChange]);

  return (
    <section aria-label="画面预览" className="timeline-preview-v2">
      <div className="timeline-preview-v2__canvas">
        {resolvedVideoUrl ? (
          <video
            aria-label="创作预览视频"
            className="timeline-preview-v2__video"
            controls
            preload="metadata"
            ref={setVideoElement}
            src={resolvedVideoUrl}
          >
            <track kind="captions" label="字幕" />
          </video>
        ) : (
          <div
            aria-label="预览画布"
            className="timeline-preview-v2__placeholder"
            role="img"
          >
            预览画布
          </div>
        )}
        <div aria-hidden="true" className="timeline-preview-v2__safe-area" />
        {activePreviewElements.map((element) => {
          if (isTransformPreviewElement(element)) {
            return (
              <PreviewElement
                element={element}
                key={element.elementId}
                positionMs={clock.positionMs}
                safeMarginRatio={timeline.canvas.safeMarginRatio}
                selected={selectedElementId === element.elementId}
                onAction={onAction}
                onSelect={onSelect}
              />
            );
          }
          if (element.elementType !== 'subtitle') return null;
          return (
            <button
              aria-label={`选择预览元素 ${element.label}`}
              aria-pressed={selectedElementId === element.elementId}
              className="timeline-preview-subtitle-v3"
              data-testid={`timeline-preview-subtitle-${element.elementId}`}
              key={element.elementId}
              type="button"
              onClick={() => onSelect?.(element.elementId)}
              onPointerDown={() => onSelect?.(element.elementId)}
            >
              <SubtitleOverlay
                element={element}
                safeMarginRatio={timeline.canvas.safeMarginRatio}
                onPlacementChange={(placement) =>
                  onAction?.({
                    type: 'elementPatched',
                    elementId: element.elementId,
                    patch: placement,
                  })
                }
              />
            </button>
          );
        })}
      </div>
      <div className="timeline-preview-v2__controls">
        <button
          aria-label={clock.playing ? '暂停预览' : '播放预览'}
          disabled={!resolvedVideoUrl}
          type="button"
          onClick={() => void clock.toggle()}
        >
          {clock.playing ? '暂停' : '播放'}
        </button>
        <input
          aria-label="时间轴播放位置"
          max={timeline.canvas.durationMs}
          min={0}
          step={50}
          type="range"
          value={clock.positionMs}
          onChange={(event) => clock.seek(Number(event.currentTarget.value))}
        />
        <output aria-live="polite">{formatPosition(clock.positionMs)}</output>
      </div>
      {resolvedAudioUrl && (
        <audio
          aria-label="创作预览音频"
          preload="metadata"
          src={resolvedAudioUrl}
        >
          <track kind="captions" label="字幕" />
        </audio>
      )}
    </section>
  );
}
