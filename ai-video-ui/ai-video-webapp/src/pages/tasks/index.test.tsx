import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { TaskListItem } from '@/services/ai-video/tasks/types';

const { cancel, list, retry } = vi.hoisted(() => ({
  cancel: vi.fn(),
  list: vi.fn(),
  retry: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  useModel: () => ({
    initialState: {
      currentUser: { id: 'user-1', workspace: { id: 'workspace-1' } },
    },
  }),
}));

vi.mock('@/components/CreatorWorkspaceShell', () => ({
  default: ({ children }: { children: ReactNode }) => <main>{children}</main>,
}));

vi.mock('@/services/ai-video/tasks/api', () => ({
  tasksApi: { cancel, list, retry },
}));

import TasksPage from './index';

const task: TaskListItem = {
  taskId: '90071992547409937' as TaskListItem['taskId'],
  taskType: 'timeline_future_task',
  resourceType: 'creation_project',
  resourceId: '90071992547409931' as TaskListItem['resourceId'],
  status: 'failed',
  stage: 'provider_paused',
  progress: 42,
  canCancel: true,
  canRetry: true,
  errorSummary: '安全错误摘要',
  createdAt: '2026-08-08T08:31:00+08:00',
  kind: 'unknown',
};

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <TasksPage />
    </QueryClientProvider>,
  );
}

describe('TasksPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    list.mockResolvedValue({ rows: [task], total: 1 });
    cancel.mockResolvedValue({ ...task, canCancel: false });
    retry.mockResolvedValue({ ...task, taskId: '90071992547409938' });
    vi.stubGlobal('crypto', { randomUUID: () => 'request-key' });
  });

  it('shows unknown lawful task types and sends no workspace identity in the API request', async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('timeline_future_task')).toBeInTheDocument();
    });
    expect(screen.getByText(/provider_paused/)).toBeInTheDocument();
    expect(screen.getByText('安全错误摘要')).toBeInTheDocument();
    expect(list).toHaveBeenCalledWith(
      {
        pageNum: 1,
        pageSize: 10,
        status: undefined,
      },
      expect.any(AbortSignal),
    );
  });

  it('does not send a cancellation request when its confirmation is dismissed', async () => {
    renderPage();

    await screen.findByText('timeline_future_task');
    fireEvent.click(
      screen.getByRole('button', { name: `取消任务 ${task.taskId}` }),
    );
    expect(cancel).not.toHaveBeenCalled();
    const dialog = await screen.findByRole('dialog');
    expect(
      within(dialog).getByText(`确认取消任务 ${task.taskId}？`, {
        selector: '.ant-modal-title',
      }),
    ).toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole('button', { name: /返\s*回/ }));
    await waitFor(() => expect(cancel).not.toHaveBeenCalled());
  });

  it('generates an idempotency key and sends exactly one retry only after confirmation', async () => {
    let resolveRetry: ((value: TaskListItem) => void) | undefined;
    retry.mockImplementation(
      () => new Promise<TaskListItem>((resolve) => { resolveRetry = resolve; }),
    );
    renderPage();

    await screen.findByText('timeline_future_task');

    fireEvent.click(
      screen.getByRole('button', { name: `重试任务 ${task.taskId}` }),
    );
    expect(retry).not.toHaveBeenCalled();
    const dialog = await screen.findByRole('dialog');
    expect(
      within(dialog).getByText(`确认重试任务 ${task.taskId}？`, {
        selector: '.ant-modal-title',
      }),
    ).toBeInTheDocument();

    const confirm = within(dialog).getByRole('button', { name: '确认重试' });
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    await waitFor(() => {
      expect(retry).toHaveBeenCalledWith(task.taskId, {
        idempotencyKey: 'request-key',
      });
    });
    expect(retry).toHaveBeenCalledTimes(1);

    resolveRetry?.({ ...task, taskId: '90071992547409938' as TaskListItem['taskId'] });
  });
});
