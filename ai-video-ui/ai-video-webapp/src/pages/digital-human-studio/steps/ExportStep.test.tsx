import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import type { ComponentProps, ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/services/ai-video/core/errors';
import ExportStep from './ExportStep';

vi.mock('../components/StepFooter', () => ({
  default: ({ children }: { children?: ReactNode }) => (
    <footer>{children}</footer>
  ),
}));

const output = {
  projectId: '90071992547409931',
  outputAssetId: '90071992547410003',
  taskId: '90071992547409937',
  createdAt: '2026-08-08T08:32:00+08:00',
};

function renderStep(props: Partial<ComponentProps<typeof ExportStep>>) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ExportStep
        onBackToTimeline={vi.fn()}
        onFinish={vi.fn()}
        onNext={vi.fn()}
        onPrevious={vi.fn()}
        onToast={vi.fn()}
        {...props}
      />
    </QueryClientProvider>,
  );
}

describe('ExportStep', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/studio');
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:timeline-output'),
      revokeObjectURL: vi.fn(),
    });
  });

  afterEach(() => {
    window.history.replaceState({}, '', '/studio');
  });

  it('loads preview media only through the current project outputs/latest result', async () => {
    const getLatestOutput = vi.fn().mockResolvedValue(output);
    const content = vi
      .fn()
      .mockResolvedValue(new Blob(['video'], { type: 'video/mp4' }));
    renderStep({
      assetsApi: { content },
      projectId: output.projectId,
      timelineApi: { getLatestOutput },
    });

    const video = await screen.findByLabelText('预览最终成品');
    expect(video).toHaveAttribute('src', 'blob:timeline-output');
    expect(getLatestOutput).toHaveBeenCalledWith(output.projectId);
    expect(content).toHaveBeenCalledWith(output.outputAssetId);
  });

  it('shows a permission state instead of a stale or mocked output', async () => {
    renderStep({
      assetsApi: { content: vi.fn() },
      projectId: output.projectId,
      timelineApi: {
        getLatestOutput: vi
          .fn()
          .mockRejectedValue(
            new ApiError({ code: 403, msg: 'forbidden', status: 403 }),
          ),
      },
    });

    await waitFor(() => {
      expect(screen.getByText('无权访问该创作项目')).toBeInTheDocument();
    });
    expect(screen.queryByLabelText('预览最终成品')).not.toBeInTheDocument();
  });

  it('does not keep a preview when the controlled output asset is unavailable', async () => {
    renderStep({
      assetsApi: {
        content: vi
          .fn()
          .mockRejectedValue(
            new ApiError({ code: 404, msg: 'missing', status: 404 }),
          ),
      },
      projectId: output.projectId,
      timelineApi: { getLatestOutput: vi.fn().mockResolvedValue(output) },
    });

    await waitFor(() => {
      expect(screen.getByText('成品素材不可用')).toBeInTheDocument();
    });
    expect(screen.queryByLabelText('预览最终成品')).not.toBeInTheDocument();
  });

  it('waits for the current render task before reading outputs/latest', async () => {
    const getLatestOutput = vi.fn();
    renderStep({
      assetsApi: { content: vi.fn() },
      projectId: output.projectId,
      renderTask: {
        canCancel: true,
        canRetry: false,
        createdAt: '2026-08-08T08:31:00+08:00',
        kind: 'render',
        progress: 50,
        resourceId: output.projectId,
        resourceType: 'creation_project',
        stage: 'rendering',
        status: 'running',
        taskId: output.taskId,
        taskType: 'timeline_render',
      } as unknown as NonNullable<
        ComponentProps<typeof ExportStep>['renderTask']
      >,
      timelineApi: { getLatestOutput },
    });

    expect(await screen.findByText('合成处理中')).toBeInTheDocument();
    expect(getLatestOutput).not.toHaveBeenCalled();
  });

  it('recovers a refreshed rendering project before reading outputs/latest', async () => {
    window.history.replaceState(
      {},
      '',
      `/studio?view=create&step=6&projectId=${output.projectId}`,
    );
    const getLatestOutput = vi.fn();
    const getProject = vi.fn().mockResolvedValue({ status: 'rendering' });

    renderStep({
      assetsApi: { content: vi.fn() },
      timelineApi: { getLatestOutput, getProject } as never,
    });

    expect(await screen.findByText('合成处理中')).toBeInTheDocument();
    expect(getProject).toHaveBeenCalledWith(output.projectId);
    expect(getLatestOutput).not.toHaveBeenCalled();
  });
});
