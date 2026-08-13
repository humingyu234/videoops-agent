import React, { useEffect, useRef, useState } from 'react';
import { getErrorMessage, isAbortError } from '@/services/ai-video/core/errors';
import {
  createIdempotencyKey,
  digitalHumanApi,
} from '@/services/ai-video/digitalHuman/api';
import {
  DIGITAL_HUMAN_POLL_INTERVAL_MS,
  DIGITAL_HUMAN_POLL_MAX_RETRIES,
  getDigitalHumanPollRetryDelay,
  shouldStopDigitalHumanPolling,
} from '@/services/ai-video/digitalHuman/polling';
import {
  DIGITAL_HUMAN_STAGE_LABELS,
  isActiveDigitalHumanJob,
  isFailedDigitalHumanJob,
  isSucceededDigitalHumanJob,
} from '@/services/ai-video/digitalHuman/types';
import StepFooter from '../components/StepFooter';
import type { StudioState } from '../model';

interface BaseStepProps {
  state: StudioState;
  update: (patch: Partial<StudioState>) => void;
  onPrevious: () => void;
  onNext: () => void;
  onFinish: () => void;
  onToast: (message: string) => void;
}

const BaseStep: React.FC<BaseStepProps> = ({
  state,
  update,
  onPrevious,
  onNext,
  onFinish,
  onToast,
}) => {
  const [submitting, setSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string>();
  const [pollingPauseMessage, setPollingPauseMessage] = useState<string>();
  const [pollingRun, setPollingRun] = useState(0);
  const [mediaUrl, setMediaUrl] = useState<string>();
  const [mediaError, setMediaError] = useState<string>();
  const [mediaRun, setMediaRun] = useState(0);
  const mountedRef = useRef(true);
  const createGenerationRef = useRef(0);
  const createControllerRef = useRef<AbortController | undefined>(undefined);
  const job = state.videoJob;
  const pollingJobId = isActiveDigitalHumanJob(job) ? job.jobId : undefined;
  const outputJobId =
    isSucceededDigitalHumanJob(job) && job.outputAvailable
      ? job.jobId
      : undefined;
  const script = state.scriptBodies[state.selectedScript] ?? '';

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      createGenerationRef.current += 1;
      createControllerRef.current?.abort();
    };
  }, []);

  useEffect(() => {
    createGenerationRef.current += 1;
    createControllerRef.current?.abort();
    createControllerRef.current = undefined;
    setSubmitting(false);
    setPollingPauseMessage(undefined);
  }, [job?.jobId, state.selectedAvatar, state.voiceJob?.jobId]);

  useEffect(() => {
    const intent = state.videoGenerationIntent;
    if (
      intent &&
      (intent.portraitId !== state.selectedAvatar ||
        intent.voiceJobId !== state.voiceJob?.jobId)
    ) {
      update({ videoGenerationIntent: null, videoJob: null });
    }
  }, [
    state.selectedAvatar,
    state.videoGenerationIntent,
    state.voiceJob?.jobId,
    update,
  ]);

  useEffect(() => {
    if (!pollingJobId) return;
    const controller = new AbortController();
    let timer: number | undefined;
    let active = true;
    let consecutiveFailures = 0;
    const poll = async () => {
      try {
        const current = await digitalHumanApi.getJob(
          pollingJobId,
          controller.signal,
        );
        if (!active) return;
        consecutiveFailures = 0;
        setActionError(undefined);
        setPollingPauseMessage(undefined);
        update({ videoJob: current });
        if (isActiveDigitalHumanJob(current))
          timer = window.setTimeout(
            () => void poll(),
            DIGITAL_HUMAN_POLL_INTERVAL_MS,
          );
      } catch (error) {
        if (!active || isAbortError(error)) return;
        const message = getErrorMessage(
          error,
          '视频任务状态读取失败，请稍后重试。',
        );
        if (shouldStopDigitalHumanPolling(error)) {
          setActionError(undefined);
          setPollingPauseMessage(message);
          return;
        }
        consecutiveFailures += 1;
        if (consecutiveFailures > DIGITAL_HUMAN_POLL_MAX_RETRIES) {
          setActionError(undefined);
          setPollingPauseMessage(message);
          return;
        }
        setActionError(message);
        timer = window.setTimeout(
          () => void poll(),
          getDigitalHumanPollRetryDelay(consecutiveFailures),
        );
      }
    };
    void poll();
    return () => {
      active = false;
      controller.abort();
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [pollingJobId, pollingRun, update]);

  useEffect(() => {
    setMediaUrl(undefined);
    setMediaError(undefined);
    if (!outputJobId) return;
    const controller = new AbortController();
    let objectUrl: string | undefined;
    let active = true;
    void digitalHumanApi
      .getJobMedia(outputJobId, controller.signal)
      .then((media) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(media);
        setMediaError(undefined);
        setMediaUrl(objectUrl);
      })
      .catch((error: unknown) => {
        if (active && !isAbortError(error))
          setMediaError(
            getErrorMessage(error, '视频媒体读取失败，请稍后重试。'),
          );
      });
    return () => {
      active = false;
      controller.abort();
      if (objectUrl && typeof URL.revokeObjectURL === 'function')
        URL.revokeObjectURL(objectUrl);
    };
  }, [mediaRun, outputJobId]);

  const retryPolling = () => {
    setActionError(undefined);
    setPollingPauseMessage(undefined);
    setPollingRun((current) => current + 1);
  };

  const retryMedia = () => {
    setMediaError(undefined);
    setMediaRun((current) => current + 1);
  };

  const createVideoJob = async (forceNewIntent = false) => {
    if (!state.selectedAvatar) return setActionError('请先选择人物形象。');
    if (
      !state.voiceJob ||
      !isSucceededDigitalHumanJob(state.voiceJob) ||
      !state.voiceJob.voiceConfirmed
    ) {
      return setActionError('请先生成并确认声音。');
    }
    const existingIntent = state.videoGenerationIntent;
    const sameIntent =
      !forceNewIntent &&
      existingIntent?.portraitId === state.selectedAvatar &&
      existingIntent.voiceJobId === state.voiceJob.jobId;
    const videoGenerationIntent = sameIntent
      ? existingIntent
      : {
          idempotencyKey: createIdempotencyKey('video'),
          portraitId: state.selectedAvatar,
          voiceJobId: state.voiceJob.jobId,
        };
    if (!sameIntent) update({ videoGenerationIntent });
    createControllerRef.current?.abort();
    const controller = new AbortController();
    const generation = createGenerationRef.current + 1;
    createGenerationRef.current = generation;
    createControllerRef.current = controller;
    setSubmitting(true);
    setActionError(undefined);
    try {
      const created = await digitalHumanApi.createVideoJob(
        {
          idempotencyKey: videoGenerationIntent.idempotencyKey,
          portraitId: state.selectedAvatar,
          voiceJobId: state.voiceJob.jobId,
        },
        controller.signal,
      );
      if (
        !mountedRef.current ||
        controller.signal.aborted ||
        generation !== createGenerationRef.current
      ) {
        return;
      }
      update({ videoJob: created });
      onToast('视频任务已创建');
    } catch (error) {
      if (
        mountedRef.current &&
        generation === createGenerationRef.current &&
        !isAbortError(error)
      ) {
        setActionError(
          getErrorMessage(error, '视频任务创建失败，请稍后重试。'),
        );
      }
    } finally {
      if (mountedRef.current && generation === createGenerationRef.current) {
        createControllerRef.current = undefined;
        setSubmitting(false);
      }
    }
  };

  const active = isActiveDigitalHumanJob(job);
  const succeeded = isSucceededDigitalHumanJob(job);
  const failed = isFailedDigitalHumanJob(job);

  return (
    <>
      <div className="base-grid">
        <div>
          {!job && (
            <div className="card generation-center">
              <h3>生成数字人底片</h3>
              <p>人物图片与已确认声音将提交至平台视频任务。</p>
              <button
                aria-label="生成数字人底片"
                className="btn btn-primary"
                disabled={submitting}
                type="button"
                onClick={() => void createVideoJob()}
              >
                {submitting ? '正在提交…' : '生成数字人底片'}
              </button>
            </div>
          )}
          {active && job && !pollingPauseMessage && (
            <div className="card generation-center" role="status">
              <span className="spinner large" />
              <h3>{DIGITAL_HUMAN_STAGE_LABELS[job.stage]}</h3>
              <p>平台任务 {job.jobId}</p>
              <div className="progress-track">
                <i style={{ width: `${job.progress}%` }} />
              </div>
              <div className="progress-meta">
                <span>{job.progress}%</span>
                <span>{DIGITAL_HUMAN_STAGE_LABELS[job.stage]}</span>
              </div>
            </div>
          )}
          {active && job && pollingPauseMessage && (
            <div className="card generation-failed" role="alert">
              <h3>视频任务状态查询已暂停</h3>
              <p>{pollingPauseMessage}</p>
              <button
                aria-label="重新查询视频任务"
                className="btn btn-outline"
                type="button"
                onClick={retryPolling}
              >
                重新查询
              </button>
            </div>
          )}
          {succeeded && job && (
            <div className="preview-stage">
              {mediaUrl ? (
                <>
                  {/* biome-ignore lint/a11y/useMediaCaption: 生成式视频预览暂时没有可用字幕轨。 */}
                  <video
                    aria-label="数字人底片预览"
                    className="preview-phone"
                    controls
                    src={mediaUrl}
                  />
                </>
              ) : mediaError ? (
                <div className="card" role="alert">
                  <p>{mediaError}</p>
                  <button
                    aria-label="重新读取数字人视频"
                    className="btn btn-outline"
                    type="button"
                    onClick={retryMedia}
                  >
                    重新读取
                  </button>
                </div>
              ) : (
                <div className="card" role="status">
                  正在读取数字人视频…
                </div>
              )}
            </div>
          )}
          {failed && job && (
            <div className="card generation-failed" role="alert">
              <h3>视频生成失败</h3>
              <p>{job.errorMessage ?? '视频生成失败，请稍后重试。'}</p>
              <button
                aria-label="重新生成数字人底片"
                className="btn btn-primary"
                disabled={submitting}
                type="button"
                onClick={() => void createVideoJob(true)}
              >
                {submitting ? '正在重新提交…' : '重新生成数字人底片'}
              </button>
            </div>
          )}
          {actionError && <p role="alert">{actionError}</p>}
        </div>
        <aside className="base-assets">
          <div className="card card-tight">
            <div className="prop-title">文案</div>
            <div className="base-script">{script}</div>
          </div>
          <div className="card card-tight">
            <div className="prop-title">人物图片</div>
            <p>{state.selectedAvatar ?? '未选择'}</p>
          </div>
          <div className="card card-tight">
            <div className="prop-title">已确认声音任务</div>
            <p>{state.voiceJob?.jobId ?? '未确认'}</p>
          </div>
        </aside>
      </div>
      <StepFooter
        step={4}
        nextLabel="进入时间轴编辑"
        nextEnabled={Boolean(succeeded && mediaUrl)}
        onPrevious={onPrevious}
        onNext={onNext}
        onFinish={onFinish}
      />
    </>
  );
};

export default BaseStep;
