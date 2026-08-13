import type { TimelineDocument, TimelineElement, TimelineTrack } from '@/services/ai-video/creation-timeline/types';
import type { TimelineLookup } from './types';

export function findTimelineTrack(timeline: TimelineDocument, trackId: string): TimelineTrack | undefined {
  return timeline.tracks.find((track) => track.trackId === trackId);
}

export function findTimelineElement(timeline: TimelineDocument, elementId: string): TimelineLookup | undefined {
  for (const track of timeline.tracks) {
    const element = track.elements.find((item) => item.elementId === elementId);
    if (element) return { track, element };
  }
  return undefined;
}

export function hasTimelineElement(timeline: TimelineDocument, elementId: string | undefined): boolean {
  return Boolean(elementId && findTimelineElement(timeline, elementId));
}

export function getTimelineElements(timeline: TimelineDocument): TimelineElement[] {
  return timeline.tracks.flatMap((track) => track.elements);
}
