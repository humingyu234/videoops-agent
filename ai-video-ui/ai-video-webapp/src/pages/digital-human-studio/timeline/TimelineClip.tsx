import { type KeyboardEvent, useCallback } from 'react';
import type {
  TimelineDocument,
  TimelineElement,
  TimelineTrack,
} from '@/services/ai-video/creation-timeline/types';
import { copyElement, splitElement } from './commands';
import { msToPixels } from './geometry';
import type { TimelineAction } from './types';
import { useTimelinePointerDrag } from './useTimelinePointerDrag';

export interface TimelineClipProps {
  durationMs: number;
  element: TimelineElement;
  track: TimelineTrack;
  zoom: number;
  timeline?: TimelineDocument;
  playheadMs?: number;
  selected?: boolean;
  createElementId?: () => string;
  onAction?: (action: TimelineAction) => void;
  onPreview?: (range: { startMs: number; endMs: number }) => void;
  onSelect?: (elementId: string) => void;
}

function defaultCopiedElementId(elementId: string): string {
  return `${elementId}-copy`;
}

export default function TimelineClip({
  durationMs,
  element,
  track,
  zoom,
  timeline,
  playheadMs,
  selected = false,
  createElementId,
  onAction,
  onPreview,
  onSelect,
}: TimelineClipProps) {
  const drag = useTimelinePointerDrag({
    durationMs,
    element,
    track,
    zoom,
    onPreview,
    onCommit: onAction,
  });
  const range = drag.previewRange;
  const locked = element.locked || track.locked;
  const canDuplicate = !locked && element.elementType !== 'main_video';
  const canDelete = canDuplicate && track.elements.length > 1;

  const createCopyId = useCallback(
    () => createElementId?.() ?? defaultCopiedElementId(element.elementId),
    [createElementId, element.elementId],
  );

  const copy = useCallback(() => {
    if (!timeline || !canDuplicate) return;
    onAction?.(copyElement(timeline, element.elementId, createCopyId()));
  }, [canDuplicate, createCopyId, element.elementId, onAction, timeline]);

  const split = useCallback(() => {
    if (!timeline || !canDuplicate) return;
    const splitAtMs =
      playheadMs ?? Math.round((range.startMs + range.endMs) / 2);
    try {
      const actions = splitElement(
        timeline,
        element.elementId,
        createCopyId(),
        splitAtMs,
      );
      actions.forEach((action) => {
        onAction?.(action);
      });
    } catch {
      // A playhead at either edge cannot form two valid clips; leave the draft unchanged.
    }
  }, [
    canDuplicate,
    createCopyId,
    element.elementId,
    onAction,
    playheadMs,
    range.endMs,
    range.startMs,
    timeline,
  ]);

  const onKeyDown = useCallback(
    (event: KeyboardEvent<HTMLButtonElement>) => {
      if (locked) return;
      if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
        event.preventDefault();
        drag.nudge(
          event.key === 'ArrowLeft' ? -1 : 1,
          event.shiftKey ? 1_000 : 100,
        );
        return;
      }
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'd') {
        event.preventDefault();
        copy();
        return;
      }
      if (event.key.toLowerCase() === 's') {
        event.preventDefault();
        split();
        return;
      }
      if ((event.key === 'Backspace' || event.key === 'Delete') && canDelete) {
        event.preventDefault();
        onAction?.({ type: 'elementRemoved', elementId: element.elementId });
      }
    },
    [canDelete, copy, drag, element.elementId, locked, onAction, split],
  );

  const clipStyle = {
    left: `${msToPixels(range.startMs, zoom)}px`,
    width: `${msToPixels(range.endMs - range.startMs, zoom)}px`,
  };

  return (
    <button
      aria-disabled={locked}
      aria-label={`选择时间轴片段 ${element.label}`}
      aria-pressed={selected}
      className="timeline-clip-v3"
      data-dragging={drag.isDragging || undefined}
      data-muted={track.muted || undefined}
      data-selected={selected || undefined}
      disabled={locked}
      style={clipStyle}
      type="button"
      onClick={() => onSelect?.(element.elementId)}
      onKeyDown={onKeyDown}
      onPointerCancel={drag.handlers.move.onPointerCancel}
      onPointerDown={drag.handlers.move.onPointerDown}
      onPointerMove={drag.handlers.move.onPointerMove}
      onPointerUp={drag.handlers.move.onPointerUp}
    >
      <span aria-hidden="true" className="timeline-clip-v3__label">
        {element.label}
      </span>
      <span
        aria-hidden="true"
        className="timeline-clip-v3__handle timeline-clip-v3__handle--start"
        onPointerCancel={(event) => {
          event.stopPropagation();
          drag.handlers.start.onPointerCancel(event);
        }}
        onPointerDown={(event) => {
          event.stopPropagation();
          drag.handlers.start.onPointerDown(event);
        }}
        onPointerMove={(event) => {
          event.stopPropagation();
          drag.handlers.start.onPointerMove(event);
        }}
        onPointerUp={(event) => {
          event.stopPropagation();
          drag.handlers.start.onPointerUp(event);
        }}
      />
      <span
        aria-hidden="true"
        className="timeline-clip-v3__handle timeline-clip-v3__handle--end"
        onPointerCancel={(event) => {
          event.stopPropagation();
          drag.handlers.end.onPointerCancel(event);
        }}
        onPointerDown={(event) => {
          event.stopPropagation();
          drag.handlers.end.onPointerDown(event);
        }}
        onPointerMove={(event) => {
          event.stopPropagation();
          drag.handlers.end.onPointerMove(event);
        }}
        onPointerUp={(event) => {
          event.stopPropagation();
          drag.handlers.end.onPointerUp(event);
        }}
      />
    </button>
  );
}
