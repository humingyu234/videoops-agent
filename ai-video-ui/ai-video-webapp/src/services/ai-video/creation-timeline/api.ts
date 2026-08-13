import type { RuoYiAdapter } from '../core/ruoyiAdapter';
import type {
  CreationOutput,
  CreationProject,
  TimelineDraft,
  TimelineSchemaVersion,
  TimelineTaskDetail,
  TimelineVersion,
} from './types';
import {
  parseCreationOutputWire,
  parseTimelineTaskDetailWire,
  parseTimelineVersionWire,
} from './adapter';
import type {
  CreateConflictCopyWire,
  CreateCreationProjectWire,
  CreateTimelineRenderTaskWire,
  SaveTimelineDraftWire,
} from './wire';

export type CreateConflictCopyRequest = CreateConflictCopyWire;
export type CreateCreationProjectRequest = CreateCreationProjectWire;
export type SaveTimelineDraftRequest = SaveTimelineDraftWire;

export type { CreationOutput, TimelineVersion } from './types';

export type SaveTimelineDraftResult = TimelineDraft & {
  replayed: boolean;
  superseded: boolean;
  operationResultRevision?: string;
  operationContentHash?: string;
  currentRevision?: string;
  normalizationChanges: Array<{
    elementId: string;
    changeType: string;
    beforeDigest: string;
    afterDigest: string;
    safeMessage: string;
  }>;
};

export type TimelineVersionPage = { total: number; rows: TimelineVersion[] };

export type CreateTimelineTaskInput = {
  idempotencyKey: string;
  expectedRevision: string;
};

export type CreateImagePromptTaskInput = CreateTimelineTaskInput & {
  sourceSelection: { sourceStartOffset: number; sourceEndOffset: number } | { subtitleElementIds: string[] };
  style: 'photorealistic' | 'cinematic' | 'illustration' | 'minimal';
};

export type CreateFancyTextTaskInput = CreateTimelineTaskInput & {
  sourceSelection: { sourceStartOffset: number; sourceEndOffset: number } | { subtitleElementIds: string[] };
  animationIntensity: 'subtle' | 'normal' | 'strong';
};

export type CreateSubtitleAlignmentTaskInput = CreateTimelineTaskInput & {
  subtitleElementIds: string[];
};

export interface CreationTimelineApi {
  createProject(request: CreateCreationProjectRequest): Promise<CreationProject>;
  getProject(projectId: string): Promise<CreationProject>;
  updateProjectTitle(projectId: string, projectTitle: string): Promise<CreationProject>;
  getDraft(projectId: string): Promise<TimelineDraft>;
  saveDraft(projectId: string, request: SaveTimelineDraftRequest): Promise<SaveTimelineDraftResult>;
  listVersions(projectId: string, input: { pageNum: number; pageSize: number }): Promise<TimelineVersionPage>;
  createVersion(projectId: string, request: CreateTimelineTaskInput): Promise<TimelineVersion>;
  restoreVersion(projectId: string, versionId: string, request: CreateTimelineTaskInput): Promise<SaveTimelineDraftResult>;
  createConflictCopy(projectId: string, request: CreateConflictCopyRequest): Promise<TimelineVersion>;
  getLatestOutput(projectId: string): Promise<CreationOutput>;
  createImagePromptTask(projectId: string, request: CreateImagePromptTaskInput): Promise<TimelineTaskDetail>;
  createFancyTextSuggestionTask(projectId: string, request: CreateFancyTextTaskInput): Promise<TimelineTaskDetail>;
  createSubtitleAlignmentTask(projectId: string, request: CreateSubtitleAlignmentTaskInput): Promise<TimelineTaskDetail>;
  createRenderTask(projectId: string, request: CreateTimelineRenderTaskWire): Promise<TimelineTaskDetail>;
}

function projectPath(projectId: string): string {
  return `/api/studio/creation-projects/${encodeURIComponent(projectId)}`;
}

function assertExactRequest(value: unknown, expectedKeys: readonly string[]): void {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new Error('Invalid timeline request: request must be an object');
  }
  const keys = Object.keys(value as Record<string, unknown>);
  if (keys.some((key) => !expectedKeys.includes(key))) {
    throw new Error('Invalid timeline request: request contains an unknown field');
  }
}

function assertExactOutputConfig(value: unknown): void {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new Error('Invalid timeline request: outputConfig must be an object');
  }
  const outputConfig = value as Record<string, unknown>;
  const expectedKeys = ['resolutionPreset', 'frameRate', 'qualityPreset'];
  if (Object.keys(outputConfig).some((key) => !expectedKeys.includes(key))) {
    throw new Error('Invalid timeline request: outputConfig contains an unknown field');
  }
  const missing = expectedKeys.find((key) => !Object.hasOwn(outputConfig, key));
  if (missing) {
    throw new Error(`Invalid timeline request: outputConfig.${missing} is required`);
  }
  if (outputConfig.resolutionPreset !== 'match_canvas') {
    throw new Error('Invalid timeline request: outputConfig.resolutionPreset is invalid');
  }
  if (outputConfig.frameRate !== 30) {
    throw new Error('Invalid timeline request: outputConfig.frameRate is invalid');
  }
  if (outputConfig.qualityPreset !== 'standard' && outputConfig.qualityPreset !== 'high') {
    throw new Error('Invalid timeline request: outputConfig.qualityPreset is invalid');
  }
}

function versionPath(projectId: string): string {
  return `${projectPath(projectId)}/timeline-versions`;
}

export function createCreationTimelineApi(adapter: RuoYiAdapter): CreationTimelineApi {
  return {
    createProject(request) {
      assertExactRequest(request, ['sourceType', 'sourceId', 'projectTitle', 'idempotencyKey']);
      return adapter.request<CreationProject>('/api/studio/creation-projects', { method: 'POST', data: request });
    },
    getProject(projectId) {
      return adapter.request<CreationProject>(projectPath(projectId), { method: 'GET' });
    },
    updateProjectTitle(projectId, projectTitle) {
      return adapter.request<CreationProject>(projectPath(projectId), { method: 'PUT', data: { projectTitle } });
    },
    getDraft(projectId) {
      return adapter.request<TimelineDraft>(`${projectPath(projectId)}/timeline-draft`, { method: 'GET' });
    },
    saveDraft(projectId, request) {
      assertExactRequest(request, ['idempotencyKey', 'expectedRevision', 'schemaVersion', 'timeline']);
      return adapter.request<SaveTimelineDraftResult>(`${projectPath(projectId)}/timeline-draft`, { method: 'PUT', data: request });
    },
    listVersions(projectId, input) {
      const query = new URLSearchParams({ pageNum: String(input.pageNum), pageSize: String(input.pageSize) });
      return adapter.request<TimelineVersionPage>(`${versionPath(projectId)}?${query.toString()}`, { method: 'GET' });
    },
    createVersion(projectId, request) {
      assertExactRequest(request, ['idempotencyKey', 'expectedRevision']);
      return adapter.request<unknown>(versionPath(projectId), { method: 'POST', data: request }).then(parseTimelineVersionWire);
    },
    restoreVersion(projectId, versionId, request) {
      assertExactRequest(request, ['idempotencyKey', 'expectedRevision']);
      return adapter.request<SaveTimelineDraftResult>(`${versionPath(projectId)}/${encodeURIComponent(versionId)}/restorations`, { method: 'POST', data: request });
    },
    async createConflictCopy(projectId, request) {
      assertExactRequest(request, ['idempotencyKey', 'baseRevision', 'schemaVersion', 'timeline']);
      return parseTimelineVersionWire(await adapter.request<unknown>(
        `${versionPath(projectId)}/conflict-copies`,
        { method: 'POST', data: request },
      ));
    },
    async getLatestOutput(projectId) {
      return parseCreationOutputWire(await adapter.request<unknown>(
        `${projectPath(projectId)}/outputs/latest`,
        { method: 'GET' },
      ));
    },
    async createImagePromptTask(projectId, request) {
      return parseTimelineTaskDetailWire(await adapter.request<unknown>(
        `${projectPath(projectId)}/image-prompt-tasks`,
        { method: 'POST', data: request },
      ));
    },
    async createFancyTextSuggestionTask(projectId, request) {
      return parseTimelineTaskDetailWire(await adapter.request<unknown>(
        `${projectPath(projectId)}/fancy-text-suggestion-tasks`,
        { method: 'POST', data: request },
      ));
    },
    async createSubtitleAlignmentTask(projectId, request) {
      return parseTimelineTaskDetailWire(await adapter.request<unknown>(
        `${projectPath(projectId)}/subtitle-alignment-tasks`,
        { method: 'POST', data: request },
      ));
    },
    async createRenderTask(projectId, request) {
      assertExactRequest(request, ['idempotencyKey', 'expectedRevision', 'outputConfig']);
      assertExactOutputConfig(request.outputConfig);
      return parseTimelineTaskDetailWire(await adapter.request<unknown>(
        `${projectPath(projectId)}/render-tasks`,
        { method: 'POST', data: request },
      ));
    },
  };
}
