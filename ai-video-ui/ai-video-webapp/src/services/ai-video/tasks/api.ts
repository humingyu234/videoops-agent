import { getRuntimeRuoYiAdapter } from '../core/runtimeRuoYiAdapter';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';
import {
  parseTaskDetailWire,
  parseTaskPageWire,
} from './adapter';
import type {
  TaskActionRequest,
  TaskDetail,
  TaskListParams,
  TaskPage,
} from './types';

export interface TasksApi {
  cancel(taskId: string, request: TaskActionRequest): Promise<TaskDetail>;
  get(taskId: string, signal?: AbortSignal): Promise<TaskDetail>;
  list(input: TaskListParams, signal?: AbortSignal): Promise<TaskPage>;
  retry(taskId: string, request: TaskActionRequest): Promise<TaskDetail>;
}

const TASK_ID_PATTERN = /^[1-9]\d*$/;
const TASK_TYPE_PATTERN = /^[a-z][a-z0-9_]{1,63}$/;
const taskStatuses = [
  'pending',
  'queued',
  'running',
  'success',
  'failed',
  'cancelled',
] as const;

function assertExactKeys(value: unknown, keys: readonly string[]): void {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new Error('Invalid task request: request must be an object');
  }
  if (Object.keys(value).some((key) => !keys.includes(key))) {
    throw new Error('Invalid task request: request contains an unknown field');
  }
}

function assertTaskId(taskId: string): void {
  if (!TASK_ID_PATTERN.test(taskId)) {
    throw new Error('Invalid task request: taskId must be a positive decimal string');
  }
}

function assertActionRequest(request: TaskActionRequest): void {
  assertExactKeys(request, ['idempotencyKey']);
  if (!request.idempotencyKey.trim()) {
    throw new Error('Invalid task request: idempotencyKey is required');
  }
}

function assertListInput(input: TaskListParams): void {
  assertExactKeys(input, [
    'pageNum',
    'pageSize',
    'taskType',
    'status',
    'keyword',
  ]);
  if (!Number.isSafeInteger(input.pageNum) || input.pageNum < 1) {
    throw new Error('Invalid task request: pageNum must be a positive integer');
  }
  if (
    !Number.isSafeInteger(input.pageSize) ||
    input.pageSize < 1 ||
    input.pageSize > 100
  ) {
    throw new Error('Invalid task request: pageSize must be between 1 and 100');
  }
  if (input.taskType && !TASK_TYPE_PATTERN.test(input.taskType)) {
    throw new Error('Invalid task request: taskType is invalid');
  }
  if (input.status && !taskStatuses.includes(input.status)) {
    throw new Error('Invalid task request: status is invalid');
  }
  if (input.keyword !== undefined && typeof input.keyword !== 'string') {
    throw new Error('Invalid task request: keyword must be a string');
  }
}

function taskPath(taskId?: string): string {
  return taskId
    ? `/api/tasks/${encodeURIComponent(taskId)}`
    : '/api/tasks';
}

export function createTasksApi(adapter: RuoYiAdapter): TasksApi {
  return {
    async list(input, signal) {
      assertListInput(input);
      const query = new URLSearchParams({
        pageNum: String(input.pageNum),
        pageSize: String(input.pageSize),
      });
      if (input.taskType) query.set('taskType', input.taskType);
      if (input.status) query.set('status', input.status);
      if (input.keyword) query.set('keyword', input.keyword);
      return parseTaskPageWire(
        await adapter.request<unknown>(`${taskPath()}?${query.toString()}`, {
          method: 'GET',
          signal,
        }),
      );
    },
    async get(taskId, signal) {
      assertTaskId(taskId);
      return parseTaskDetailWire(
        await adapter.request<unknown>(taskPath(taskId), {
          method: 'GET',
          signal,
        }),
      );
    },
    async cancel(taskId, request) {
      assertTaskId(taskId);
      assertActionRequest(request);
      return parseTaskDetailWire(
        await adapter.request<unknown>(`${taskPath(taskId)}/cancellations`, {
          data: request,
          method: 'POST',
        }),
      );
    },
    async retry(taskId, request) {
      assertTaskId(taskId);
      assertActionRequest(request);
      return parseTaskDetailWire(
        await adapter.request<unknown>(`${taskPath(taskId)}/retry`, {
          data: request,
          method: 'POST',
        }),
      );
    },
  };
}

let runtimeTasksApi: TasksApi | undefined;

function getRuntimeTasksApi(): TasksApi {
  runtimeTasksApi ??= createTasksApi(getRuntimeRuoYiAdapter());
  return runtimeTasksApi;
}

export const tasksApi: TasksApi = {
  cancel: (taskId, request) => getRuntimeTasksApi().cancel(taskId, request),
  get: (taskId, signal) => getRuntimeTasksApi().get(taskId, signal),
  list: (input, signal) => getRuntimeTasksApi().list(input, signal),
  retry: (taskId, request) => getRuntimeTasksApi().retry(taskId, request),
};
