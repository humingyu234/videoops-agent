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

interface VoiceStepProps {
  state: StudioState;
  update: (patch: Partial<StudioState>) => void;
  onPrevious: () => void;
  onNext: () => void;
  onFinish: () => void;
  onToast: (message: string) => void;
}

const VoiceStep: React.FC<VoiceStepProps> = ({
  state,
  update,
  onPrevious,
  onNext,
  onFinish,
  onToast,
}) => {
  const selectedScriptText = state.scriptBodies[state.selectedScript] ?? '';
  const [script, setScript] = useState(selectedScriptText);
  const [submitting, setSubmitting] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [actionError, setActionError] = useState<string>();
  const [pollingPauseMessage, setPollingPauseMessage] = useState<string>();
  const [pollingRun, setPollingRun] = useState(0);
  const [mediaUrl, setMediaUrl] = useState<string>();
  const [mediaError, setMediaError] = useState<string>();
  const [mediaRun, setMediaRun] = useState(0);
  const mountedRef = useRef(true);
  const createGenerationRef = useRef(0);
  const confirmGenerationRef = useRef(0);
  const createControllerRef = useRef<AbortController | undefined>(undefined);
  const confirmControllerRef = useRef<AbortController | undefined>(undefined);
  const job = state.voiceJob;
  const pollingJobId = isActiveDigitalHumanJob(job) ? job.jobId : undefined;
  const outputJobId =
    isSucceededDigitalHumanJob(job) && job.outputAvailable
      ? job.jobId
      : undefined;

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      createGenerationRef.current += 1;
      confirmGenerationRef.current += 1;
      createControllerRef.current?.abort();
      confirmControllerRef.current?.abort();
    };
  }, []);

  useEffect(() => {
    createGenerationRef.current += 1;
    createControllerRef.current?.abort();
    createControllerRef.current = undefined;
    setSubmitting(false);
    setScript(selectedScriptText);
  }, [selectedScriptText, state.selectedVoice, state.selectedScript]);

  useEffect(() => {
    const intent = state.voiceGenerationIntent;
    if (
      intent &&
      (intent.referenceVoiceId !== state.selectedVoice ||
        intent.scriptText !== selectedScriptText.trim())
    ) {
      update({
        videoGenerationIntent: null,
        videoJob: null,
        voiceGenerationIntent: null,
        voiceJob: null,
      });
    }
  }, [
    selectedScriptText,
    state.selectedVoice,
    state.voiceGenerationIntent,
    update,
  ]);

  useEffect(() => {
    createGenerationRef.current += 1;
    confirmGenerationRef.current += 1;
    createControllerRef.current?.abort();
    confirmControllerRef.current?.abort();
    createControllerRef.current = undefined;
    confirmControllerRef.current = undefined;
    setSubmitting(false);
    setConfirming(false);
    setPollingPauseMessage(undefined);
  }, [job?.jobId]);

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
        update({ voiceJob: current });
        if (isActiveDigitalHumanJob(current)) {
          timer = window.setTimeout(
            () => void poll(),
            DIGITAL_HUMAN_POLL_INTERVAL_MS,
          );
        }
      } catch (error) {
        if (!active || isAbortError(error)) return;
        const message = getErrorMessage(
          error,
          '声音任务状态读取失败，请稍后重试。',
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
        if (active && !isAbortError(error)) {
          setMediaError(
            getErrorMessage(error, '声音媒体读取失败，请稍后重试。'),
          );
        }
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

  const createVoiceJob = async () => {
    const scriptText = script.trim();
    if (!state.selectedVoice) return setActionError('请先选择参考声音。');
    if (!scriptText || scriptText.length > 1000) {
      return setActionError('口播正文需为 1～1000 个字符。');
    }
    const existingIntent = state.voiceGenerationIntent;
    const sameIntent =
      existingIntent?.referenceVoiceId === state.selectedVoice &&
      existingIntent.scriptText === scriptText;
    const voiceGenerationIntent = sameIntent
      ? existingIntent
      : {
          idempotencyKey: createIdempotencyKey('voice'),
          referenceVoiceId: state.selectedVoice,
          scriptText,
        };
    if (!sameIntent) update({ voiceGenerationIntent });
    createControllerRef.current?.abort();
    const controller = new AbortController();
    const generation = createGenerationRef.current + 1;
    createGenerationRef.current = generation;
    createControllerRef.current = controller;
    setSubmitting(true);
    setActionError(undefined);
    try {
      const created = await digitalHumanApi.createVoiceJob(
        {
          idempotencyKey: voiceGenerationIntent.idempotencyKey,
          referenceVoiceId: state.selectedVoice,
          scriptText,
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
      const scriptBodies = [...state.scriptBodies];
      scriptBodies[state.selectedScript] = scriptText;
      update({
        scriptBodies,
        videoGenerationIntent: null,
        videoJob: null,
        voiceJob: created,
      });
      onToast('声音任务已创建');
    } catch (error) {
      if (
        mountedRef.current &&
        generation === createGenerationRef.current &&
        !isAbortError(error)
      ) {
        setActionError(
          getErrorMessage(error, '声音任务创建失败，请稍后重试。'),
        );
      }
    } finally {
      if (mountedRef.current && generation === createGenerationRef.current) {
        createControllerRef.current = undefined;
        setSubmitting(false);
      }
    }
  };

  const confirmAndContinue = async () => {
    if (!job || !isSucceededDigitalHumanJob(job) || !job.outputAvailable)
      return;
    if (job.voiceConfirmed) return onNext();
    confirmControllerRef.current?.abort();
    const controller = new AbortController();
    const generation = confirmGenerationRef.current + 1;
    confirmGenerationRef.current = generation;
    confirmControllerRef.current = controller;
    setConfirming(true);
    setActionError(undefined);
    try {
      const confirmed = await digitalHumanApi.confirmVoiceJob(
        job.jobId,
        controller.signal,
      );
      if (
        !mountedRef.current ||
        controller.signal.aborted ||
        generation !== confirmGenerationRef.current
      ) {
        return;
      }
      update({ voiceJob: confirmed });
      onNext();
    } catch (error) {
      if (
        mountedRef.current &&
        generation === confirmGenerationRef.current &&
        !isAbortError(error)
      ) {
        setActionError(getErrorMessage(error, '声音确认失败，请稍后重试。'));
      }
    } finally {
      if (mountedRef.current && generation === confirmGenerationRef.current) {
        confirmControllerRef.current = undefined;
        setConfirming(false);
      }
    }
  };

  const changeScript = (nextScript: string) => {
    if (nextScript === script) return;
    createGenerationRef.current += 1;
    createControllerRef.current?.abort();
    createControllerRef.current = undefined;
    setSubmitting(false);
    setScript(nextScript);
    const scriptBodies = [...state.scriptBodies];
    scriptBodies[state.selectedScript] = nextScript;
    update({
      scriptBodies,
      videoGenerationIntent: null,
      videoJob: null,
      voiceGenerationIntent: null,
      voiceJob: null,
    });
  };

  const active = isActiveDigitalHumanJob(job);
  const succeeded = isSucceededDigitalHumanJob(job);
  const failed = isFailedDigitalHumanJob(job);

  return (
    <>
      <div>
        <div className="card">
          {!job && (
            <div className="generation-center">
              <h3>生成克隆声音</h3>
              <p>将已确认正文与参考音频提交至平台声音任务。</p>
              <button
                aria-label="生成克隆声音"
                className="btn btn-primary"
                disabled={submitting}
                type="button"
                onClick={() => void createVoiceJob()}
              >
                {submitting ? '正在提交…' : '生成克隆声音'}
              </button>
            </div>
          )}
          {active && job && !pollingPauseMessage && (
            <div className="generation-card" role="status">
              <div className="generation-title">
                <span className="spinner" />
                <div>
                  <b>{DIGITAL_HUMAN_STAGE_LABELS[job.stage]}</b>
                  <small>平台任务 {job.jobId}</small>
                </div>
              </div>
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
            <div className="generation-failed" role="alert">
              <h3>声音任务状态查询已暂停</h3>
              <p>{pollingPauseMessage}</p>
              <button
                aria-label="重新查询声音任务"
                className="btn btn-outline"
                type="button"
                onClick={retryPolling}
              >
                重新查询
              </button>
            </div>
          )}
          {succeeded && job && (
            <div>
              <div className="voice-generated-head">
                <b>
                  {job.voiceConfirmed ? '克隆声音已确认' : '克隆声音已生成'}
                </b>
                <span className="numeric">{job.progress}%</span>
              </div>
              {mediaUrl ? (
                <>
                  {/* biome-ignore lint/a11y/useMediaCaption: 生成式声音预览暂时没有可用字幕轨。 */}
                  <audio aria-label="克隆声音试听" controls src={mediaUrl} />
                </>
              ) : mediaError ? (
                <div role="alert">
                  <p>{mediaError}</p>
                  <button
                    aria-label="重新读取声音文件"
                    className="btn btn-outline"
                    type="button"
                    onClick={retryMedia}
                  >
                    重新读取
                  </button>
                </div>
              ) : (
                <p role="status">正在读取声音文件…</p>
              )}
            </div>
          )}
          {failed && job && (
            <div className="generation-failed" role="alert">
              <h3>声音生成失败</h3>
              <p>{job.errorMessage ?? '声音生成失败，请稍后重试。'}</p>
            </div>
          )}
          {actionError && <p role="alert">{actionError}</p>}
        </div>
        <div className="card voice-script-card">
          <div className="section-title small">文案</div>
          <textarea
            aria-label="口播正文"
            className="input voice-script"
            disabled={Boolean(job)}
            maxLength={1000}
            value={script}
            onChange={(event) => changeScript(event.target.value)}
          />
          <p className="field-help">
            创建任务后正文快照不可修改；如需修改，请返回重新选择素材。
          </p>
        </div>
      </div>
      <StepFooter
        step={3}
        nextLabel={confirming ? '正在确认…' : '确认声音，去生成底片'}
        nextEnabled={Boolean(
          succeeded && job.outputAvailable && mediaUrl && !confirming,
        )}
        onPrevious={onPrevious}
        onNext={() => void confirmAndContinue()}
        onFinish={onFinish}
      />
    </>
  );
};

export default VoiceStep;
