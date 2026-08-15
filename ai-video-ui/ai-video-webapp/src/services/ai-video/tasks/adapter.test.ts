import { describe, expect, it } from 'vitest';
import {
  parseTaskDetailWire,
  parseTaskPageWire,
} from './adapter';

const queuedTask = {
  taskId: '90071992547409937',
  taskType: 'timeline_render',
  resourceType: 'creation_project',
  resourceId: '90071992547409931',
  projectId: '90071992547409931',
  draftRevision: '1',
  inputVersionId: '90071992547409936',
  status: 'queued',
  stage: 'queued',
  progress: 0,
  canCancel: true,
  canRetry: false,
  createdAt: '2026-08-08T08:31:00+08:00',
  updatedAt: '2026-08-08T08:31:01+08:00',
};

describe('unified task wire adapter', () => {
  it('accepts lawful unknown task and resource types without exposing raw results', () => {
    const task = parseTaskDetailWire({
      ...queuedTask,
      resourceType: 'workflow_order',
      taskType: 'workflow_future_task',
    });

    expect(task).toMatchObject({
      taskId: queuedTask.taskId,
      kind: 'unknown',
      canCancel: true,
      canRetry: false,
      resourceType: 'workflow_order',
    });
    expect('cancellable' in task).toBe(false);
    expect('retryable' in task).toBe(false);
    expect('safeMessage' in task).toBe(false);
    expect('resultPayload' in task).toBe(false);
  });

  it('accepts the current AiTaskVo project context fields', () => {
    expect(parseTaskDetailWire({
      ...queuedTask,
      result: null,
      resultAssetId: null,
      errorCode: null,
      errorSummary: null,
    })).toMatchObject({
      taskId: queuedTask.taskId,
      resourceId: queuedTask.resourceId,
      status: 'queued',
    });
  });

  it('rejects backend-internal task fields instead of accepting a second wire shape', () => {
    expect(() =>
      parseTaskDetailWire({ ...queuedTask, cancellable: true }),
    ).toThrow('contains an unknown field');
    expect(() =>
      parseTaskDetailWire({ ...queuedTask, retryable: true }),
    ).toThrow('contains an unknown field');
    expect(() =>
      parseTaskDetailWire({ ...queuedTask, safeMessage: 'internal' }),
    ).toThrow('contains an unknown field');
    expect(() =>
      parseTaskDetailWire({ ...queuedTask, resultPayload: {} }),
    ).toThrow('contains an unknown field');
  });

  it('retains a detail result for a specialized task parser while list items omit it', () => {
    const result = { taskId: queuedTask.taskId, providerInternals: 'validated downstream' };
    const detail = parseTaskDetailWire({ ...queuedTask, result });

    expect(detail.result).toEqual(result);
  });

  it('parses pages without carrying result payloads from a list response', () => {
    const page = parseTaskPageWire({
      total: 1,
      rows: [{ ...queuedTask, result: { mustNotBeUsed: true } }],
    });

    expect(page).toMatchObject({ total: 1 });
    expect(page.rows[0]).toMatchObject({ taskId: queuedTask.taskId });
    expect('result' in page.rows[0]).toBe(false);
  });
});
