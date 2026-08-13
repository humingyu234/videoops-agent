import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  ImageOverlayElement,
  TimelineDocument,
  TimelineTrack,
} from '@/services/ai-video/creation-timeline/types';
import TimelineClip from './TimelineClip';

const image: ImageOverlayElement = {
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
  elements: [image, { ...image, elementId: 'image-older' }],
};

const timeline: TimelineDocument = {
  schemaVersion: 'timeline-1',
  canvas: {
    width: 1080,
    height: 1920,
    frameRate: 30,
    durationMs: 10_000,
    safeMarginRatio: 0.05,
  },
  tracks: [track],
};

describe('TimelineClip', () => {
  beforeEach(() => {
    Object.defineProperty(HTMLElement.prototype, 'setPointerCapture', {
      configurable: true,
      value: vi.fn(),
    });
    Object.defineProperty(HTMLElement.prototype, 'releasePointerCapture', {
      configurable: true,
      value: vi.fn(),
    });
  });

  it('keeps time geometry exact while committing a drag only after pointer release', () => {
    const onAction = vi.fn();
    render(
      <TimelineClip
        durationMs={10_000}
        element={image}
        track={track}
        zoom={1}
        onAction={onAction}
      />,
    );

    const clip = screen.getByRole('button', { name: '选择时间轴片段 图片' });
    expect(clip).toHaveStyle({ left: '100px', width: '200px' });

    fireEvent.pointerDown(clip, { pointerId: 1, clientX: 0 });
    fireEvent.pointerMove(clip, { pointerId: 1, clientX: 20 });
    expect(onAction).not.toHaveBeenCalled();
    fireEvent.pointerUp(clip, { pointerId: 1, clientX: 20 });

    expect(onAction).toHaveBeenCalledWith({
      type: 'elementPatched',
      elementId: 'image-1',
      patch: { startMs: 1_200, endMs: 3_200 },
    });
  });

  it('offers bounded keyboard nudge plus copy, split, and delete reducer commands', () => {
    const onAction = vi.fn();
    const onSelect = vi.fn();
    render(
      <TimelineClip
        createElementId={() => 'image-copy'}
        durationMs={10_000}
        element={image}
        playheadMs={2_000}
        timeline={timeline}
        track={track}
        zoom={1}
        onAction={onAction}
        onSelect={onSelect}
      />,
    );

    const clip = screen.getByRole('button', { name: '选择时间轴片段 图片' });
    fireEvent.click(clip);
    expect(onSelect).toHaveBeenCalledWith('image-1');

    fireEvent.keyDown(clip, { key: 'ArrowRight' });
    expect(onAction).toHaveBeenLastCalledWith({
      type: 'elementPatched',
      elementId: 'image-1',
      patch: { startMs: 1_100, endMs: 3_100 },
    });

    fireEvent.keyDown(clip, { key: 'd', ctrlKey: true });
    expect(onAction).toHaveBeenLastCalledWith(
      expect.objectContaining({
        type: 'elementAdded',
        trackId: 'image-track',
        element: expect.objectContaining({ elementId: 'image-copy' }),
      }),
    );

    fireEvent.keyDown(clip, { key: 's' });
    expect(onAction).toHaveBeenLastCalledWith(
      expect.objectContaining({
        type: 'elementAdded',
        trackId: 'image-track',
        element: expect.objectContaining({ elementId: 'image-copy' }),
      }),
    );

    fireEvent.keyDown(clip, { key: 'Delete' });
    expect(onAction).toHaveBeenLastCalledWith({
      type: 'elementRemoved',
      elementId: 'image-1',
    });
  });
});
