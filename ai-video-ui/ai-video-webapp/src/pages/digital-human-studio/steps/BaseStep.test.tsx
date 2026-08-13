import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/services/ai-video/core/errors';
import type { DigitalHumanJob } from '@/services/ai-video/digitalHuman/types';
import { initialStudioState, type StudioState } from '../model';
import BaseStep from './BaseStep';

const { apiMock, createIdempotencyKeyMock } = vi.hoisted(() => ({
  apiMock: {
    confirmVoiceJob: vi.fn(),
    createVoiceJob: vi.fn(),
    createVideoJob: vi.fn(),
    getJob: vi.fn(),
    getJobMedia: vi.fn(),
  },
  createIdempotencyKeyMock: vi.fn(),
}));

vi.mock('@/services/ai-video/digitalHuman/api', () => ({
  createIdempotencyKey: createIdempotencyKeyMock,
  digitalHumanApi: apiMock,
}));

function deferred<Value>() {
  let resolve!: (value: Value) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<Value>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

const confirmedVoiceJob: DigitalHumanJob = {
  errorMessage: null,
  jobId: 'voice-job-ui',
  jobType: 'voice_generate',
  outputAvailable: true,
  parentJobId: null,
  progress: 100,
  stage: 'awaiting_voice_confirmation',
  status: 'succeeded',
  voiceConfirmed: true,
};

const queuedVideoJob: DigitalHumanJob = {
  ...confirmedVoiceJob,
  jobId: 'video-job-ui',
  jobType: 'video_generate',
  outputAvailable: false,
  parentJobId: confirmedVoiceJob.jobId,
  progress: 10,
  stage: 'video_submitted',
  status: 'queued',
  voiceConfirmed: false,
};

const succeededVideoJob: DigitalHumanJob = {
  ...queuedVideoJob,
  outputAvailable: true,
  progress: 100,
  stage: 'completed',
  status: 'succeeded',
};

describe('BaseStep', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    createIdempotencyKeyMock.mockReset();
    createIdempotencyKeyMock.mockReturnValue('video-ui-key');
    vi.mocked(URL.createObjectURL).mockReturnValue('blob:video-preview');
    apiMock.createVideoJob.mockResolvedValue(queuedVideoJob);
    apiMock.getJob.mockResolvedValue(succeededVideoJob);
    apiMock.getJobMedia.mockResolvedValue(
      new Blob(['video'], { type: 'video/mp4' }),
    );
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('does not substitute a static script when no generated script exists', () => {
    const { container } = render(
      <BaseStep
        state={initialStudioState}
        update={vi.fn()}
        onFinish={vi.fn()}
        onNext={vi.fn()}
        onPrevious={vi.fn()}
        onToast={vi.fn()}
      />,
    );

    expect(container.querySelector('.base-script')).toBeEmptyDOMElement();
  });

  it('creates and polls the video job with confirmed voice and selected portrait', async () => {
    const selectedAvatar = 'avatar-source-ui';

    function Harness() {
      const [state, setState] = useState<StudioState>({
        ...initialStudioState,
        selectedAvatar,
        videoJob: null,
        voiceJob: confirmedVoiceJob,
      });
      return (
        <BaseStep
          state={state}
          update={(patch) => setState((current) => ({ ...current, ...patch }))}
          onFinish={vi.fn()}
          onNext={vi.fn()}
          onPrevious={vi.fn()}
          onToast={vi.fn()}
        />
      );
    }

    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: '生成数字人底片' }));
    expect(apiMock.createVideoJob).toHaveBeenCalledWith(
      {
        idempotencyKey: 'video-ui-key',
        portraitId: selectedAvatar,
        voiceJobId: confirmedVoiceJob.jobId,
      },
      expect.any(AbortSignal),
    );
    expect(await screen.findByLabelText('数字人底片预览')).toHaveAttribute(
      'src',
      'blob:video-preview',
    );
  });

  it('renders terminal video failure without inventing local progress', () => {
    render(
      <BaseStep
        state={{
          ...initialStudioState,
          selectedAvatar: 'avatar-source-failure',
          videoJob: {
            ...queuedVideoJob,
            errorMessage: '视频生成失败，请稍后重试。',
            progress: 31,
            stage: 'failed',
            status: 'failed',
          },
          voiceJob: confirmedVoiceJob,
        }}
        update={vi.fn()}
        onFinish={vi.fn()}
        onNext={vi.fn()}
        onPrevious={vi.fn()}
        onToast={vi.fn()}
      />,
    );

    expect(screen.getByText('视频生成失败，请稍后重试。')).toBeVisible();
    expect(screen.queryByText('32%')).not.toBeInTheDocument();
  });

  it('retries a failed video job with a fresh idempotency key', async () => {
    const selectedAvatar = 'avatar-source-retry';
    createIdempotencyKeyMock.mockReturnValueOnce('video-retry-key');

    function Harness() {
      const [state, setState] = useState<StudioState>({
        ...initialStudioState,
        selectedAvatar,
        videoGenerationIntent: {
          idempotencyKey: 'failed-video-key',
          portraitId: selectedAvatar,
          voiceJobId: confirmedVoiceJob.jobId,
        },
        videoJob: {
          ...queuedVideoJob,
          errorMessage: '视频任务提交失败，请重试',
          stage: 'failed',
          status: 'failed',
        },
        voiceJob: confirmedVoiceJob,
      });
      return (
        <BaseStep
          state={state}
          update={(patch) => setState((current) => ({ ...current, ...patch }))}
          onFinish={vi.fn()}
          onNext={vi.fn()}
          onPrevious={vi.fn()}
          onToast={vi.fn()}
        />
      );
    }

    render(<Harness />);
    fireEvent.click(
      screen.getByRole('button', { name: '重新生成数字人底片' }),
    );

    await waitFor(() =>
      expect(apiMock.createVideoJob).toHaveBeenCalledWith(
        {
          idempotencyKey: 'video-retry-key',
          portraitId: selectedAvatar,
          voiceJobId: confirmedVoiceJob.jobId,
        },
        expect.any(AbortSignal),
      ),
    );
  });

  it('retries a transient polling failure and resumes from the platform state', async () => {
    vi.useFakeTimers();
    apiMock.getJob
      .mockRejectedValueOnce(new Error('临时网络错误'))
      .mockResolvedValueOnce(succeededVideoJob);

    function Harness() {
      const [state, setState] = useState<StudioState>({
        ...initialStudioState,
        selectedAvatar: 'avatar-source-poll',
        videoJob: queuedVideoJob,
        voiceJob: confirmedVoiceJob,
      });
      return (
        <BaseStep
          state={state}
          update={(patch) => setState((current) => ({ ...current, ...patch }))}
          onFinish={vi.fn()}
          onNext={vi.fn()}
          onPrevious={vi.fn()}
          onToast={vi.fn()}
        />
      );
    }

    render(<Harness />);
    await act(async () => {
      await Promise.resolve();
    });
    expect(apiMock.getJob).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1500);
      await Promise.resolve();
    });

    expect(apiMock.getJob).toHaveBeenCalledTimes(2);
    expect(screen.getByLabelText('数字人底片预览')).toHaveAttribute(
      'src',
      'blob:video-preview',
    );
    expect(screen.queryByText('临时网络错误')).not.toBeInTheDocument();
  });

  it('pauses with a manual retry action after exhausting the polling retry budget', async () => {
    vi.useFakeTimers();
    apiMock.getJob.mockRejectedValue(new Error('视频任务网络持续异常'));

    render(
      <BaseStep
        state={{
          ...initialStudioState,
          videoJob: queuedVideoJob,
          voiceJob: confirmedVoiceJob,
        }}
        update={vi.fn()}
        onFinish={vi.fn()}
        onNext={vi.fn()}
        onPrevious={vi.fn()}
        onToast={vi.fn()}
      />,
    );
    await act(async () => {
      await Promise.resolve();
      await vi.advanceTimersByTimeAsync(10_500);
    });

    expect(apiMock.getJob).toHaveBeenCalledTimes(4);
    expect(screen.getByText('视频任务状态查询已暂停')).toBeVisible();
    expect(
      screen.getByRole('button', { name: '重新查询视频任务' }),
    ).toBeVisible();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(apiMock.getJob).toHaveBeenCalledTimes(4);
  });

  it('pauses after session invalidation and manually resumes the same video job', async () => {
    vi.useFakeTimers();
    apiMock.getJob
      .mockRejectedValueOnce(
        new ApiError({ code: 401, msg: '登录已失效', status: 401 }),
      )
      .mockResolvedValueOnce(succeededVideoJob);

    function Harness() {
      const [state, setState] = useState<StudioState>({
        ...initialStudioState,
        videoJob: queuedVideoJob,
        voiceJob: confirmedVoiceJob,
      });
      return (
        <BaseStep
          state={state}
          update={(patch) => setState((current) => ({ ...current, ...patch }))}
          onFinish={vi.fn()}
          onNext={vi.fn()}
          onPrevious={vi.fn()}
          onToast={vi.fn()}
        />
      );
    }

    render(<Harness />);
    await act(async () => {
      await Promise.resolve();
    });

    expect(apiMock.getJob).toHaveBeenCalledTimes(1);
    expect(screen.getByText('登录已失效')).toBeVisible();
    expect(screen.getByText('视频任务状态查询已暂停')).toBeVisible();
    const retryButton = screen.getByRole('button', {
      name: '重新查询视频任务',
    });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(apiMock.getJob).toHaveBeenCalledTimes(1);

    vi.useRealTimers();
    fireEvent.click(retryButton);

    expect(await screen.findByLabelText('数字人底片预览')).toHaveAttribute(
      'src',
      'blob:video-preview',
    );
    expect(apiMock.getJob).toHaveBeenCalledTimes(2);
    expect(apiMock.getJob).toHaveBeenNthCalledWith(
      1,
      queuedVideoJob.jobId,
      expect.any(AbortSignal),
    );
    expect(apiMock.getJob).toHaveBeenNthCalledWith(
      2,
      queuedVideoJob.jobId,
      expect.any(AbortSignal),
    );
    expect(apiMock.createVideoJob).not.toHaveBeenCalled();
  });

  it('manually reloads failed video media without creating another job', async () => {
    apiMock.getJobMedia
      .mockRejectedValueOnce(new Error('视频媒体临时不可用'))
      .mockResolvedValueOnce(new Blob(['video'], { type: 'video/mp4' }));

    render(
      <BaseStep
        state={{
          ...initialStudioState,
          videoJob: succeededVideoJob,
          voiceJob: confirmedVoiceJob,
        }}
        update={vi.fn()}
        onFinish={vi.fn()}
        onNext={vi.fn()}
        onPrevious={vi.fn()}
        onToast={vi.fn()}
      />,
    );

    expect(await screen.findByText('视频媒体临时不可用')).toBeVisible();
    expect(screen.queryByText('正在读取数字人视频…')).not.toBeInTheDocument();
    const nextButton = screen.getByRole('button', { name: /进入时间轴编辑/ });
    expect(nextButton).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '重新读取数字人视频' }));

    expect(await screen.findByLabelText('数字人底片预览')).toHaveAttribute(
      'src',
      'blob:video-preview',
    );
    expect(apiMock.getJobMedia).toHaveBeenCalledTimes(2);
    expect(apiMock.getJobMedia).toHaveBeenNthCalledWith(
      1,
      succeededVideoJob.jobId,
      expect.any(AbortSignal),
    );
    expect(apiMock.getJobMedia).toHaveBeenNthCalledWith(
      2,
      succeededVideoJob.jobId,
      expect.any(AbortSignal),
    );
    expect(apiMock.createVideoJob).not.toHaveBeenCalled();
  });

  it('persists an idempotency key across remounts and rotates it after the voice job changes', async () => {
    createIdempotencyKeyMock
      .mockReturnValueOnce('video-intent-1')
      .mockReturnValueOnce('video-intent-2')
      .mockReturnValueOnce('video-intent-3');
    apiMock.createVideoJob.mockRejectedValue(new Error('响应丢失'));
    const selectedAvatar = 'avatar-source-intent';
    const secondVoiceJob = {
      ...confirmedVoiceJob,
      jobId: 'voice-job-ui-2',
    };

    function Harness() {
      const [visible, setVisible] = useState(true);
      const [state, setState] = useState<StudioState>({
        ...initialStudioState,
        selectedAvatar,
        voiceJob: confirmedVoiceJob,
      });
      return (
        <>
          <button type="button" onClick={() => setVisible((value) => !value)}>
            切换底片步骤
          </button>
          <button
            type="button"
            onClick={() =>
              setState((current) => ({
                ...current,
                voiceJob: secondVoiceJob,
              }))
            }
          >
            选择新声音
          </button>
          {visible && (
            <BaseStep
              state={state}
              update={(patch) =>
                setState((current) => ({ ...current, ...patch }))
              }
              onFinish={vi.fn()}
              onNext={vi.fn()}
              onPrevious={vi.fn()}
              onToast={vi.fn()}
            />
          )}
        </>
      );
    }

    render(<Harness />);
    const createButton = screen.getByRole('button', { name: '生成数字人底片' });
    fireEvent.click(createButton);
    await waitFor(() =>
      expect(apiMock.createVideoJob).toHaveBeenCalledTimes(1),
    );
    await screen.findByText('响应丢失');

    fireEvent.click(screen.getByRole('button', { name: '切换底片步骤' }));
    fireEvent.click(screen.getByRole('button', { name: '切换底片步骤' }));
    fireEvent.click(screen.getByRole('button', { name: '生成数字人底片' }));
    await waitFor(() =>
      expect(apiMock.createVideoJob).toHaveBeenCalledTimes(2),
    );

    fireEvent.click(screen.getByRole('button', { name: '选择新声音' }));
    fireEvent.click(screen.getByRole('button', { name: '生成数字人底片' }));
    await waitFor(() =>
      expect(apiMock.createVideoJob).toHaveBeenCalledTimes(3),
    );

    expect(apiMock.createVideoJob.mock.calls.map((call) => call[0])).toEqual([
      expect.objectContaining({
        idempotencyKey: 'video-intent-1',
        portraitId: selectedAvatar,
      }),
      expect.objectContaining({
        idempotencyKey: 'video-intent-1',
        portraitId: selectedAvatar,
      }),
      expect.objectContaining({
        idempotencyKey: 'video-intent-2',
        portraitId: selectedAvatar,
        voiceJobId: secondVoiceJob.jobId,
      }),
    ]);
    expect(createIdempotencyKeyMock).toHaveBeenCalledTimes(2);
  });

  it('aborts create and drops its result after unmount', async () => {
    const pendingCreate = deferred<DigitalHumanJob>();
    apiMock.createVideoJob.mockReturnValueOnce(pendingCreate.promise);
    const update = vi.fn();
    const onToast = vi.fn();
    const { unmount } = render(
      <BaseStep
        state={{
          ...initialStudioState,
          selectedAvatar: 'avatar-source-unmount',
          voiceJob: confirmedVoiceJob,
        }}
        update={update}
        onFinish={vi.fn()}
        onNext={vi.fn()}
        onPrevious={vi.fn()}
        onToast={onToast}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: '生成数字人底片' }));
    await waitFor(() =>
      expect(apiMock.createVideoJob).toHaveBeenCalledTimes(1),
    );

    unmount();
    pendingCreate.resolve(queuedVideoJob);
    await act(async () => {
      await pendingCreate.promise;
    });

    const signal = apiMock.createVideoJob.mock.calls[0]?.[1] as
      | AbortSignal
      | undefined;
    expect(signal?.aborted).toBe(true);
    expect(update).toHaveBeenCalledWith({
      videoGenerationIntent: expect.objectContaining({
        idempotencyKey: 'video-ui-key',
        portraitId: 'avatar-source-unmount',
      }),
    });
    expect(update).not.toHaveBeenCalledWith(
      expect.objectContaining({ videoJob: expect.anything() }),
    );
    expect(onToast).not.toHaveBeenCalled();
  });
});
