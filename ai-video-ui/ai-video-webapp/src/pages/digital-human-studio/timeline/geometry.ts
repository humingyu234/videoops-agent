import type { TimelineElement, TimelineTrack, TimelineTransform } from '@/services/ai-video/creation-timeline/types';

export const TIMELINE_PIXELS_PER_SECOND = 100;
export const MIN_CLIP_DURATION_MS = 100;
const SNAP_THRESHOLD_MS = 12;

export function msToPixels(milliseconds: number, zoom: number): number {
  return (milliseconds / 1000) * TIMELINE_PIXELS_PER_SECOND * zoom;
}

export function pixelsToMs(pixels: number, zoom: number): number {
  return Number(((pixels / (TIMELINE_PIXELS_PER_SECOND * zoom)) * 1000).toFixed(6));
}

export function snapTime(
  candidateMs: number,
  options: { durationMs: number; snapPointsMs: number[]; modifierKey?: boolean },
): number {
  const bounded = Math.min(options.durationMs, Math.max(0, candidateMs));
  if (options.modifierKey) return bounded;
  const target = [0, options.durationMs, ...options.snapPointsMs]
    .filter((point) => point >= 0 && point <= options.durationMs)
    .sort((left, right) => Math.abs(left - bounded) - Math.abs(right - bounded))[0];
  return target !== undefined && Math.abs(target - bounded) <= SNAP_THRESHOLD_MS ? target : bounded;
}

export function trimRange(
  range: { startMs: number; endMs: number },
  durationMs: number,
): { startMs: number; endMs: number } {
  let startMs = Math.max(0, Math.min(range.startMs, durationMs));
  let endMs = Math.max(startMs, Math.min(range.endMs, durationMs));
  if (endMs - startMs >= MIN_CLIP_DURATION_MS) return { startMs, endMs };
  if (endMs >= durationMs) startMs = Math.max(0, durationMs - MIN_CLIP_DURATION_MS);
  else endMs = Math.min(durationMs, startMs + MIN_CLIP_DURATION_MS);
  return { startMs, endMs };
}

export function deriveVisualLanes(elements: TimelineElement[]): string[][] {
  const lanes: { endMs: number; ids: string[] }[] = [];
  for (const element of [...elements].sort((left, right) => left.startMs - right.startMs || left.endMs - right.endMs)) {
    const lane = lanes.find((candidate) => candidate.endMs <= element.startMs);
    if (lane) {
      lane.endMs = element.endMs;
      lane.ids.push(element.elementId);
    } else {
      lanes.push({ endMs: element.endMs, ids: [element.elementId] });
    }
  }
  return lanes.map((lane) => lane.ids);
}

export function sortTracksForTimeline(tracks: TimelineTrack[]): TimelineTrack[] {
  const areaRank: Record<TimelineTrack['area'], number> = { top: 0, center: 1, bottom: 2 };
  return [...tracks].sort((left, right) => areaRank[left.area] - areaRank[right.area] || left.order - right.order);
}

export function normalizePipDisplayRange(range: { startMs: number; endMs: number; sourceDurationMs: number }): {
  startMs: number;
  endMs: number;
  loopWhenOverflow: true;
} {
  if (range.endMs <= range.startMs || range.sourceDurationMs <= 0) throw new Error('PiP ranges must be positive');
  return { startMs: range.startMs, endMs: range.endMs, loopWhenOverflow: true };
}

export type PipPosition = 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';

export function positionPip(position: PipPosition, edgeRatio: number, sizeRatio = 0.32): Pick<TimelineTransform, 'xRatio' | 'yRatio'> {
  if (edgeRatio < 0 || edgeRatio * 2 + sizeRatio > 1) throw new Error('PiP edge distance is out of range');
  const far = Number((1 - edgeRatio - sizeRatio).toFixed(6));
  switch (position) {
    case 'top-left': return { xRatio: edgeRatio, yRatio: edgeRatio };
    case 'top-right': return { xRatio: far, yRatio: edgeRatio };
    case 'bottom-left': return { xRatio: edgeRatio, yRatio: far };
    case 'bottom-right': return { xRatio: far, yRatio: far };
  }
}
