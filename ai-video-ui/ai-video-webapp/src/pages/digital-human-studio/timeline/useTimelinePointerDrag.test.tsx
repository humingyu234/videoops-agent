import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type {
  TimelineElement,
  TimelineTrack,
} from '@/services/ai-video/creation-timeline/types';
import { useTimelinePointerDrag } from './useTimelinePointerDrag';

const element: TimelineElement = {
  elementId: 'image-1',
  elementType: 'image_overlay',
  startMs: 1_000,
  endMs: 3_000,
  zIndex: 1,
  enabled: true,
  locked: false,
  label: '图片',
  assetId: '10001' as never,
  transform: {
    xRatio: 0,
    yRatio: 0,
    widthRatio: 1,
    heightRatio: 1,
    rotationDeg: 0,
    opacity: 1,
  },
  fitMode: 'contain',
  crop: { xRatio: 0, yRatio: 0, widthRatio: 1, heightRatio: 1 },
  fade: { fadeInMs: 0, fadeOutMs: 0 },
  sourceStartOffset: 0,
  sourceEndOffset: 0,
  adoptedPrompt: null,
  sourceTaskId: null,
};

const track: TimelineTrack = {
  trackId: 'image-track',
  trackType: 'image_overlay',
  area: 'top',
  order: 0,
  locked: false,
  muted: false,
  elements: [element],
};

function pointerEvent(pointerId: number, clientX: number, modifierKey = false) {
  return {
    pointerId,
    clientX,
    ctrlKey: modifierKey,
    metaKey: false,
    currentTarget: {
      setPointerCapture: vi.fn(),
      releasePointerCapture: vi.fn(),
    },
  } as unknown as React.PointerEvent<HTMLElement>;
}

describe('useTimelinePointerDrag', () => {
  it('captures a pointer, previews a move, and commits one reducer action on release', () => {
    const onPreview = vi.fn();
    const onCommit = vi.fn();
    const { result } = renderHook(() =>
      useTimelinePointerDrag({
        durationMs: 10_000,
        element,
        track,
        zoom: 1,
        onPreview,
        onCommit,
      }),
    );
    const down = pointerEvent(7, 100);

    act(() => result.current.handlers.move.onPointerDown(down));
    expect(down.currentTarget.setPointerCapture).toHaveBeenCalledWith(7);
    act(() => result.current.handlers.move.onPointerMove(pointerEvent(7, 120)));
    expect(result.current.previewRange).toEqual({
      startMs: 1_200,
      endMs: 3_200,
    });
    expect(onCommit).not.toHaveBeenCalled();

    const up = pointerEvent(7, 120);
    act(() => result.current.handlers.move.onPointerUp(up));
    expect(up.currentTarget.releasePointerCapture).toHaveBeenCalledWith(7);
    expect(onCommit).toHaveBeenCalledTimes(1);
    expect(onCommit).toHaveBeenCalledWith({
      type: 'elementPatched',
      elementId: 'image-1',
      patch: { startMs: 1_200, endMs: 3_200 },
    });
  });

  it('snaps trims to known boundaries unless a modifier key disables snapping', () => {
    const onCommit = vi.fn();
    const { result } = renderHook(() =>
      useTimelinePointerDrag({
        durationMs: 10_000,
        element,
        track,
        zoom: 1,
        snapPointsMs: [2_000],
        onCommit,
      }),
    );

    act(() => result.current.handlers.start.onPointerDown(pointerEvent(1, 0)));
    act(() =>
      result.current.handlers.start.onPointerMove(pointerEvent(1, 100.8)),
    );
    expect(result.current.previewRange).toEqual({
      startMs: 2_000,
      endMs: 3_000,
    });
    act(() =>
      result.current.handlers.start.onPointerUp(pointerEvent(1, 100.8)),
    );

    act(() => result.current.handlers.start.onPointerDown(pointerEvent(2, 0)));
    act(() =>
      result.current.handlers.start.onPointerMove(pointerEvent(2, 100.8, true)),
    );
    expect(result.current.previewRange).toEqual({
      startMs: 2_008,
      endMs: 3_000,
    });
  });

  it('keeps C0 time values integral at every zoom level without cumulative pixel drift', () => {
    const onCommit = vi.fn();
    const { result } = renderHook(() =>
      useTimelinePointerDrag({
        durationMs: 10_000,
        element,
        track,
        zoom: 1.7,
        onCommit,
      }),
    );

    act(() => result.current.handlers.move.onPointerDown(pointerEvent(9, 0)));
    act(() =>
      result.current.handlers.move.onPointerMove(pointerEvent(9, 34.123)),
    );
    act(() =>
      result.current.handlers.move.onPointerUp(pointerEvent(9, 34.123)),
    );

    expect(onCommit).toHaveBeenLastCalledWith({
      type: 'elementPatched',
      elementId: 'image-1',
      patch: { startMs: 1_201, endMs: 3_201 },
    });
  });

  it('rejects locked elements and supports a bounded keyboard nudge without an active drag', () => {
    const onCommit = vi.fn();
    const { result, rerender } = renderHook(
      ({ currentElement }) =>
        useTimelinePointerDrag({
          durationMs: 10_000,
          element: currentElement,
          track,
          zoom: 1,
          onCommit,
        }),
      { initialProps: { currentElement: element } },
    );

    act(() => result.current.nudge(1));
    expect(onCommit).toHaveBeenCalledWith({
      type: 'elementPatched',
      elementId: 'image-1',
      patch: { startMs: 1_100, endMs: 3_100 },
    });

    rerender({ currentElement: { ...element, locked: true } });
    act(() => result.current.handlers.move.onPointerDown(pointerEvent(3, 0)));
    expect(result.current.isDragging).toBe(false);
  });
});
