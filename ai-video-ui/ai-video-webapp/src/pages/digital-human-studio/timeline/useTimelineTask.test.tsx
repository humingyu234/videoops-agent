import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/services/ai-video/core/errors';
import type { TasksApi } from '@/services/ai-video/tasks/api';
import type { TaskDetail } from '@/services/ai-video/tasks/types';
import { useTimelineTask } from './useTimelineTask';

const queuedTask: TaskDetail = {
  taskId: '90071992547409937' as TaskDetail['taskId'],
  taskType: 'timeline_render',
  resourceType: 'creation_project',
  resourceId: '90071992547409931' as TaskDetail['resourceId'],
  status: 'queued',
  stage: 'queued',
  progress: 0,
  canCancel: true,
  canRetry: false,
  createdAt: '2026-08-08T08:31:00+08:00',
  kind: 'render',
};

const completedTask: TaskDetail = {
  ...queuedTask,
  status: 'success',
  stage: 'completed',
  progress: 100,
  canCancel: false,
  resultAssetId: '90071992547410003' as TaskDetail['resourceId'],
};

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe('useTimelineTask', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('polls a visible current task every two seconds and clears work on unmount', async () => {
    vi.useFakeTimers();
    const get = vi.fn().mockResolvedValue(queuedTask);
    const api = { get } as Pick<TasksApi, 'get'>;
    const { result, unmount } = renderHook(
      () =>
        useTimelineTask({
          api,
          taskId: queuedTask.taskId,
          userId: 'user-1',
          workspaceId: 'workspace-1',
        }),
      { wrapper },
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current.task).toEqual(queuedTask);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2_000);
    });
    expect(get).toHaveBeenCalledTimes(2);

    unmount();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('performs exactly one uncached final detail read after a terminal observation', async () => {
    const get = vi.fn().mockResolvedValue(completedTask);
    const api = { get } as Pick<TasksApi, 'get'>;
    const { result } = renderHook(
      () =>
        useTimelineTask({
          api,
          taskId: completedTask.taskId,
          userId: 'user-1',
          workspaceId: 'workspace-1',
        }),
      { wrapper },
    );

    await waitFor(() => {
      expect(result.current.finalization).toMatchObject({
        kind: 'confirmed',
        task: completedTask,
      });
    });
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('stops a failed refetch before stale queued data can schedule another poll', async () => {
    const accessError = new ApiError({
      code: 403,
      msg: 'forbidden',
      status: 403,
    });
    const get = vi
      .fn()
      .mockResolvedValueOnce(queuedTask)
      .mockRejectedValueOnce(accessError);
    const api = { get } as Pick<TasksApi, 'get'>;
    const { result, unmount } = renderHook(
      () =>
        useTimelineTask({
          api,
          taskId: queuedTask.taskId,
          userId: 'user-1',
          workspaceId: 'workspace-1',
        }),
      { wrapper },
    );

    await waitFor(() => {
      expect(result.current.task).toEqual(queuedTask);
    });
    act(() => {
      result.current.retry();
    });

    await waitFor(() => {
      expect(result.current.error).toBe(accessError);
    });
    expect(result.current.task).toEqual(queuedTask);
    expect(result.current.pollingStatus).toBe('stopped');
    expect(get).toHaveBeenCalledTimes(2);
    unmount();
  });
});
