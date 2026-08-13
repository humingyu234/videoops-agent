import type { TimelineDocument, TimelineElement, TimelineTrack } from '@/services/ai-video/creation-timeline/types';
import { appendHistory, createTimelineHistory, redoHistory, rebaselineHistory, undoHistory } from './history';
import { findTimelineElement, findTimelineTrack, hasTimelineElement } from './selectors';
import { EMPTY_TIMELINE, type TimelineAction, type TimelineEditorState, type TimelineElementPatch } from './types';

function assertNever(value: never): never {
  throw new Error(`Unhandled timeline action: ${JSON.stringify(value)}`);
}

function cloneTimeline(timeline: TimelineDocument): TimelineDocument {
  return structuredClone(timeline);
}

function patchElement<Element extends TimelineElement>(element: Element, patch: TimelineElementPatch): Element {
  return { ...element, ...patch } as Element;
}

function replaceTrack(timeline: TimelineDocument, nextTrack: TimelineTrack): TimelineDocument {
  return {
    ...timeline,
    tracks: timeline.tracks.map((track) => track.trackId === nextTrack.trackId ? nextTrack : track),
  };
}

function assertElementMatchesTrack(track: TimelineTrack, element: TimelineElement): void {
  const elementTypeByTrack = {
    fancy_text: 'fancy_text',
    subtitle: 'subtitle',
    visual_effect: 'visual_effect',
    image_overlay: 'image_overlay',
    pip_video: 'pip_video',
    main_video: 'main_video',
    primary_audio: 'audio',
    background_music: 'audio',
    sound_effect: 'audio',
  } as const;
  if (element.elementType !== elementTypeByTrack[track.trackType]) {
    throw new Error('Timeline element type does not match its track');
  }
  if (element.elementType === 'audio' && element.usageType !== track.trackType) {
    throw new Error('Audio usage type does not match its track');
  }
}

function assertFixedElementFields(element: TimelineElement): void {
  if (element.elementType === 'pip_video' && (!element.loopWhenOverflow || element.audioEnabled)) {
    throw new Error('Timeline patch violates a C0 fixed field');
  }
  if (element.elementType === 'audio') {
    const isBackgroundMusic = element.usageType === 'background_music';
    const hasCanonicalBackgroundMusicValues = element.volumeRatio === 0.3
      && element.loopWhenOverflow
      && element.duckingEnabled
      && element.targetGainRatio === 0.35
      && element.attackMs === 120
      && element.releaseMs === 400;
    if ((isBackgroundMusic && !hasCanonicalBackgroundMusicValues)
      || (!isBackgroundMusic && (element.loopWhenOverflow || element.duckingEnabled))) {
      throw new Error('Timeline patch violates a C0 fixed field');
    }
  }
}

function addElement(timeline: TimelineDocument, trackId: string, element: TimelineElement): TimelineDocument {
  const track = findTimelineTrack(timeline, trackId);
  if (!track) throw new Error(`Timeline track ${trackId} does not exist`);
  if (findTimelineElement(timeline, element.elementId)) throw new Error(`Timeline element ${element.elementId} already exists`);
  assertElementMatchesTrack(track, element);
  assertFixedElementFields(element);
  return replaceTrack(timeline, { ...track, elements: [...track.elements, structuredClone(element)] });
}

function applyElementPatch(timeline: TimelineDocument, elementId: string, patch: TimelineElementPatch): TimelineDocument {
  const lookup = findTimelineElement(timeline, elementId);
  if (!lookup) throw new Error(`Timeline element ${elementId} does not exist`);
  const nextElement = patchElement(lookup.element, patch);
  if (nextElement.startMs < 0 || nextElement.endMs > timeline.canvas.durationMs || nextElement.startMs >= nextElement.endMs) {
    throw new Error('Timeline patch creates an invalid range');
  }
  assertElementMatchesTrack(lookup.track, nextElement);
  assertFixedElementFields(nextElement);
  return replaceTrack(timeline, {
    ...lookup.track,
    elements: lookup.track.elements.map((element) => element.elementId === elementId ? nextElement : element),
  });
}

function removeElement(timeline: TimelineDocument, elementId: string): TimelineDocument {
  const lookup = findTimelineElement(timeline, elementId);
  if (!lookup) throw new Error(`Timeline element ${elementId} does not exist`);
  if (lookup.track.trackType === 'main_video' || lookup.track.elements.length === 1) {
    throw new Error('The final required timeline element cannot be removed');
  }
  return replaceTrack(timeline, {
    ...lookup.track,
    elements: lookup.track.elements.filter((element) => element.elementId !== elementId),
  });
}

function withEdit(
  state: TimelineEditorState,
  action: Extract<TimelineAction, { type: 'elementAdded' | 'elementPatched' | 'elementRemoved' }>,
  nextTimeline: TimelineDocument,
): TimelineEditorState {
  const history = appendHistory({ past: state.past, future: state.future }, state.timeline, nextTimeline, action);
  return {
    ...state,
    timeline: nextTimeline,
    past: history.past,
    future: history.future,
    selectedElementId: hasTimelineElement(nextTimeline, state.selectedElementId) ? state.selectedElementId : undefined,
  };
}

export function createTimelineEditorState(timeline: TimelineDocument = EMPTY_TIMELINE, revision?: string): TimelineEditorState {
  const history = createTimelineHistory(timeline);
  return { timeline: cloneTimeline(timeline), revision, past: history.past, future: history.future };
}

export function timelineReducer(state: TimelineEditorState, action: TimelineAction): TimelineEditorState {
  switch (action.type) {
    case 'serverLoaded': {
      const history = rebaselineHistory({ past: state.past, future: state.future }, action.timeline);
      return { timeline: cloneTimeline(action.timeline), revision: action.revision, past: history.past, future: history.future };
    }
    case 'elementSelected':
      return {
        ...state,
        selectedElementId: action.elementId && hasTimelineElement(state.timeline, action.elementId) ? action.elementId : undefined,
      };
    case 'elementAdded':
      return withEdit(state, action, addElement(state.timeline, action.trackId, action.element));
    case 'elementPatched':
      return withEdit(state, action, applyElementPatch(state.timeline, action.elementId, action.patch));
    case 'elementRemoved':
      return withEdit(state, action, removeElement(state.timeline, action.elementId));
    case 'serverNormalized': {
      const history = rebaselineHistory({ past: state.past, future: state.future }, action.timeline);
      return {
        timeline: cloneTimeline(action.timeline),
        revision: action.revision,
        past: history.past,
        future: history.future,
        selectedElementId: hasTimelineElement(action.timeline, state.selectedElementId) ? state.selectedElementId : undefined,
      };
    }
    case 'undo': {
      const result = undoHistory({ past: state.past, future: state.future }, state.timeline);
      return {
        ...state,
        timeline: result.timeline,
        past: result.history.past,
        future: result.history.future,
        selectedElementId: hasTimelineElement(result.timeline, state.selectedElementId) ? state.selectedElementId : undefined,
      };
    }
    case 'redo': {
      const result = redoHistory({ past: state.past, future: state.future }, state.timeline);
      return {
        ...state,
        timeline: result.timeline,
        past: result.history.past,
        future: result.history.future,
        selectedElementId: hasTimelineElement(result.timeline, state.selectedElementId) ? state.selectedElementId : undefined,
      };
    }
    default:
      return assertNever(action);
  }
}
