import { ApiError } from '@/services/ai-video/core/errors';
import { describe, expect, it, vi } from 'vitest';
import type { TaskDetail } from './types';
import {
  createTaskFinalizer,
  getTaskPollingDelay,
  isTaskTerminal,
  shouldStopTaskPolling,
} from './polling';

const terminalTask: TaskDetail = {
  taskId: '90071992547409937' as TaskDetail['taskId'],
  taskType: 'timeline_render',
  resourceType: 'creation_project',
  resourceId: '90071992547409931' as TaskDetail['resourceId'],
  status: 'success' as const,
  stage: 'completed',
  progress: 100,
  canCancel: false,
  canRetry: false,
  resultAssetId: '90071992547410003' as TaskDetail['taskId'],
  createdAt: '2026-08-08T08:31:00+08:00',
  finishedAt: '2026-08-08T08:32:00+08:00',
  kind: 'render' as const,
};

describe('task polling policy', () => {
  it('uses focused detail/list intervals, hidden intervals, bounded backoff, and an offline stop', () => {
    expect(
      getTaskPollingDelay({
        failureCount: 0,
        hiddenForMs: 0,
        online: true,
        scope: 'detail',
        visible: true,
      }),
    ).toBe(2_000);
    expect(
      getTaskPollingDelay({
        failureCount: 0,
        hiddenForMs: 0,
        online: true,
        scope: 'list',
        visible: true,
      }),
    ).toBe(5_000);
    expect(
      getTaskPollingDelay({
        failureCount: 0,
        hiddenForMs: 1_000,
        online: true,
        scope: 'detail',
        visible: false,
      }),
    ).toBe(15_000);
    expect(
      getTaskPollingDelay({
        failureCount: 5,
        hiddenForMs: 0,
        online: true,
        random: () => 0.5,
        scope: 'detail',
        visible: true,
      }),
    ).toBe(30_000);
    expect(
      getTaskPollingDelay({
        failureCount: 0,
        hiddenForMs: 300_000,
        online: true,
        scope: 'detail',
        visible: false,
      }),
    ).toBe(false);
    expect(
      getTaskPollingDelay({
        failureCount: 0,
        hiddenForMs: 0,
        online: false,
        scope: 'detail',
        visible: true,
      }),
    ).toBe(false);
  });

  it('does one fresh final read after a terminal observation and never repeats it', async () => {
    const fetchFinal = vi.fn().mockResolvedValue(terminalTask);
    const stopPeriodic = vi.fn();
    const finalizer = createTaskFinalizer(fetchFinal);

    const first = await finalizer.finalize(terminalTask, stopPeriodic);
    const second = await finalizer.finalize(terminalTask, stopPeriodic);

    expect(isTaskTerminal(terminalTask.status)).toBe(true);
    expect(first).toMatchObject({ kind: 'confirmed', task: terminalTask });
    expect(second).toEqual({ kind: 'already-finalized' });
    expect(stopPeriodic).toHaveBeenCalledTimes(1);
    expect(fetchFinal).toHaveBeenCalledTimes(1);
    expect(fetchFinal).toHaveBeenCalledWith(terminalTask.taskId);
  });

  it('stops only the affected polling resource for access and missing-resource failures', () => {
    expect(
      shouldStopTaskPolling(new ApiError({ code: 403, msg: 'forbidden' })),
    ).toBe(true);
    expect(
      shouldStopTaskPolling(new ApiError({ code: 403, msg: 'forbidden', status: 403 })),
    ).toBe(true);
    expect(
      shouldStopTaskPolling(new ApiError({ code: 46601, msg: 'missing' })),
    ).toBe(true);
    expect(
      shouldStopTaskPolling(new ApiError({ code: 46129, msg: 'session' })),
    ).toBe(true);
    expect(
      shouldStopTaskPolling(new ApiError({ code: 500, msg: 'transient' })),
    ).toBe(false);
  });
});
