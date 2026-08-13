import {
  type PointerEvent,
  useCallback,
  useMemo,
  useRef,
  useState,
} from 'react';
import type {
  TimelineElement,
  TimelineTrack,
} from '@/services/ai-video/creation-timeline/types';
import { pixelsToMs, snapTime, trimRange } from './geometry';
import type { TimelineAction } from './types';

export type TimelineDragMode = 'move' | 'start' | 'end';

export type TimelineRange = {
  startMs: number;
  endMs: number;
};

type PatchAction = Extract<TimelineAction, { type: 'elementPatched' }>;

export interface TimelinePointerDragOptions {
  durationMs: number;
  element: TimelineElement;
  track: TimelineTrack;
  zoom: number;
  snapPointsMs?: number[];
  onPreview?: (range: TimelineRange) => void;
  onCommit?: (action: PatchAction) => void;
}

type ActiveDrag = {
  mode: TimelineDragMode;
  pointerId: number;
  startClientX: number;
  initialRange: TimelineRange;
};

type PointerHandlers = {
  onPointerDown: (event: PointerEvent<HTMLElement>) => void;
  onPointerMove: (event: PointerEvent<HTMLElement>) => void;
  onPointerUp: (event: PointerEvent<HTMLElement>) => void;
  onPointerCancel: (event: PointerEvent<HTMLElement>) => void;
};

function toRange(element: TimelineElement): TimelineRange {
  return { startMs: element.startMs, endMs: element.endMs };
}

function equalRange(left: TimelineRange, right: TimelineRange): boolean {
  return left.startMs === right.startMs && left.endMs === right.endMs;
}

function toTimelineMillisecond(value: number): number {
  return Math.round(value);
}

export function useTimelinePointerDrag({
  durationMs,
  element,
  track,
  zoom,
  snapPointsMs = [],
  onPreview,
  onCommit,
}: TimelinePointerDragOptions) {
  const [isDragging, setIsDragging] = useState(false);
  const [previewRange, setPreviewRange] = useState<TimelineRange>(() =>
    toRange(element),
  );
  const activeRef = useRef<ActiveDrag | undefined>(undefined);
  const previewRef = useRef(previewRange);

  const updatePreview = useCallback(
    (range: TimelineRange) => {
      previewRef.current = range;
      setPreviewRange(range);
      onPreview?.(range);
    },
    [onPreview],
  );

  const isDisabled = useCallback(
    (mode: TimelineDragMode) => {
      return (
        element.locked ||
        track.locked ||
        (mode === 'move' && element.elementType === 'main_video')
      );
    },
    [element.elementType, element.locked, track.locked],
  );

  const rangeForPointer = useCallback(
    (clientX: number, modifierKey: boolean): TimelineRange | undefined => {
      const active = activeRef.current;
      if (!active) return undefined;

      const deltaMs = pixelsToMs(clientX - active.startClientX, zoom);
      const initialDuration =
        active.initialRange.endMs - active.initialRange.startMs;
      const snap = (candidateMs: number) =>
        toTimelineMillisecond(
          snapTime(candidateMs, {
            durationMs,
            snapPointsMs,
            modifierKey,
          }),
        );

      if (active.mode === 'move') {
        const startMs = Math.max(
          0,
          Math.min(
            durationMs - initialDuration,
            snap(active.initialRange.startMs + deltaMs),
          ),
        );
        return { startMs, endMs: startMs + initialDuration };
      }
      if (active.mode === 'start') {
        return trimRange(
          {
            startMs: snap(active.initialRange.startMs + deltaMs),
            endMs: active.initialRange.endMs,
          },
          durationMs,
        );
      }
      return trimRange(
        {
          startMs: active.initialRange.startMs,
          endMs: snap(active.initialRange.endMs + deltaMs),
        },
        durationMs,
      );
    },
    [durationMs, snapPointsMs, zoom],
  );

  const begin = useCallback(
    (mode: TimelineDragMode, event: PointerEvent<HTMLElement>) => {
      if (isDisabled(mode)) return;
      const initialRange = toRange(element);
      activeRef.current = {
        mode,
        pointerId: event.pointerId,
        startClientX: event.clientX,
        initialRange,
      };
      event.currentTarget.setPointerCapture(event.pointerId);
      setIsDragging(true);
      updatePreview(initialRange);
    },
    [element, isDisabled, updatePreview],
  );

  const move = useCallback(
    (event: PointerEvent<HTMLElement>) => {
      const active = activeRef.current;
      if (!active || active.pointerId !== event.pointerId) return;
      const range = rangeForPointer(
        event.clientX,
        event.ctrlKey || event.metaKey,
      );
      if (range) updatePreview(range);
    },
    [rangeForPointer, updatePreview],
  );

  const finish = useCallback(
    (event: PointerEvent<HTMLElement>, commit: boolean) => {
      const active = activeRef.current;
      if (!active || active.pointerId !== event.pointerId) return;
      const range =
        rangeForPointer(event.clientX, event.ctrlKey || event.metaKey) ??
        previewRef.current;
      event.currentTarget.releasePointerCapture(event.pointerId);
      activeRef.current = undefined;
      setIsDragging(false);
      updatePreview(range);
      if (commit && !equalRange(range, active.initialRange)) {
        onCommit?.({
          type: 'elementPatched',
          elementId: element.elementId,
          patch: range,
        });
      }
    },
    [element.elementId, onCommit, rangeForPointer, updatePreview],
  );

  const nudge = useCallback(
    (direction: -1 | 1, stepMs = 100) => {
      if (isDisabled('move')) return;
      const initialRange = toRange(element);
      const duration = initialRange.endMs - initialRange.startMs;
      const startMs = Math.max(
        0,
        Math.min(
          durationMs - duration,
          initialRange.startMs + direction * stepMs,
        ),
      );
      const range = { startMs, endMs: startMs + duration };
      updatePreview(range);
      if (!equalRange(range, initialRange)) {
        onCommit?.({
          type: 'elementPatched',
          elementId: element.elementId,
          patch: range,
        });
      }
    },
    [durationMs, element, isDisabled, onCommit, updatePreview],
  );

  const handlers = useMemo<Record<TimelineDragMode, PointerHandlers>>(() => {
    const createHandlers = (mode: TimelineDragMode): PointerHandlers => ({
      onPointerDown: (event) => begin(mode, event),
      onPointerMove: move,
      onPointerUp: (event) => finish(event, true),
      onPointerCancel: (event) => finish(event, false),
    });
    return {
      move: createHandlers('move'),
      start: createHandlers('start'),
      end: createHandlers('end'),
    };
  }, [begin, finish, move]);

  return { isDragging, previewRange, handlers, nudge };
}
