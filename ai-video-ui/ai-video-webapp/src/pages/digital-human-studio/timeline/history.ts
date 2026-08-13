import type { TimelineDocument } from '@/services/ai-video/creation-timeline/types';
import type { TimelineEditAction, TimelineHistory } from './types';

function cloneTimeline(timeline: TimelineDocument): TimelineDocument {
  return structuredClone(timeline);
}

function assertHistoryValueIsSafe(value: unknown): void {
  if (typeof value === 'string') {
    if (value.startsWith('blob:')) throw new Error('Timeline history cannot store temporary URLs');
    return;
  }
  if (value instanceof Blob) throw new Error('Timeline history cannot store Blobs');
  if (!value || typeof value !== 'object') return;
  for (const nestedValue of Object.values(value)) assertHistoryValueIsSafe(nestedValue);
}

export function createTimelineHistory(_baseline: TimelineDocument): TimelineHistory {
  return { past: [], future: [] };
}

export function appendHistory(
  history: TimelineHistory,
  beforeTimeline: TimelineDocument,
  afterTimeline: TimelineDocument,
  action: TimelineEditAction,
): TimelineHistory {
  assertHistoryValueIsSafe(beforeTimeline);
  assertHistoryValueIsSafe(afterTimeline);
  assertHistoryValueIsSafe(action);
  return {
    past: [...history.past, { action, beforeTimeline: cloneTimeline(beforeTimeline), afterTimeline: cloneTimeline(afterTimeline) }],
    future: [],
  };
}

export function undoHistory(history: TimelineHistory, currentTimeline: TimelineDocument): { timeline: TimelineDocument; history: TimelineHistory } {
  const entry = history.past.at(-1);
  if (!entry) return { timeline: currentTimeline, history };
  return {
    timeline: cloneTimeline(entry.beforeTimeline),
    history: { past: history.past.slice(0, -1), future: [entry, ...history.future] },
  };
}

export function redoHistory(history: TimelineHistory, currentTimeline: TimelineDocument): { timeline: TimelineDocument; history: TimelineHistory } {
  const entry = history.future[0];
  if (!entry) return { timeline: currentTimeline, history };
  return {
    timeline: cloneTimeline(entry.afterTimeline),
    history: { past: [...history.past, entry], future: history.future.slice(1) },
  };
}

export function rebaselineHistory(_history: TimelineHistory, baseline: TimelineDocument): TimelineHistory {
  return createTimelineHistory(baseline);
}
