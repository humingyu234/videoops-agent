import {
  type PointerEvent,
  useCallback,
  useMemo,
  useRef,
  useState,
} from 'react';
import type {
  TimelineElement,
  TimelineTransform,
} from '@/services/ai-video/creation-timeline/types';
import { type PipPosition, positionPip } from './geometry';
import type { TimelineAction } from './types';

type PreviewElement = Extract<
  TimelineElement,
  { transform: TimelineTransform }
>;
type DragMode = 'move' | 'resize';
type PatchAction = Extract<TimelineAction, { type: 'elementPatched' }>;

export type PreviewAlignmentGuides = {
  centerX: boolean;
  centerY: boolean;
  safeArea: boolean;
};

export interface PreviewElementDragOptions {
  element: PreviewElement;
  safeMarginRatio: number;
  onPreview?: (transform: TimelineTransform) => void;
  onCommit?: (action: PatchAction) => void;
}

type ActiveDrag = {
  mode: DragMode;
  pointerId: number;
  clientX: number;
  clientY: number;
  width: number;
  height: number;
  initialTransform: TimelineTransform;
};

type PointerHandlers = {
  onPointerDown: (event: PointerEvent<HTMLElement>) => void;
  onPointerMove: (event: PointerEvent<HTMLElement>) => void;
  onPointerUp: (event: PointerEvent<HTMLElement>) => void;
  onPointerCancel: (event: PointerEvent<HTMLElement>) => void;
};

const SNAP_RATIO = 0.015;
const MIN_SIZE_RATIO = 0.05;

function equalTransform(
  left: TimelineTransform,
  right: TimelineTransform,
): boolean {
  return (
    left.xRatio === right.xRatio &&
    left.yRatio === right.yRatio &&
    left.widthRatio === right.widthRatio &&
    left.heightRatio === right.heightRatio
  );
}

function clampTransform(transform: TimelineTransform): TimelineTransform {
  const xRatio = Math.max(0, Math.min(1 - MIN_SIZE_RATIO, transform.xRatio));
  const yRatio = Math.max(0, Math.min(1 - MIN_SIZE_RATIO, transform.yRatio));
  const widthRatio = Math.max(
    MIN_SIZE_RATIO,
    Math.min(1 - xRatio, transform.widthRatio),
  );
  const heightRatio = Math.max(
    MIN_SIZE_RATIO,
    Math.min(1 - yRatio, transform.heightRatio),
  );
  return {
    ...transform,
    xRatio,
    yRatio,
    widthRatio,
    heightRatio,
  };
}

function snapToGuides(
  transform: TimelineTransform,
  safeMarginRatio: number,
): TimelineTransform {
  const next = { ...transform };
  const centerX = next.xRatio + next.widthRatio / 2;
  const centerY = next.yRatio + next.heightRatio / 2;
  if (Math.abs(centerX - 0.5) <= SNAP_RATIO)
    next.xRatio = 0.5 - next.widthRatio / 2;
  if (Math.abs(centerY - 0.5) <= SNAP_RATIO)
    next.yRatio = 0.5 - next.heightRatio / 2;
  if (Math.abs(next.xRatio - safeMarginRatio) <= SNAP_RATIO)
    next.xRatio = safeMarginRatio;
  if (Math.abs(next.yRatio - safeMarginRatio) <= SNAP_RATIO)
    next.yRatio = safeMarginRatio;
  if (
    Math.abs(next.xRatio + next.widthRatio - (1 - safeMarginRatio)) <=
    SNAP_RATIO
  ) {
    next.xRatio = 1 - safeMarginRatio - next.widthRatio;
  }
  if (
    Math.abs(next.yRatio + next.heightRatio - (1 - safeMarginRatio)) <=
    SNAP_RATIO
  ) {
    next.yRatio = 1 - safeMarginRatio - next.heightRatio;
  }
  return clampTransform(next);
}

function guidesFor(
  transform: TimelineTransform,
  safeMarginRatio: number,
): PreviewAlignmentGuides {
  return {
    centerX:
      Math.abs(transform.xRatio + transform.widthRatio / 2 - 0.5) <= SNAP_RATIO,
    centerY:
      Math.abs(transform.yRatio + transform.heightRatio / 2 - 0.5) <=
      SNAP_RATIO,
    safeArea:
      transform.xRatio >= safeMarginRatio &&
      transform.yRatio >= safeMarginRatio &&
      transform.xRatio + transform.widthRatio <= 1 - safeMarginRatio &&
      transform.yRatio + transform.heightRatio <= 1 - safeMarginRatio,
  };
}

export function usePreviewElementDrag({
  element,
  safeMarginRatio,
  onPreview,
  onCommit,
}: PreviewElementDragOptions) {
  const [isDragging, setIsDragging] = useState(false);
  const [previewTransform, setPreviewTransform] = useState<TimelineTransform>(
    element.transform,
  );
  const activeRef = useRef<ActiveDrag | undefined>(undefined);
  const previewRef = useRef<TimelineTransform>(element.transform);

  const updatePreview = useCallback(
    (transform: TimelineTransform) => {
      previewRef.current = transform;
      setPreviewTransform(transform);
      onPreview?.(transform);
    },
    [onPreview],
  );

  const transformForPointer = useCallback(
    (clientX: number, clientY: number): TimelineTransform | undefined => {
      const active = activeRef.current;
      if (!active) return undefined;
      const deltaX = (clientX - active.clientX) / active.width;
      const deltaY = (clientY - active.clientY) / active.height;
      const transform =
        active.mode === 'move'
          ? {
              ...active.initialTransform,
              xRatio: active.initialTransform.xRatio + deltaX,
              yRatio: active.initialTransform.yRatio + deltaY,
            }
          : {
              ...active.initialTransform,
              widthRatio: active.initialTransform.widthRatio + deltaX,
              heightRatio: active.initialTransform.heightRatio + deltaY,
            };
      return snapToGuides(clampTransform(transform), safeMarginRatio);
    },
    [safeMarginRatio],
  );

  const begin = useCallback(
    (mode: DragMode, event: PointerEvent<HTMLElement>) => {
      if (element.locked) return;
      const rect = event.currentTarget.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return;
      activeRef.current = {
        mode,
        pointerId: event.pointerId,
        clientX: event.clientX,
        clientY: event.clientY,
        width: rect.width,
        height: rect.height,
        initialTransform: element.transform,
      };
      event.currentTarget.setPointerCapture(event.pointerId);
      setIsDragging(true);
      updatePreview(element.transform);
    },
    [element, updatePreview],
  );

  const move = useCallback(
    (event: PointerEvent<HTMLElement>) => {
      const active = activeRef.current;
      if (!active || active.pointerId !== event.pointerId) return;
      const transform = transformForPointer(event.clientX, event.clientY);
      if (transform) updatePreview(transform);
    },
    [transformForPointer, updatePreview],
  );

  const finish = useCallback(
    (event: PointerEvent<HTMLElement>, commit: boolean) => {
      const active = activeRef.current;
      if (!active || active.pointerId !== event.pointerId) return;
      const transform =
        transformForPointer(event.clientX, event.clientY) ?? previewRef.current;
      event.currentTarget.releasePointerCapture(event.pointerId);
      activeRef.current = undefined;
      setIsDragging(false);
      updatePreview(transform);
      if (commit && !equalTransform(transform, active.initialTransform)) {
        onCommit?.({
          type: 'elementPatched',
          elementId: element.elementId,
          patch: { transform },
        });
      }
    },
    [element.elementId, onCommit, transformForPointer, updatePreview],
  );

  const placePip = useCallback(
    (position: PipPosition, edgeRatio: number) => {
      if (element.elementType !== 'pip_video' || element.locked) return;
      const transform = {
        ...element.transform,
        ...positionPip(position, edgeRatio, element.transform.widthRatio),
      };
      updatePreview(transform);
      onCommit?.({
        type: 'elementPatched',
        elementId: element.elementId,
        patch: { transform },
      });
    },
    [element, onCommit, updatePreview],
  );

  const handlers = useMemo<Record<DragMode, PointerHandlers>>(() => {
    const createHandlers = (mode: DragMode): PointerHandlers => ({
      onPointerDown: (event) => begin(mode, event),
      onPointerMove: move,
      onPointerUp: (event) => finish(event, true),
      onPointerCancel: (event) => finish(event, false),
    });
    return { move: createHandlers('move'), resize: createHandlers('resize') };
  }, [begin, finish, move]);

  return {
    isDragging,
    previewTransform,
    guides: guidesFor(previewTransform, safeMarginRatio),
    handlers,
    placePip,
  };
}
