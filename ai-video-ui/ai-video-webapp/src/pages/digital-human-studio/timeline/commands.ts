import type { TimelineDocument, TimelineElement } from '@/services/ai-video/creation-timeline/types';
import { MIN_CLIP_DURATION_MS } from './geometry';
import { findTimelineElement, findTimelineTrack } from './selectors';
import type { TimelineAction } from './types';

function cloneElement<Element extends TimelineElement>(element: Element): Element {
  return structuredClone(element);
}

export function copyElement(timeline: TimelineDocument, elementId: string, copiedElementId: string): TimelineAction {
  const lookup = findTimelineElement(timeline, elementId);
  if (!lookup) throw new Error(`Timeline element ${elementId} does not exist`);
  return { type: 'elementAdded', trackId: lookup.track.trackId, element: { ...cloneElement(lookup.element), elementId: copiedElementId } };
}

export function splitElement(timeline: TimelineDocument, elementId: string, copiedElementId: string, splitAtMs: number): [TimelineAction, TimelineAction] {
  const lookup = findTimelineElement(timeline, elementId);
  if (!lookup) throw new Error(`Timeline element ${elementId} does not exist`);
  if (splitAtMs - lookup.element.startMs < MIN_CLIP_DURATION_MS || lookup.element.endMs - splitAtMs < MIN_CLIP_DURATION_MS) {
    throw new Error('Split point violates the minimum clip duration');
  }
  return [
    { type: 'elementPatched', elementId, patch: { endMs: splitAtMs } },
    { type: 'elementAdded', trackId: lookup.track.trackId, element: { ...cloneElement(lookup.element), elementId: copiedElementId, startMs: splitAtMs } },
  ];
}

export function setElementEnabled(elementId: string, enabled: boolean): TimelineAction {
  return { type: 'elementPatched', elementId, patch: { enabled } };
}

export function moveElement(timeline: TimelineDocument, elementId: string, destinationTrackId: string, startMs: number): [TimelineAction, TimelineAction] {
  const lookup = findTimelineElement(timeline, elementId);
  const destinationTrack = findTimelineTrack(timeline, destinationTrackId);
  if (!lookup || !destinationTrack) throw new Error('Timeline element or destination track does not exist');
  if (lookup.element.elementType === 'main_video' || destinationTrack.area !== lookup.track.area || destinationTrack.trackType !== lookup.track.trackType) {
    throw new Error('main_video must remain on its centre track');
  }
  const duration = lookup.element.endMs - lookup.element.startMs;
  return [
    { type: 'elementRemoved', elementId },
    { type: 'elementAdded', trackId: destinationTrackId, element: { ...cloneElement(lookup.element), startMs, endMs: startMs + duration } },
  ];
}

export function trimElement(timeline: TimelineDocument, elementId: string, startMs: number, endMs: number): TimelineAction {
  const lookup = findTimelineElement(timeline, elementId);
  if (!lookup) throw new Error(`Timeline element ${elementId} does not exist`);
  if (startMs < 0 || endMs > timeline.canvas.durationMs || endMs - startMs < MIN_CLIP_DURATION_MS) {
    throw new Error('Trim range is outside the project bounds');
  }
  return { type: 'elementPatched', elementId, patch: { startMs, endMs } };
}
