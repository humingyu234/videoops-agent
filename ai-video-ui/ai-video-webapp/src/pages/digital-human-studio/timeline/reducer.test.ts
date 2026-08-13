import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { parseTimelineDraftWire } from '@/services/ai-video/creation-timeline/adapter';
import type { TimelineDocument, TimelineElement } from '@/services/ai-video/creation-timeline/types';
import {
  copyElement,
  moveElement,
  setElementEnabled,
  splitElement,
  trimElement,
} from './commands';
import { createTimelineEditorState, timelineReducer } from './reducer';

const contractDirectory = resolve(process.cwd(), '../../docs/contracts/creation-timeline');

async function loadCanonicalTimeline(): Promise<TimelineDocument> {
  const wire = JSON.parse(
    await readFile(resolve(contractDirectory, 'timeline-draft.example.json'), 'utf8'),
  ) as unknown;
  return parseTimelineDraftWire(wire).timeline;
}

function findElement(timeline: TimelineDocument, elementId: string): TimelineElement {
  const element = timeline.tracks.flatMap((track) => track.elements).find((item) => item.elementId === elementId);
  if (!element) throw new Error(`Missing test element ${elementId}`);
  return element;
}

function findTrack(timeline: TimelineDocument, trackId: string) {
  const track = timeline.tracks.find((item) => item.trackId === trackId);
  if (!track) throw new Error(`Missing test track ${trackId}`);
  return track;
}

describe('timeline reducer', () => {
  it('loads the canonical server timeline and keeps one shared selection id', async () => {
    const timeline = await loadCanonicalTimeline();
    let state = timelineReducer(createTimelineEditorState(), {
      type: 'serverLoaded',
      timeline,
      revision: '3',
    });

    state = timelineReducer(state, { type: 'elementSelected', elementId: 'image_0001' });

    expect(state.timeline).toEqual(timeline);
    expect(state.revision).toBe('3');
    expect(state.selectedElementId).toBe('image_0001');
    expect(Object.keys(state)).not.toContain('previewSelectedElementId');
    expect(Object.keys(state)).not.toContain('trackSelectedElementId');

    state = timelineReducer(state, { type: 'elementSelected' });
    expect(state.selectedElementId).toBeUndefined();
  });

  it('adds, patches, removes, and replaces local edits with the server-normalized result', async () => {
    const timeline = await loadCanonicalTimeline();
    const image = findElement(timeline, 'image_0001');
    let state = timelineReducer(createTimelineEditorState(timeline, '3'), {
      type: 'elementAdded',
      trackId: 'track_image_01',
      element: { ...image, elementId: 'image_0002', startMs: 11000, endMs: 13000 },
    });
    state = timelineReducer(state, {
      type: 'elementPatched',
      elementId: 'image_0002',
      patch: { fitMode: 'cover', crop: { xRatio: 0.1, yRatio: 0, widthRatio: 0.9, heightRatio: 1 }, fade: { fadeInMs: 120, fadeOutMs: 300 } },
    });

    expect(findElement(state.timeline, 'image_0002')).toMatchObject({
      fitMode: 'cover',
      crop: { xRatio: 0.1, yRatio: 0, widthRatio: 0.9, heightRatio: 1 },
      fade: { fadeInMs: 120, fadeOutMs: 300 },
    });

    state = timelineReducer(state, { type: 'elementRemoved', elementId: 'image_0002' });
    expect(state.timeline.tracks.find((track) => track.trackId === 'track_image_01')?.elements).toHaveLength(1);

    const normalized = structuredClone(timeline);
    findTrack(normalized, 'track_image_01').elements[0].label = 'Server canonical label';
    state = timelineReducer(state, { type: 'serverNormalized', timeline: normalized, revision: '4' });

    expect(state.revision).toBe('4');
    expect(findElement(state.timeline, 'image_0001').label).toBe('Server canonical label');
    expect(state.past).toEqual([]);
  });

  it('creates serializable reducer actions for copy, split, lock, mute, move, trim, layer, and track changes', async () => {
    const timeline = await loadCanonicalTimeline();
    const destinationTrack = {
      ...findTrack(timeline, 'track_image_01'),
      trackId: 'track_image_02',
      order: 4,
      elements: [],
    };
    let state = createTimelineEditorState({ ...timeline, tracks: [...timeline.tracks, destinationTrack] }, '3');
    const copied = copyElement(state.timeline, 'image_0001', 'image_copy_0001');
    state = timelineReducer(state, copied);
    const splitActions = splitElement(state.timeline, 'image_copy_0001', 'image_copy_0002', 8000);
    state = timelineReducer(state, splitActions[0]);
    state = timelineReducer(state, splitActions[1]);
    state = timelineReducer(state, { type: 'elementPatched', elementId: 'image_copy_0001', patch: { locked: true, enabled: false, zIndex: 210 } });
    for (const action of moveElement(state.timeline, 'image_copy_0001', 'track_image_02', 8500)) {
      state = timelineReducer(state, action);
    }
    state = timelineReducer(state, trimElement(state.timeline, 'image_copy_0001', 8500, 9000));

    const moved = findElement(state.timeline, 'image_copy_0001');
    expect(moved).toMatchObject({ elementId: 'image_copy_0001', locked: true, enabled: false, zIndex: 210, startMs: 8500, endMs: 9000 });
    expect(state.timeline.tracks.find((track) => track.trackId === 'track_image_02')?.elements.map((element) => element.elementId)).toContain('image_copy_0001');
    expect(state.past.every((entry) => JSON.stringify(entry).includes('blob:') === false)).toBe(true);
  });

  it('does not allow the main video to leave the centre track', async () => {
    const timeline = await loadCanonicalTimeline();
    expect(() => moveElement(timeline, 'main_video_0001', 'track_image_01', 1)).toThrow(
      'main_video must remain on its centre track',
    );
  });

  it('preserves the C0 fixed element defaults when command patches do not change them', async () => {
    const timeline = await loadCanonicalTimeline();
    let state = createTimelineEditorState(timeline, '3');
    state = timelineReducer(state, setElementEnabled('pip_0001', true));

    expect(findElement(state.timeline, 'pip_0001')).toMatchObject({
      loopWhenOverflow: true,
      audioEnabled: false,
      crop: { xRatio: 0, yRatio: 0, widthRatio: 1, heightRatio: 1 },
      fade: { fadeInMs: 0, fadeOutMs: 0 },
    });
    expect(findElement(state.timeline, 'audio_bgm_0001')).toMatchObject({
      loopWhenOverflow: true,
      duckingEnabled: true,
      volumeRatio: 0.3,
      targetGainRatio: 0.35,
      attackMs: 120,
      releaseMs: 400,
    });
  });

  it('rejects patches that would violate C0 PiP and background music fixed fields', async () => {
    const state = createTimelineEditorState(await loadCanonicalTimeline(), '3');

    expect(() => timelineReducer(state, {
      type: 'elementPatched', elementId: 'pip_0001', patch: { loopWhenOverflow: false },
    })).toThrow('fixed field');
    expect(() => timelineReducer(state, {
      type: 'elementPatched', elementId: 'audio_bgm_0001', patch: { duckingEnabled: false },
    })).toThrow('fixed field');
  });
});
