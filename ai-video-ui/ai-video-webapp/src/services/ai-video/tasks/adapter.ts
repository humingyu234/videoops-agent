import {
  assertRecord,
  readArray,
  readDecimalString,
  readEnum,
  readString,
} from '../core/wire';
import type {
  AiTaskVO,
  TaskDetail,
  TaskKind,
  TaskListItem,
  TaskPage,
} from './types';

type WireRecord = Record<string, unknown>;

function assertOnlyKnownKeys(
  record: WireRecord,
  allowed: readonly string[],
  field: string,
): void {
  if (Object.keys(record).some((key) => !allowed.includes(key))) {
    throw new Error(`Invalid wire response: ${field} contains an unknown field`);
  }
}

const aiTaskKeys = [
  'taskId',
  'taskType',
  'resourceType',
  'resourceId',
  'projectId',
  'draftRevision',
  'inputVersionId',
  'status',
  'stage',
  'progress',
  'canCancel',
  'canRetry',
  'resultAssetId',
  'resultSchemaVersion',
  'result',
  'errorCode',
  'errorSummary',
  'createdAt',
  'updatedAt',
  'startedAt',
  'finishedAt',
] as const;
const aiTaskRequiredKeys = [
  'taskId',
  'taskType',
  'resourceType',
  'resourceId',
  'status',
  'stage',
  'progress',
  'canCancel',
  'canRetry',
  'createdAt',
] as const;
const taskCodePattern = /^[a-z][a-z0-9_]{1,63}$/;
const taskStatuses = [
  'pending',
  'queued',
  'running',
  'success',
  'failed',
  'cancelled',
] as const;

function assertRequiredKeys(record: WireRecord, required: readonly string[], field: string): void {
  if (required.some((key) => !Object.hasOwn(record, key))) {
    throw new Error(`Invalid wire response: ${field} is missing a required field`);
  }
}

function readPositiveDecimalId(record: WireRecord, key: string): string {
  const value = readDecimalString(record, key);
  if (!/^[1-9]\d*$/.test(value)) {
    throw new Error(`Invalid wire response: ${key} must be a positive decimal string`);
  }
  return value;
}

function readTaskCode(record: WireRecord, key: string): string {
  const value = readString(record, key);
  if (!taskCodePattern.test(value)) {
    throw new Error(`Invalid wire response: ${key} must be a lawful task code`);
  }
  return value;
}

function readOptionalString(record: WireRecord, key: string): string | undefined {
  return Object.hasOwn(record, key) && record[key] !== null
    ? readString(record, key)
    : undefined;
}

function readOptionalPositiveDecimalId(record: WireRecord, key: string): string | undefined {
  return Object.hasOwn(record, key) && record[key] !== null
    ? readPositiveDecimalId(record, key)
    : undefined;
}

export function parseAiTaskVOWire(value: unknown): AiTaskVO {
  const record = assertRecord(value, 'aiTask');
  assertOnlyKnownKeys(record, aiTaskKeys, 'aiTask');
  assertRequiredKeys(record, aiTaskRequiredKeys, 'aiTask');

  if (Object.hasOwn(record, 'result') && record.result !== null) {
    assertRecord(record.result, 'result');
  }

  const task: AiTaskVO = {
    taskId: readPositiveDecimalId(record, 'taskId'),
    taskType: readTaskCode(record, 'taskType'),
    resourceType: readTaskCode(record, 'resourceType'),
    resourceId: readPositiveDecimalId(record, 'resourceId'),
    status: readEnum(record, 'status', taskStatuses),
    stage: readString(record, 'stage'),
    progress: readProgress(record),
    canCancel: readBoolean(record, 'canCancel'),
    canRetry: readBoolean(record, 'canRetry'),
    createdAt: readString(record, 'createdAt'),
  };

  const inputVersionId = readOptionalPositiveDecimalId(record, 'inputVersionId');
  const resultAssetId = readOptionalPositiveDecimalId(record, 'resultAssetId');
  const resultSchemaVersion = readOptionalString(record, 'resultSchemaVersion');
  const errorCode = readOptionalString(record, 'errorCode');
  const errorSummary = readOptionalString(record, 'errorSummary');
  const startedAt = readOptionalString(record, 'startedAt');
  const finishedAt = readOptionalString(record, 'finishedAt');
  if (inputVersionId) task.inputVersionId = inputVersionId;
  if (resultAssetId) task.resultAssetId = resultAssetId;
  if (resultSchemaVersion) task.resultSchemaVersion = resultSchemaVersion;
  if (errorCode) task.errorCode = errorCode;
  if (errorSummary) task.errorSummary = errorSummary;
  if (startedAt) task.startedAt = startedAt;
  if (finishedAt) task.finishedAt = finishedAt;
  return task;
}

function readProgress(record: WireRecord): number {
  const value = record.progress;
  if (
    typeof value !== 'number' ||
    !Number.isSafeInteger(value) ||
    value < 0 ||
    value > 100
  ) {
    throw new Error('Invalid wire response: progress must be an in-range integer');
  }
  return value;
}

function readBoolean(record: WireRecord, key: string): boolean {
  const value = record[key];
  if (typeof value !== 'boolean') {
    throw new Error(`Invalid wire response: ${key} must be a boolean`);
  }
  return value;
}

function taskKind(taskType: string): TaskKind {
  const kinds: Record<string, TaskKind> = {
    timeline_image_prompt_generate: 'image-prompt',
    timeline_fancy_text_suggest: 'fancy-text',
    timeline_subtitle_align: 'subtitle-alignment',
    timeline_render: 'render',
  };
  return kinds[taskType] ?? 'unknown';
}

export function parseTaskDetailWire(value: unknown): TaskDetail {
  const task = parseAiTaskVOWire(value);
  const record = assertRecord(value, 'aiTask');
  const result = Object.hasOwn(record, 'result') && record.result !== null
    ? assertRecord(record.result, 'result')
    : undefined;
  return {
    ...task,
    kind: taskKind(task.taskType),
    ...(result ? { result } : {}),
  };
}

export function parseTaskListItemWire(value: unknown): TaskListItem {
  const { result: _result, ...task } = parseTaskDetailWire(value);
  return task;
}

export function parseTaskPageWire(value: unknown): TaskPage {
  const record = assertRecord(value, 'taskPage');
  assertOnlyKnownKeys(record, ['total', 'rows'], 'taskPage');
  if (!Number.isSafeInteger(record.total) || (record.total as number) < 0) {
    throw new Error('Invalid wire response: taskPage.total must be a non-negative integer');
  }
  return {
    total: record.total as number,
    rows: readArray(record, 'rows', parseTaskListItemWire),
  };
}
