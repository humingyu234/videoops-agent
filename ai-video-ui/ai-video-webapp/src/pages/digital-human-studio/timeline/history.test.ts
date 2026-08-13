import { describe, expect, it } from 'vitest';
import type { TimelineDocument } from '@/services/ai-video/creation-timeline/types';
import {
  appendHistory,
  createTimelineHistory,
  rebaselineHistory,
  redoHistory,
  undoHistory,
} from './history';

const timeline = (_label: string): TimelineDocument => ({
  schemaVersion: 'timeline-1',
  canvas: { width: 1080, height: 1920, frameRate: 30, durationMs: 1000, safeMarginRatio: 0.05 },
  tracks: [],
}) as TimelineDocument;

describe('timeline history', () => {
  it('undoes and redoes local timeline commands', () => {
    const initial = timeline('initial');
    const edited = { ...timeline('edited'), canvas: { ...timeline('edited').canvas, durationMs: 1200 } };
    const history = appendHistory(createTimelineHistory(initial), initial, edited, { type: 'elementPatched', elementId: 'e1', patch: { label: 'changed' } });

    const undone = undoHistory(history, edited);
    expect(undone.timeline).toEqual(initial);
    expect(redoHistory(undone.history, undone.timeline).timeline).toEqual(edited);
  });

  it('rebaselines history on server load or save confirmation', () => {
    const initial = timeline('initial');
    const edited = { ...timeline('edited'), canvas: { ...timeline('edited').canvas, durationMs: 1200 } };
    const history = appendHistory(createTimelineHistory(initial), initial, edited, { type: 'elementPatched', elementId: 'e1', patch: { label: 'changed' } });

    expect(rebaselineHistory(history, edited)).toEqual(createTimelineHistory(edited));
  });

  it('stores only serializable command snapshots instead of blobs, temporary URLs, or React state', () => {
    const initial = timeline('initial');
    const edited = { ...timeline('edited'), canvas: { ...timeline('edited').canvas, durationMs: 1200 } };
    const history = appendHistory(createTimelineHistory(initial), initial, edited, { type: 'elementPatched', elementId: 'e1', patch: { label: 'changed' } });
    const serialized = JSON.stringify(history);

    expect(serialized).not.toContain('blob:');
    expect(serialized).not.toContain('react');
    expect(history.past[0]).toMatchObject({ action: { type: 'elementPatched', elementId: 'e1' } });
  });
});
