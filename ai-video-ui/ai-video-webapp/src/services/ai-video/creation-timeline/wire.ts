import type {
  TimelineDocument,
  TimelineOutputConfig,
  TimelineSchemaVersion,
} from './types';

export type CreateCreationProjectWire = {
  sourceType: 'digital_human_job';
  sourceId: string;
  projectTitle?: string;
  idempotencyKey: string;
};

export type SaveTimelineDraftWire = {
  idempotencyKey: string;
  expectedRevision: string;
  schemaVersion: TimelineSchemaVersion;
  timeline: TimelineDocument;
};

export type CreateConflictCopyWire = {
  idempotencyKey: string;
  baseRevision: string;
  schemaVersion: TimelineSchemaVersion;
  timeline: TimelineDocument;
};

export type CreateTimelineRenderTaskWire = {
  idempotencyKey: string;
  expectedRevision: string;
  outputConfig: TimelineOutputConfig;
};
