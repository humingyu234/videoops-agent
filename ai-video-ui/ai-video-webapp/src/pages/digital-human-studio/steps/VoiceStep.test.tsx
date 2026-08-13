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
import VoiceStep from './VoiceStep';

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

const queuedVoiceJob: DigitalHumanJob = {
  errorMessage: null,
  jobId: 'voice-job-ui',
  jobType: 'voice_generate',
  outputAvailable: false,
  parentJobId: null,
  progress: 5,
  stage: 'queued',
  status: 'queued',
  voiceConfirmed: false,
};

const succeededVoiceJob: DigitalHumanJob = {
  ...queuedVoiceJob,
  outputAvailable: true,
  progress: 100,
  stage: 'awaiting_voice_confirmation',
  status: 'succeeded',
};

describe('VoiceStep', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    createIdempotencyKeyMock.mockReset();
    createIdempotencyKeyMock.mockReturnValue('voice-ui-key');
    vi.mocked(URL.createObjectURL).mockReturnValue('blob:voice-preview');
    apiMock.createVoiceJob.mockResolvedValue(queuedVoiceJob);
    apiMock.getJob.mockResolvedValue(succeededVoiceJob);
    apiMock.getJobMedia.mockResolvedValue(
      new Blob(['voice'], { type: 'audio/wav' }),
    );
    apiMock.confirmVoiceJob.mockResolvedValue({
      ...succeededVoiceJob,
      voiceConfirmed: true,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('does not substitute a static script when no generated script exists', () => {
    render(
      <VoiceStep
        state={initialStudioState}
        update={vi.fn()}
        onFinish={vi.fn()}
        onNext={vi.fn()}
        onPrevious={vi.fn()}
        onToast={vi.fn()}
      />,
    );

    expect(screen.getByLabelText('口播正文')).toHaveValue('');
  });

  it('creates, polls, previews, and confirms the real voice job before continuing', async () => {
    const onNext = vi.fn();
    const selectedVoice = 'voice-source-ui';

    function Harness() {
      const [state, setState] = useState<StudioState>({
        ...initialStudioState,
        selectedVoice,
        scriptBodies: ['这是已经确认的正文'],
        selectedScript: 0,
        voiceJob: null,
      });
      return (
        <VoiceStep
          state={state}
          update={(patch) => setState((current) => ({ ...current, ...patch }))}
          onFinish={vi.fn()}
          onNext={onNext}
          onPrevious={vi.fn()}
          onToast={vi.fn()}
        />
      );
    }

    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: '生成克隆声音' }));

    expect(apiMock.createVoiceJob).toHaveBeenCalledWith(
      {
        idempotencyKey: 'voice-ui-key',
        referenceVoiceId: selectedVoice,
        scriptText: '这是已经确认的正文',
      },
      expect.any(AbortSignal),
    );
    expect(await screen.findByLabelText('克隆声音试听')).toHaveAttribute(
      'src',
      'blob:voice-preview',
    );
    fireEvent.click(
      screen.getByRole('button', { name: /确认声音，去生成底片/ }),
    );
    await waitFor(() => {
      expect(apiMock.confirmVoiceJob).toHaveBeenCalledWith(
        'voice-job-ui',
        expect.any(AbortSignal),
      );
      expect(onNext).toHaveBeenCalledTimes(1);
    });
  });

  it('shows the provider-neutral terminal error returned by the platform job', async () => {
    apiMock.createVoiceJob.mockResolvedValue({
      ...queuedVoiceJob,
      errorMessage: '声音生成失败，请稍后重试。',
      stage: 'failed',
      status: 'failed',
    });

    function FailureHarness() {
      const [state, setState] = useState<StudioState>({
        ...initialStudioState,
        selectedVoice: 'voice-source-failure',
        scriptBodies: ['这是已经确认的正文'],
        selectedScript: 0,
        voiceJob: null,
      });
      return (
        <VoiceStep
          state={state}
          update={(patch) => setState((current) => ({ ...current, ...patch }))}
          onFinish={vi.fn()}
          onNext={vi.fn()}
          onPrevious={vi.fn()}
          onToast={vi.fn()}
        />
      );
    }

    render(<FailureHarness />);
    fireEvent.click(screen.getByRole('button', { name: '生成克隆声音' }));
    expect(await screen.findByText('声音生成失败，请稍后重试。')).toBeVisible();
  });

  it('retries a transient polling failure and resumes from the platform state', async () => {
    vi.useFakeTimers();
    apiMock.getJob
      .mockRejectedValueOnce(new Error('临时网络错误'))
      .mockResolvedValueOnce(succeededVoiceJob);

    function Harness() {
      const [state, setState] = useState<StudioState>({
        ...initialStudioState,
        selectedVoice: 'voice-source-poll',
        voiceJob: queuedVoiceJob,
      });
      return (
        <VoiceStep
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
    expect(screen.getByLabelText('克隆声音试听')).toHaveAttribute(
      'src',
      'blob:voice-preview',
    );
    expect(screen.queryByText('临时网络错误')).not.toBeInTheDocument();
  });

  it('pauses with a manual retry action after exhausting the polling retry budget', async () => {
    vi.useFakeTimers();
    apiMock.getJob.mockRejectedValue(new Error('声音任务网络持续异常'));

    render(
      <VoiceStep
        state={{ ...initialStudioState, voiceJob: queuedVoiceJob }}
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
    expect(screen.getByText('声音任务状态查询已暂停')).toBeVisible();
    expect(
      screen.getByRole('button', { name: '重新查询声音任务' }),
    ).toBeVisible();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(apiMock.getJob).toHaveBeenCalledTimes(4);
  });

  it('pauses after an authorization failure and manually resumes the same voice job', async () => {
    vi.useFakeTimers();
    apiMock.getJob
      .mockRejectedValueOnce(
        new ApiError({ code: 403, msg: '没有任务读取权限', status: 403 }),
      )
      .mockResolvedValueOnce(succeededVoiceJob);

    function Harness() {
      const [state, setState] = useState<StudioState>({
        ...initialStudioState,
        voiceJob: queuedVoiceJob,
      });
      return (
        <VoiceStep
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
    expect(screen.getByText('没有任务读取权限')).toBeVisible();
    expect(screen.getByText('声音任务状态查询已暂停')).toBeVisible();
    const retryButton = screen.getByRole('button', {
      name: '重新查询声音任务',
    });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(apiMock.getJob).toHaveBeenCalledTimes(1);

    vi.useRealTimers();
    fireEvent.click(retryButton);

    expect(await screen.findByLabelText('克隆声音试听')).toHaveAttribute(
      'src',
      'blob:voice-preview',
    );
    expect(apiMock.getJob).toHaveBeenCalledTimes(2);
    expect(apiMock.getJob).toHaveBeenNthCalledWith(
      1,
      queuedVoiceJob.jobId,
      expect.any(AbortSignal),
    );
    expect(apiMock.getJob).toHaveBeenNthCalledWith(
      2,
      queuedVoiceJob.jobId,
      expect.any(AbortSignal),
    );
    expect(apiMock.createVoiceJob).not.toHaveBeenCalled();
  });

  it('manually reloads failed voice media without creating another job', async () => {
    apiMock.getJobMedia
      .mockRejectedValueOnce(new Error('声音媒体临时不可用'))
      .mockResolvedValueOnce(new Blob(['voice'], { type: 'audio/wav' }));

    render(
      <VoiceStep
        state={{ ...initialStudioState, voiceJob: succeededVoiceJob }}
        update={vi.fn()}
        onFinish={vi.fn()}
        onNext={vi.fn()}
        onPrevious={vi.fn()}
        onToast={vi.fn()}
      />,
    );

    expect(await screen.findByText('声音媒体临时不可用')).toBeVisible();
    expect(screen.queryByText('正在读取声音文件…')).not.toBeInTheDocument();
    const confirmButton = screen.getByRole('button', {
      name: /确认声音，去生成底片/,
    });
    expect(confirmButton).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '重新读取声音文件' }));

    expect(await screen.findByLabelText('克隆声音试听')).toHaveAttribute(
      'src',
      'blob:voice-preview',
    );
    expect(apiMock.getJobMedia).toHaveBeenCalledTimes(2);
    expect(apiMock.getJobMedia).toHaveBeenNthCalledWith(
      1,
      succeededVoiceJob.jobId,
      expect.any(AbortSignal),
    );
    expect(apiMock.getJobMedia).toHaveBeenNthCalledWith(
      2,
      succeededVoiceJob.jobId,
      expect.any(AbortSignal),
    );
    expect(apiMock.createVoiceJob).not.toHaveBeenCalled();
    expect(apiMock.confirmVoiceJob).not.toHaveBeenCalled();
  });

  it('persists an idempotency key across remounts and rotates it after the script changes', async () => {
    createIdempotencyKeyMock
      .mockReturnValueOnce('voice-intent-1')
      .mockReturnValueOnce('voice-intent-2')
      .mockReturnValueOnce('voice-intent-3');
    apiMock.createVoiceJob.mockRejectedValue(new Error('响应丢失'));
    const selectedVoice = 'voice-source-intent';

    function Harness() {
      const [visible, setVisible] = useState(true);
      const [state, setState] = useState<StudioState>({
        ...initialStudioState,
        selectedVoice,
        scriptBodies: ['第一版正文'],
      });
      return (
        <>
          <button type="button" onClick={() => setVisible((value) => !value)}>
            切换声音步骤
          </button>
          {visible && (
            <VoiceStep
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

    const createButton = screen.getByRole('button', { name: '生成克隆声音' });
    fireEvent.click(createButton);
    await waitFor(() =>
      expect(apiMock.createVoiceJob).toHaveBeenCalledTimes(1),
    );
    await screen.findByText('响应丢失');

    fireEvent.click(screen.getByRole('button', { name: '切换声音步骤' }));
    fireEvent.click(screen.getByRole('button', { name: '切换声音步骤' }));
    fireEvent.click(screen.getByRole('button', { name: '生成克隆声音' }));
    await waitFor(() =>
      expect(apiMock.createVoiceJob).toHaveBeenCalledTimes(2),
    );

    fireEvent.change(screen.getByLabelText('口播正文'), {
      target: { value: '第二版正文' },
    });
    fireEvent.click(screen.getByRole('button', { name: '切换声音步骤' }));
    fireEvent.click(screen.getByRole('button', { name: '切换声音步骤' }));
    fireEvent.click(screen.getByRole('button', { name: '生成克隆声音' }));
    await waitFor(() =>
      expect(apiMock.createVoiceJob).toHaveBeenCalledTimes(3),
    );

    expect(apiMock.createVoiceJob.mock.calls.map((call) => call[0])).toEqual([
      expect.objectContaining({
        idempotencyKey: 'voice-intent-1',
        referenceVoiceId: selectedVoice,
      }),
      expect.objectContaining({
        idempotencyKey: 'voice-intent-1',
        referenceVoiceId: selectedVoice,
      }),
      expect.objectContaining({
        idempotencyKey: 'voice-intent-2',
        referenceVoiceId: selectedVoice,
        scriptText: '第二版正文',
      }),
    ]);
    expect(createIdempotencyKeyMock).toHaveBeenCalledTimes(2);
  });

  it('drops a pending create result after the selected input changes', async () => {
    const pendingCreate = deferred<DigitalHumanJob>();
    apiMock.createVoiceJob.mockReturnValueOnce(pendingCreate.promise);
    const update = vi.fn();
    const onToast = vi.fn();
    const firstVoiceId = 'voice-source-first';
    const secondVoiceId = 'voice-source-second';
    const props = {
      onFinish: vi.fn(),
      onNext: vi.fn(),
      onPrevious: vi.fn(),
      onToast,
      update,
    };

    const { rerender } = render(
      <VoiceStep
        {...props}
        state={{
          ...initialStudioState,
          selectedVoice: firstVoiceId,
          scriptBodies: ['正文'],
        }}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: '生成克隆声音' }));
    await waitFor(() =>
      expect(apiMock.createVoiceJob).toHaveBeenCalledTimes(1),
    );

    rerender(
      <VoiceStep
        {...props}
        state={{
          ...initialStudioState,
          selectedVoice: secondVoiceId,
          scriptBodies: ['正文'],
        }}
      />,
    );
    pendingCreate.resolve(queuedVoiceJob);
    await act(async () => {
      await pendingCreate.promise;
    });

    const signal = apiMock.createVoiceJob.mock.calls[0]?.[1] as
      | AbortSignal
      | undefined;
    expect(signal?.aborted).toBe(true);
    expect(update).toHaveBeenCalledWith({
      voiceGenerationIntent: expect.objectContaining({
        idempotencyKey: 'voice-ui-key',
        referenceVoiceId: firstVoiceId,
      }),
    });
    expect(update).not.toHaveBeenCalledWith(
      expect.objectContaining({ voiceJob: expect.anything() }),
    );
    expect(onToast).not.toHaveBeenCalled();
  });

  it('aborts confirmation and does not continue after unmount', async () => {
    const pendingConfirmation = deferred<DigitalHumanJob>();
    apiMock.confirmVoiceJob.mockReturnValueOnce(pendingConfirmation.promise);
    const update = vi.fn();
    const onNext = vi.fn();
    const { unmount } = render(
      <VoiceStep
        state={{ ...initialStudioState, voiceJob: succeededVoiceJob }}
        update={update}
        onFinish={vi.fn()}
        onNext={onNext}
        onPrevious={vi.fn()}
        onToast={vi.fn()}
      />,
    );
    const confirmButton = screen.getByRole('button', {
      name: /确认声音，去生成底片/,
    });
    await waitFor(() => expect(confirmButton).toBeEnabled());
    fireEvent.click(confirmButton);
    await waitFor(() =>
      expect(apiMock.confirmVoiceJob).toHaveBeenCalledTimes(1),
    );

    unmount();
    pendingConfirmation.resolve({
      ...succeededVoiceJob,
      voiceConfirmed: true,
    });
    await act(async () => {
      await pendingConfirmation.promise;
    });

    const signal = apiMock.confirmVoiceJob.mock.calls[0]?.[1] as
      | AbortSignal
      | undefined;
    expect(signal?.aborted).toBe(true);
    expect(update).not.toHaveBeenCalled();
    expect(onNext).not.toHaveBeenCalled();
  });
});
