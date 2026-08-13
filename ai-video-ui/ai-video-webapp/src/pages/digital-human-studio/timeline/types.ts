import type {
  TimelineDocument,
  TimelineElement,
  TimelineTrack,
} from '@/services/ai-video/creation-timeline/types';

export type TimelineElementPatch = TimelineElement extends infer Element
  ? Element extends TimelineElement
    ? Partial<Omit<Element, 'elementId' | 'elementType'>>
    : never
  : never;

export type TimelineAction =
  | { type: 'serverLoaded'; timeline: TimelineDocument; revision: string }
  | { type: 'elementSelected'; elementId?: string }
  | { type: 'elementAdded'; trackId: string; element: TimelineElement }
  | { type: 'elementPatched'; elementId: string; patch: TimelineElementPatch }
  | { type: 'elementRemoved'; elementId: string }
  | { type: 'serverNormalized'; timeline: TimelineDocument; revision: string }
  | { type: 'undo' }
  | { type: 'redo' };

export type TimelineEditAction = Extract<
  TimelineAction,
  { type: 'elementAdded' | 'elementPatched' | 'elementRemoved' }
>;

export type TimelineHistoryEntry = {
  action: TimelineEditAction;
  beforeTimeline: TimelineDocument;
  afterTimeline: TimelineDocument;
};

export type TimelineHistory = {
  past: TimelineHistoryEntry[];
  future: TimelineHistoryEntry[];
};

export type TimelineEditorState = {
  timeline: TimelineDocument;
  revision?: string;
  selectedElementId?: string;
  past: TimelineHistoryEntry[];
  future: TimelineHistoryEntry[];
};

export type TimelineLookup = {
  track: TimelineTrack;
  element: TimelineElement;
};

export const EMPTY_TIMELINE: TimelineDocument = {
  schemaVersion: 'timeline-1',
  canvas: {
    width: 1080,
    height: 1920,
    frameRate: 30,
    durationMs: 1,
    safeMarginRatio: 0.05,
  },
  tracks: [],
};
