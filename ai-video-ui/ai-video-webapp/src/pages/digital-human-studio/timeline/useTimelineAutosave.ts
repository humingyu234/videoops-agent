import { useEffect, useRef, useState } from 'react';
import {
  ApiError,
  getErrorMessage,
  isAbortError,
} from '@/services/ai-video/core/errors';
import type {
  CreationTimelineApi,
  SaveTimelineDraftRequest,
  SaveTimelineDraftResult,
} from '@/services/ai-video/creation-timeline/api';
import { createIdempotencyKeyStore } from '@/services/ai-video/creation-timeline/idempotency';
import type {
  TimelineDocument,
  TimelineDraft,
  TimelineSaveStatus,
} from '@/services/ai-video/creation-timeline/types';

type AutosaveApi = Pick<CreationTimelineApi, 'getDraft' | 'saveDraft'>;

type DraftSnapshot = {
  fingerprint: string;
  timeline: TimelineDocument;
};

type DraftBaseline = DraftSnapshot & {
  contentHash: string;
  projectId: string;
  revision: string;
};

type ActiveSave = DraftSnapshot & {
  discardResult: boolean;
  expectedRevision: string;
  inFlight: boolean;
  projectId: string;
  request: SaveTimelineDraftRequest;
  runId: number;
  unknownResult: boolean;
};

type SupersededRecovery = {
  projectId: string;
  requestKey: string;
  runId: number;
};

function fingerprintTimeline(timeline: TimelineDocument): string {
  return JSON.stringify(timeline);
}

function isUnknownSaveResult(error: unknown): boolean {
  return !(error instanceof ApiError);
}

export function useTimelineAutosave({
  api,
  projectId,
  timeline,
  revision,
  debounceMs = 500,
}: {
  api: AutosaveApi;
  projectId: string | undefined;
  timeline: TimelineDocument | undefined;
  revision: string | undefined;
  debounceMs?: number;
}) {
  const apiRef = useRef(api);
  apiRef.current = api;
  const keys = useRef(createIdempotencyKeyStore()).current;
  const activeRef = useRef<ActiveSave | undefined>(undefined);
  const baselineRef = useRef<DraftBaseline | undefined>(undefined);
  const conflictPausedRef = useRef(false);
  const currentRef = useRef<{
    fingerprint?: string;
    projectId?: string;
    revision?: string;
    timeline?: TimelineDocument;
  }>({});
  const mountedRef = useRef(true);
  const pendingRef = useRef<DraftSnapshot | undefined>(undefined);
  const recoveryRef = useRef<SupersededRecovery | undefined>(undefined);
  const runIdRef = useRef(0);
  const timerRef = useRef<number | undefined>(undefined);
  const [hasUnconfirmedChanges, setHasUnconfirmedChanges] = useState(false);
  const [lastSaved, setLastSaved] = useState<TimelineDraft>();
  const [saveStatus, setSaveStatus] = useState<TimelineSaveStatus>(() => ({
    contentHash: '',
    kind: 'saved',
    revision: revision ?? '',
  }));

  currentRef.current = timeline
    ? {
        fingerprint: fingerprintTimeline(timeline),
        projectId,
        revision,
        timeline,
      }
    : { projectId, revision };

  function clearScheduledSave() {
    if (timerRef.current !== undefined) {
      window.clearTimeout(timerRef.current);
      timerRef.current = undefined;
    }
  }

  function isCurrentRun(runId: number) {
    return mountedRef.current && runId === runIdRef.current;
  }

  function syncBeforeUnload() {
    const baseline = baselineRef.current;
    const current = currentRef.current;
    const localDiffersFromBaseline = Boolean(
      baseline &&
        current.projectId === baseline.projectId &&
        current.fingerprint !== baseline.fingerprint,
    );
    setHasUnconfirmedChanges(
      Boolean(
        (activeRef.current && !activeRef.current.discardResult) ||
          pendingRef.current ||
          recoveryRef.current ||
          conflictPausedRef.current ||
          localDiffersFromBaseline,
      ),
    );
  }

  function startPendingSave() {
    const baseline = baselineRef.current;
    const pending = pendingRef.current;
    if (
      !baseline ||
      !pending ||
      activeRef.current ||
      recoveryRef.current ||
      conflictPausedRef.current
    ) {
      syncBeforeUnload();
      return;
    }
    if (pending.fingerprint === baseline.fingerprint) {
      pendingRef.current = undefined;
      setSaveStatus({
        contentHash: baseline.contentHash,
        kind: 'saved',
        revision: baseline.revision,
      });
      syncBeforeUnload();
      return;
    }
    pendingRef.current = undefined;
    const requestKey = keys.beginNewIntent('save-draft');
    const active: ActiveSave = {
      ...pending,
      discardResult: false,
      expectedRevision: baseline.revision,
      inFlight: false,
      projectId: baseline.projectId,
      request: {
        expectedRevision: baseline.revision,
        idempotencyKey: requestKey,
        schemaVersion: 'timeline-1',
        timeline: pending.timeline,
      },
      runId: runIdRef.current,
      unknownResult: false,
    };
    activeRef.current = active;
    setSaveStatus({
      basedOnRevision: active.expectedRevision,
      kind: 'saving',
      requestKey,
    });
    syncBeforeUnload();
    sendActiveSave(active);
  }

  function schedulePendingSave(immediate: boolean) {
    clearScheduledSave();
    if (!pendingRef.current || activeRef.current || recoveryRef.current) {
      syncBeforeUnload();
      return;
    }
    if (immediate) {
      startPendingSave();
      return;
    }
    timerRef.current = window.setTimeout(() => {
      timerRef.current = undefined;
      startPendingSave();
    }, debounceMs);
    syncBeforeUnload();
  }

  function queueCurrentTimeline(current: DraftSnapshot) {
    const baseline = baselineRef.current;
    if (!baseline || conflictPausedRef.current || recoveryRef.current) {
      syncBeforeUnload();
      return;
    }

    if (current.fingerprint !== baseline.fingerprint) {
      setLastSaved(undefined);
    }

    const active = activeRef.current;
    if (active) {
      pendingRef.current =
        current.fingerprint === active.fingerprint ? undefined : current;
      setSaveStatus({
        basedOnRevision: active.expectedRevision,
        kind: 'saving',
        requestKey: active.request.idempotencyKey,
      });
      syncBeforeUnload();
      return;
    }

    if (current.fingerprint === baseline.fingerprint) {
      pendingRef.current = undefined;
      clearScheduledSave();
      setSaveStatus({
        contentHash: baseline.contentHash,
        kind: 'saved',
        revision: baseline.revision,
      });
      syncBeforeUnload();
      return;
    }

    pendingRef.current = current;
    setSaveStatus({ kind: 'dirty', basedOnRevision: baseline.revision });
    schedulePendingSave(false);
  }

  function completeSave(result: SaveTimelineDraftResult, active: ActiveSave) {
    if (!mountedRef.current || activeRef.current !== active) return;
    active.inFlight = false;
    if (active.discardResult || active.runId !== runIdRef.current) {
      releaseDiscardedSave(active);
      return;
    }

    if (result.superseded) {
      activeRef.current = undefined;
      keys.forget('save-draft');
      const recovery: SupersededRecovery = {
        projectId: active.projectId,
        requestKey: active.request.idempotencyKey,
        runId: active.runId,
      };
      recoveryRef.current = recovery;
      setSaveStatus({
        basedOnRevision: active.expectedRevision,
        kind: 'saving',
        requestKey: active.request.idempotencyKey,
      });
      syncBeforeUnload();
      refreshSupersededBaseline(recovery);
      return;
    }

    activeRef.current = undefined;
    keys.forget('save-draft');
    const savedFingerprint = fingerprintTimeline(result.timeline);
    baselineRef.current = {
      contentHash: result.contentHash,
      fingerprint: savedFingerprint,
      projectId: active.projectId,
      revision: result.revision,
      timeline: result.timeline,
    };
    const current = currentRef.current;
    if (
      current.projectId === active.projectId &&
      current.timeline &&
      current.fingerprint === savedFingerprint
    ) {
      pendingRef.current = undefined;
      setLastSaved(result);
      setSaveStatus({
        contentHash: result.contentHash,
        kind: 'saved',
        revision: result.revision,
      });
      syncBeforeUnload();
      return;
    }

    if (current.timeline && current.fingerprint) {
      pendingRef.current = {
        fingerprint: current.fingerprint,
        timeline: current.timeline,
      };
      setSaveStatus({ kind: 'dirty', basedOnRevision: result.revision });
      syncBeforeUnload();
      schedulePendingSave(true);
      return;
    }
    syncBeforeUnload();
  }

  function refreshSupersededBaseline(recovery: SupersededRecovery) {
    void apiRef.current
      .getDraft(recovery.projectId)
      .then((server) => {
        if (!isCurrentRun(recovery.runId) || recoveryRef.current !== recovery) {
          return;
        }
        recoveryRef.current = undefined;
        const serverFingerprint = fingerprintTimeline(server.timeline);
        baselineRef.current = {
          contentHash: server.contentHash,
          fingerprint: serverFingerprint,
          projectId: recovery.projectId,
          revision: server.revision,
          timeline: server.timeline,
        };
        const current = currentRef.current;
        if (
          current.projectId === recovery.projectId &&
          current.timeline &&
          current.fingerprint === serverFingerprint
        ) {
          pendingRef.current = undefined;
          setLastSaved(server);
          setSaveStatus({
            contentHash: server.contentHash,
            kind: 'saved',
            revision: server.revision,
          });
          syncBeforeUnload();
          return;
        }
        if (current.timeline && current.fingerprint) {
          pendingRef.current = {
            fingerprint: current.fingerprint,
            timeline: current.timeline,
          };
          setLastSaved(undefined);
          setSaveStatus({ kind: 'dirty', basedOnRevision: server.revision });
          syncBeforeUnload();
          schedulePendingSave(true);
        }
      })
      .catch((error: unknown) => {
        if (!isCurrentRun(recovery.runId) || recoveryRef.current !== recovery) {
          return;
        }
        if (isAbortError(error)) return;
        setSaveStatus({
          kind: 'failed',
          requestKey: recovery.requestKey,
          retryable: true,
        });
        syncBeforeUnload();
      });
  }

  function failSave(error: unknown, active: ActiveSave) {
    if (!mountedRef.current || activeRef.current !== active) return;
    active.inFlight = false;
    if (active.discardResult || active.runId !== runIdRef.current) {
      releaseDiscardedSave(active);
      return;
    }
    if (isAbortError(error)) return;
    if (error instanceof ApiError && error.code === 46603) {
      activeRef.current = undefined;
      keys.forget('save-draft');
      conflictPausedRef.current = true;
      syncBeforeUnload();
      void apiRef.current
        .getDraft(active.projectId)
        .then((server) => {
          if (!isCurrentRun(active.runId) || !conflictPausedRef.current) return;
          setSaveStatus({
            baseRevision: active.expectedRevision,
            kind: 'conflict',
            serverRevision: server.revision,
            snapshot: active.timeline,
          });
          syncBeforeUnload();
        })
        .catch((loadError: unknown) => {
          if (!isCurrentRun(active.runId) || isAbortError(loadError)) return;
          setSaveStatus({
            kind: 'failed',
            requestKey: active.request.idempotencyKey,
            retryable: true,
          });
          syncBeforeUnload();
        });
      return;
    }

    if (isUnknownSaveResult(error)) {
      active.unknownResult = true;
      setSaveStatus({
        kind: 'failed',
        requestKey: active.request.idempotencyKey,
        retryable: true,
      });
      syncBeforeUnload();
      return;
    }

    activeRef.current = undefined;
    keys.forget('save-draft');
    setSaveStatus({
      kind: 'failed',
      requestKey: active.request.idempotencyKey,
      retryable: true,
    });
    syncBeforeUnload();
  }

  function releaseDiscardedSave(active: ActiveSave) {
    if (activeRef.current !== active) return;
    activeRef.current = undefined;
    const baseline = baselineRef.current;
    const current = currentRef.current;
    if (
      baseline &&
      current.projectId === baseline.projectId &&
      current.timeline &&
      current.fingerprint &&
      current.fingerprint !== baseline.fingerprint
    ) {
      pendingRef.current = {
        fingerprint: current.fingerprint,
        timeline: current.timeline,
      };
      setLastSaved(undefined);
      setSaveStatus({ kind: 'dirty', basedOnRevision: baseline.revision });
      syncBeforeUnload();
      schedulePendingSave(true);
      return;
    }
    syncBeforeUnload();
  }

  function sendActiveSave(active: ActiveSave) {
    if (
      active.inFlight ||
      !isCurrentRun(active.runId) ||
      activeRef.current !== active
    ) {
      return;
    }
    active.inFlight = true;
    void apiRef.current
      .saveDraft(active.projectId, active.request)
      .then((result) => completeSave(result, active))
      .catch((error: unknown) => failSave(error, active));
  }

  useEffect(() => {
    if (!hasUnconfirmedChanges) return undefined;
    const warnBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
      return '';
    };
    window.addEventListener('beforeunload', warnBeforeUnload);
    return () => window.removeEventListener('beforeunload', warnBeforeUnload);
  }, [hasUnconfirmedChanges]);

  useEffect(() => {
    if (!projectId || !timeline || !revision) {
      if (baselineRef.current) {
        runIdRef.current += 1;
        if (activeRef.current) activeRef.current.discardResult = true;
        baselineRef.current = undefined;
        conflictPausedRef.current = false;
        pendingRef.current = undefined;
        recoveryRef.current = undefined;
        clearScheduledSave();
        setLastSaved(undefined);
        setSaveStatus({ contentHash: '', kind: 'saved', revision: '' });
        syncBeforeUnload();
      }
      return;
    }
    const current: DraftSnapshot = {
      fingerprint: fingerprintTimeline(timeline),
      timeline,
    };
    const baseline = baselineRef.current;
    if (!baseline || baseline.projectId !== projectId) {
      runIdRef.current += 1;
      if (activeRef.current) activeRef.current.discardResult = true;
      baselineRef.current = {
        contentHash: '',
        ...current,
        projectId,
        revision,
      };
      conflictPausedRef.current = false;
      pendingRef.current = undefined;
      recoveryRef.current = undefined;
      clearScheduledSave();
      setLastSaved(undefined);
      setSaveStatus({ contentHash: '', kind: 'saved', revision });
      syncBeforeUnload();
      return;
    }
    queueCurrentTimeline(current);
  }, [projectId, revision, timeline]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      runIdRef.current += 1;
      if (activeRef.current) activeRef.current.discardResult = true;
      pendingRef.current = undefined;
      recoveryRef.current = undefined;
      clearScheduledSave();
    };
  }, []);

  return {
    errorMessage:
      saveStatus.kind === 'failed'
        ? getErrorMessage(saveStatus, '草稿保存失败')
        : undefined,
    lastSaved,
    rebaseline: (
      nextTimeline: TimelineDocument,
      nextRevision: string,
      contentHash: string,
    ) => {
      runIdRef.current += 1;
      if (activeRef.current) activeRef.current.discardResult = true;
      clearScheduledSave();
      conflictPausedRef.current = false;
      pendingRef.current = undefined;
      recoveryRef.current = undefined;
      keys.forget('save-draft');
      const fingerprint = fingerprintTimeline(nextTimeline);
      const resolvedProjectId = projectId ?? baselineRef.current?.projectId;
      if (resolvedProjectId) {
        baselineRef.current = {
          contentHash,
          fingerprint,
          projectId: resolvedProjectId,
          revision: nextRevision,
          timeline: nextTimeline,
        };
        currentRef.current = {
          fingerprint,
          projectId: resolvedProjectId,
          revision: nextRevision,
          timeline: nextTimeline,
        };
      }
      setLastSaved(undefined);
      setSaveStatus({ contentHash, kind: 'saved', revision: nextRevision });
      syncBeforeUnload();
    },
    retry: () => {
      if (saveStatus.kind !== 'failed') return;
      const recovery = recoveryRef.current;
      if (recovery) {
        refreshSupersededBaseline(recovery);
        return;
      }
      const active = activeRef.current;
      if (active?.unknownResult) {
        active.unknownResult = false;
        setSaveStatus({
          basedOnRevision: active.expectedRevision,
          kind: 'saving',
          requestKey: active.request.idempotencyKey,
        });
        sendActiveSave(active);
        return;
      }
      const current = currentRef.current;
      if (current.timeline && current.fingerprint && baselineRef.current) {
        pendingRef.current = {
          fingerprint: current.fingerprint,
          timeline: current.timeline,
        };
        setSaveStatus({
          kind: 'dirty',
          basedOnRevision: baselineRef.current.revision,
        });
        schedulePendingSave(true);
      }
    },
    saveStatus,
  };
}
