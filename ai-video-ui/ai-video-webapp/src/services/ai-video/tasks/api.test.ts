import { describe, expect, it, vi } from 'vitest';
import { createTasksApi } from './api';

const queuedTask = {
  taskId: '90071992547409937',
  taskType: 'timeline_render',
  resourceType: 'creation_project',
  resourceId: '90071992547409931',
  status: 'queued',
  stage: 'queued',
  progress: 0,
  canCancel: true,
  canRetry: false,
  createdAt: '2026-08-08T08:31:00+08:00',
};

describe('tasks api', () => {
  it('uses only the frozen task list query and no workspace identity fields', async () => {
    const request = vi.fn().mockResolvedValue({ total: 1, rows: [queuedTask] });
    const api = createTasksApi({ request });

    await api.list({
      pageNum: 2,
      pageSize: 20,
      taskType: 'timeline_render',
      status: 'queued',
      keyword: 'summer',
    });

    expect(request).toHaveBeenCalledWith(
      '/api/tasks?pageNum=2&pageSize=20&taskType=timeline_render&status=queued&keyword=summer',
      { method: 'GET', signal: undefined },
    );
  });

  it('reads task details and sends exactly one idempotency key for cancellation and retry', async () => {
    const request = vi.fn().mockResolvedValue(queuedTask);
    const api = createTasksApi({ request });

    await api.get('90071992547409937');
    await api.cancel('90071992547409937', { idempotencyKey: 'cancel-1' });
    await api.retry('90071992547409937', { idempotencyKey: 'retry-1' });

    expect(request.mock.calls).toEqual([
      ['/api/tasks/90071992547409937', { method: 'GET', signal: undefined }],
      [
        '/api/tasks/90071992547409937/cancellations',
        { data: { idempotencyKey: 'cancel-1' }, method: 'POST' },
      ],
      [
        '/api/tasks/90071992547409937/retry',
        { data: { idempotencyKey: 'retry-1' }, method: 'POST' },
      ],
    ]);
  });

  it('fails closed before sending extra action fields', async () => {
    const request = vi.fn();
    const api = createTasksApi({ request });

    await expect(
      api.cancel('90071992547409937', {
        idempotencyKey: 'cancel-1',
        workspaceId: 'must-not-send',
      } as never),
    ).rejects.toThrow('contains an unknown field');
    expect(request).not.toHaveBeenCalled();
  });
});
