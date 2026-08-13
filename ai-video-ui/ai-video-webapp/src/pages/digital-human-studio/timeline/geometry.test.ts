import { describe, expect, it } from 'vitest';
import type { TimelineElement, TimelineTrack } from '@/services/ai-video/creation-timeline/types';
import {
  deriveVisualLanes,
  msToPixels,
  normalizePipDisplayRange,
  positionPip,
  pixelsToMs,
  snapTime,
  sortTracksForTimeline,
  trimRange,
} from './geometry';

const visual = (elementId: string, startMs: number, endMs: number): TimelineElement => ({
  elementId,
  elementType: 'image_overlay',
  startMs,
  endMs,
  zIndex: 1,
  enabled: true,
  locked: false,
  label: elementId,
  assetId: '90071992547410001' as never,
  transform: { xRatio: 0, yRatio: 0, widthRatio: 1, heightRatio: 1, rotationDeg: 0, opacity: 1 },
  fitMode: 'contain',
  crop: { xRatio: 0, yRatio: 0, widthRatio: 1, heightRatio: 1 },
  fade: { fadeInMs: 0, fadeOutMs: 0 },
  sourceStartOffset: 0,
  sourceEndOffset: 0,
  adoptedPrompt: null,
  sourceTaskId: null,
});

describe('timeline geometry', () => {
  it('converts milliseconds, pixels, zoom, and the playhead without rounding drift', () => {
    expect(msToPixels(1500, 2)).toBe(300);
    expect(pixelsToMs(300, 2)).toBe(1500);
    expect(pixelsToMs(msToPixels(733, 1.5), 1.5)).toBe(733);
  });

  it('snaps to neighbours and main-video bounds unless a modifier disables snapping', () => {
    expect(snapTime(1008, { durationMs: 30000, snapPointsMs: [0, 1000, 5000, 30000] })).toBe(1000);
    expect(snapTime(29994, { durationMs: 30000, snapPointsMs: [0, 1000, 5000, 30000] })).toBe(30000);
    expect(snapTime(1008, { durationMs: 30000, snapPointsMs: [1000], modifierKey: true })).toBe(1008);
  });

  it('enforces minimum clip duration and project bounds while trimming', () => {
    expect(trimRange({ startMs: -1, endMs: 20 }, 30000)).toEqual({ startMs: 0, endMs: 100 });
    expect(trimRange({ startMs: 29980, endMs: 30050 }, 30000)).toEqual({ startMs: 29900, endMs: 30000 });
  });

  it('derives child visual lanes only for overlaps of the same timeline type', () => {
    const lanes = deriveVisualLanes([
      visual('a', 0, 1000),
      visual('b', 500, 1500),
      visual('c', 1500, 2500),
    ]);

    expect(lanes).toEqual([
      ['a', 'c'],
      ['b'],
    ]);
  });

  it('keeps main video at centre, visual tracks above, and audio tracks below', () => {
    const tracks = [
      { trackId: 'audio', trackType: 'background_music', area: 'bottom', order: 0, locked: false, muted: false, elements: [] },
      { trackId: 'main', trackType: 'main_video', area: 'center', order: 0, locked: true, muted: false, elements: [] },
      { trackId: 'visual', trackType: 'image_overlay', area: 'top', order: 0, locked: false, muted: false, elements: [] },
    ] as TimelineTrack[];

    expect(sortTracksForTimeline(tracks).map((track) => track.trackId)).toEqual(['visual', 'main', 'audio']);
  });

  it('keeps PiP source playback whole and looped when display is longer than its source', () => {
    expect(normalizePipDisplayRange({ startMs: 10000, endMs: 22000, sourceDurationMs: 5000 })).toEqual({
      startMs: 10000,
      endMs: 22000,
      loopWhenOverflow: true,
    });
  });

  it('positions PiP only at four corners using the requested edge distance', () => {
    expect(positionPip('top-left', 0.05)).toMatchObject({ xRatio: 0.05, yRatio: 0.05 });
    expect(positionPip('top-right', 0.05)).toMatchObject({ xRatio: 0.63, yRatio: 0.05 });
    expect(positionPip('bottom-left', 0.05)).toMatchObject({ xRatio: 0.05, yRatio: 0.63 });
    expect(positionPip('bottom-right', 0.05)).toMatchObject({ xRatio: 0.63, yRatio: 0.63 });
  });
});
