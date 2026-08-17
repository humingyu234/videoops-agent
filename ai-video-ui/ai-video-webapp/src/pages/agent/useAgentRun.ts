import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  type AgentApi,
  getRuntimeAgentApi,
} from '@/services/ai-video/agent/api';
import type {
  AgentPlanStartAt,
  AgentRunDetail,
  AgentRunStatus,
  CreateAgentRunInput,
} from '@/services/ai-video/agent/types';
import { getRuntimeRuoYiAdapter } from '@/services/ai-video/core/runtimeRuoYiAdapter';
import {
  type CreationAssetsApi,
  createCreationAssetsApi,
} from '@/services/ai-video/creation-assets/api';

const LAST_RUN_STORAGE_KEY = 'videoops-agent:last-owned-run-id';
const DEFAULT_POLL_INTERVAL_MS = 2_000;
const MAX_CONSECUTIVE_POLL_FAILURES = 5;
const POSITIVE_DECIMAL_ID = /^[1-9]\d{0,18}$/;
const ACTIVE_STATUSES = new Set<AgentRunStatus>([
  'queued',
  'running',
  'waiting_external_task',
]);
const CANCELLABLE_STATUSES = new Set<AgentRunStatus>([
  ...ACTIVE_STATUSES,
  'waiting_input',
  'waiting_approval',
]);

export type AgentFormValues = {
  startAt: AgentPlanStartAt;
  projectTitle?: string;
  scriptText?: string;
  portraitId?: string;
  referenceVoiceId?: string;
  voiceJobId?: string;
  videoJobId?: string;
  projectId?: string;
  expectedRevision?: string;
  taskId?: string;
};

type UseAgentRunOptions = {
  agentClient?: AgentApi;
  assetsClient?: Pick<CreationAssetsApi, 'content'>;
  pollingIntervalMs?: number;
};

type IntentIdentity = { fingerprint: string; idempotencyKey: string };

function messageFrom(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

function createIntentKey(): string {
  const random = globalThis.crypto?.randomUUID?.().replaceAll('-', '');
  if (random) return `agent-${random}`.slice(0, 48);
  return `agent-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`.slice(
    0,
    48,
  );
}

function requiredText(value: string | undefined, field: string): string {
  const normalized = value?.trim();
  if (!normalized) throw new Error(`${field}不能为空。`);
  return normalized;
}

function requiredId(value: string | undefined, field: string): string {
  const normalized = value?.trim();
  if (!normalized || !POSITIVE_DECIMAL_ID.test(normalized)) {
    throw new Error(`${field}必须是正十进制 ID。`);
  }
  return normalized;
}

function createInput(
  values: AgentFormValues,
  idempotencyKey: string,
): CreateAgentRunInput {
  switch (values.startAt) {
    case 'new':
      return {
        startAt: 'new',
        projectTitle: requiredText(values.projectTitle, '项目标题'),
        scriptText: requiredText(values.scriptText, '中文口播脚本'),
        portraitId: requiredId(values.portraitId, '人物 ID'),
        referenceVoiceId: requiredId(values.referenceVoiceId, '原声音 ID'),
        idempotencyKey,
      };
    case 'voice_job':
      return {
        startAt: 'voice_job',
        voiceJobId: requiredId(values.voiceJobId, '成功声音任务 ID'),
        portraitId: requiredId(values.portraitId, '人物 ID'),
        projectTitle: requiredText(values.projectTitle, '项目标题'),
        idempotencyKey,
      };
    case 'video_job':
      return {
        startAt: 'video_job',
        videoJobId: requiredId(values.videoJobId, '成功视频任务 ID'),
        projectTitle: requiredText(values.projectTitle, '项目标题'),
        idempotencyKey,
      };
    case 'project':
      return {
        startAt: 'project',
        projectId: requiredId(values.projectId, '项目 ID'),
        expectedRevision: requiredId(values.expectedRevision, '草稿版本'),
        idempotencyKey,
      };
    case 'render_task':
      return {
        startAt: 'render_task',
        taskId: requiredId(values.taskId, '成功渲染任务 ID'),
        idempotencyKey,
      };
    default:
      throw new Error('执行起点无效。');
  }
}

function readRouteRunId(): string | undefined | null {
  if (typeof window === 'undefined') return undefined;
  const value = new URLSearchParams(window.location.search).get('runId');
  if (value === null) return undefined;
  return POSITIVE_DECIMAL_ID.test(value) ? value : null;
}

function readLastRunId(): string | undefined {
  if (typeof window === 'undefined') return undefined;
  const value = window.localStorage.getItem(LAST_RUN_STORAGE_KEY);
  return value && POSITIVE_DECIMAL_ID.test(value) ? value : undefined;
}

export function useAgentRun({
  agentClient: suppliedAgentClient,
  assetsClient: suppliedAssetsClient,
  pollingIntervalMs = DEFAULT_POLL_INTERVAL_MS,
}: UseAgentRunOptions) {
  const agentClient = suppliedAgentClient ?? getRuntimeAgentApi();
  const assetsClient = useMemo(
    () =>
      suppliedAssetsClient ?? createCreationAssetsApi(getRuntimeRuoYiAdapter()),
    [suppliedAssetsClient],
  );
  const [detail, setDetail] = useState<AgentRunDetail>();
  const detailRef = useRef<AgentRunDetail | undefined>(undefined);
  const [lastRunId, setLastRunId] = useState(readLastRunId);
  const [busyAction, setBusyAction] = useState<string>();
  const [actionError, setActionError] = useState<string>();
  const [pollingNotice, setPollingNotice] = useState<string>();
  const [pollingPaused, setPollingPaused] = useState(false);
  const [pollingGeneration, setPollingGeneration] = useState(0);
  const intentRef = useRef<IntentIdentity | undefined>(undefined);
  const initialRouteRead = useRef(false);
  const [outputUrl, setOutputUrl] = useState<string>();
  const [outputError, setOutputError] = useState<string>();
  const [outputLoading, setOutputLoading] = useState(false);

  const rememberDetail = useCallback((next: AgentRunDetail) => {
    detailRef.current = next;
    setDetail(next);
    setLastRunId(next.run.runId);
    window.localStorage.setItem(LAST_RUN_STORAGE_KEY, next.run.runId);
  }, []);

  const loadOwnedRun = useCallback(
    async (runId: string) => {
      setBusyAction('read');
      setActionError(undefined);
      try {
        rememberDetail(await agentClient.get(runId));
        setPollingPaused(false);
      } catch (error) {
        setActionError(messageFrom(error, '无法读取该 AgentRun。'));
      } finally {
        setBusyAction(undefined);
      }
    },
    [agentClient, rememberDetail],
  );

  useEffect(() => {
    if (initialRouteRead.current) return;
    initialRouteRead.current = true;
    const routeRunId = readRouteRunId();
    if (routeRunId === null) {
      setActionError('链接中的 AgentRun ID 无效，未发起读取。');
    } else if (routeRunId) {
      void loadOwnedRun(routeRunId);
    }
  }, [loadOwnedRun]);

  const pollingActive = Boolean(
    detail && ACTIVE_STATUSES.has(detail.run.status),
  );

  useEffect(() => {
    if (!pollingActive || pollingPaused || !detail?.run.runId) return undefined;
    let disposed = false;
    let timer: number | undefined;
    let failureCount = 0;

    const schedule = (delay: number) => {
      timer = window.setTimeout(() => void tick(), delay);
    };
    const tick = async () => {
      const current = detailRef.current;
      if (!current || !ACTIVE_STATUSES.has(current.run.status)) return;
      if (
        (typeof navigator !== 'undefined' && !navigator.onLine) ||
        (typeof document !== 'undefined' &&
          document.visibilityState === 'hidden')
      ) {
        setPollingNotice('页面离线或位于后台，已暂停请求但保留当前任务身份。');
        schedule(Math.max(pollingIntervalMs, 5_000));
        return;
      }
      try {
        const next = await agentClient.advance(current.run.runId, {
          rowVersion: current.run.rowVersion,
          contractRevision: current.run.contractRevision,
        });
        if (disposed) return;
        failureCount = 0;
        setPollingNotice(undefined);
        rememberDetail(next);
        if (ACTIVE_STATUSES.has(next.run.status)) {
          schedule(pollingIntervalMs);
        }
      } catch {
        if (disposed) return;
        failureCount += 1;
        if (failureCount >= MAX_CONSECUTIVE_POLL_FAILURES) {
          setPollingPaused(true);
          setPollingNotice(
            '连续查询失败，自动推进已暂停。任务身份已保留，请手动恢复。',
          );
          return;
        }
        setPollingNotice(
          `查询暂时失败（${failureCount}/${MAX_CONSECUTIVE_POLL_FAILURES}），将继续读取同一个任务。`,
        );
        schedule(Math.min(pollingIntervalMs * 2 ** failureCount, 30_000));
      }
    };

    schedule(pollingIntervalMs);
    return () => {
      disposed = true;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [
    agentClient,
    detail?.run.runId,
    pollingActive,
    pollingGeneration,
    pollingIntervalMs,
    pollingPaused,
    rememberDetail,
  ]);

  useEffect(() => {
    const assetId = detail?.finalOutputAssetId;
    const controller = new AbortController();
    let objectUrl: string | undefined;
    let active = true;
    setOutputUrl(undefined);
    setOutputError(undefined);
    setOutputLoading(Boolean(assetId));
    if (!assetId) return () => controller.abort();

    void assetsClient
      .content(assetId, { signal: controller.signal })
      .then((blob) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setOutputUrl(objectUrl);
      })
      .catch((error: unknown) => {
        if (active && !controller.signal.aborted) {
          setOutputError(messageFrom(error, '无法读取最终成品。'));
        }
      })
      .finally(() => {
        if (active) setOutputLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [assetsClient, detail?.finalOutputAssetId]);

  const createRun = async (values: AgentFormValues) => {
    let candidate: CreateAgentRunInput;
    try {
      candidate = createInput(values, 'intent');
    } catch (error) {
      setActionError(messageFrom(error, '创建参数无效。'));
      return;
    }
    const fingerprint = JSON.stringify({ ...candidate, idempotencyKey: null });
    if (intentRef.current?.fingerprint !== fingerprint) {
      intentRef.current = { fingerprint, idempotencyKey: createIntentKey() };
    }
    const input = createInput(values, intentRef.current.idempotencyKey);
    setBusyAction('create');
    setActionError(undefined);
    try {
      const created = await agentClient.create(input);
      rememberDetail(created);
      setPollingPaused(false);
      window.history.replaceState({}, '', `/agent?runId=${created.run.runId}`);
    } catch (error) {
      setActionError(messageFrom(error, '创建 AgentRun 失败。'));
    } finally {
      setBusyAction(undefined);
    }
  };

  const decideApproval = async (approved: boolean) => {
    if (!detail?.pendingApproval) return;
    const { run, pendingApproval } = detail;
    setBusyAction(approved ? 'approve' : 'reject');
    setActionError(undefined);
    try {
      rememberDetail(
        await agentClient.decideApproval(
          run.runId,
          pendingApproval.approvalId,
          {
            rowVersion: run.rowVersion,
            contractRevision: run.contractRevision,
            approvalRevision: pendingApproval.revision,
            type: pendingApproval.type,
            approved,
          },
        ),
      );
      setPollingPaused(false);
    } catch (error) {
      setActionError(messageFrom(error, '审批提交失败，请重新读取后再试。'));
    } finally {
      setBusyAction(undefined);
    }
  };

  const cancelRun = async () => {
    if (!detail) return;
    setBusyAction('cancel');
    setActionError(undefined);
    try {
      rememberDetail(
        await agentClient.cancel(detail.run.runId, {
          rowVersion: detail.run.rowVersion,
          contractRevision: detail.run.contractRevision,
        }),
      );
      setPollingPaused(false);
    } catch (error) {
      setActionError(messageFrom(error, '取消失败，请重新读取后再试。'));
    } finally {
      setBusyAction(undefined);
    }
  };

  const downloadOutput = () => {
    if (!outputUrl || !detail?.finalOutputAssetId) return;
    const anchor = document.createElement('a');
    anchor.href = outputUrl;
    anchor.download = `videoops-agent-${detail.finalOutputAssetId}.mp4`;
    anchor.click();
  };

  const resumePolling = () => {
    setPollingPaused(false);
    setPollingNotice(undefined);
    setPollingGeneration((value) => value + 1);
  };

  return {
    actionError,
    busyAction,
    cancelRun,
    createRun,
    decideApproval,
    detail,
    downloadOutput,
    isCancellable: Boolean(
      detail && CANCELLABLE_STATUSES.has(detail.run.status),
    ),
    lastRunId,
    loadOwnedRun,
    outputError,
    outputLoading,
    outputUrl,
    pollingNotice,
    pollingPaused,
    resumePolling,
  };
}
