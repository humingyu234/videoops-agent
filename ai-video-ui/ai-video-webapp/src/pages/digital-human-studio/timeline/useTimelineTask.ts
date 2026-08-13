import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { parseTimelineTaskDetailWire } from '@/services/ai-video/creation-timeline/adapter';
import type { TimelineTaskDetail } from '@/services/ai-video/creation-timeline/types';
import type { TasksApi } from '@/services/ai-video/tasks/api';
import {
  createTaskFinalizer,
  getTaskPollingDelay,
  isTaskTerminal,
  shouldStopTaskPolling,
  type TaskFinalization,
} from '@/services/ai-video/tasks/polling';
import { taskQueryKeys } from '@/services/ai-video/tasks/queryKeys';
import type { TaskDetail } from '@/services/ai-video/tasks/types';

type TimelineTaskApi = Pick<TasksApi, 'get'>;
type TimelineTaskFinalization = TaskFinalization<TimelineTaskDetail>;

export type UseTimelineTaskOptions = {
  api: TimelineTaskApi;
  enabled?: boolean;
  onFinalized?: (finalization: TimelineTaskFinalization) => void;
  taskId?: string;
  userId: string;
  workspaceId: string;
};

type PollingStatus = 'finalized' | 'paused' | 'polling' | 'stopped';

function parseTimelineTask(task: TaskDetail): TimelineTaskDetail {
  const { kind: _kind, ...wire } = task;
  return parseTimelineTaskDetailWire(wire);
}

function browserEnvironment() {
  return {
    online: typeof navigator === 'undefined' || navigator.onLine !== false,
    visible:
      typeof document === 'undefined' || document.visibilityState !== 'hidden',
  };
}

export function useTimelineTask({
  api,
  enabled = true,
  onFinalized,
  taskId,
  userId,
  workspaceId,
}: UseTimelineTaskOptions) {
  const queryClient = useQueryClient();
  const active = Boolean(enabled && taskId && userId);
  const [environment, setEnvironment] = useState(browserEnvironment);
  const [finalization, setFinalization] = useState<TimelineTaskFinalization>();
  const [pollingStatus, setPollingStatus] = useState<PollingStatus>('polling');
  const apiRef = useRef(api);
  const finalRequestRef = useRef<AbortController | undefined>(undefined);
  const finalizingRef = useRef(false);
  const hiddenSinceRef = useRef<number | undefined>(undefined);
  const mountedRef = useRef(true);
  const stoppedRef = useRef(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const finalizerRef = useRef<
    ReturnType<typeof createTaskFinalizer<TimelineTaskDetail>> | undefined
  >(undefined);

  apiRef.current = api;
  if (!finalizerRef.current) {
    finalizerRef.current = createTaskFinalizer((id, signal) =>
      apiRef.current.get(id, signal).then(parseTimelineTask),
    );
  }

  const queryKey = useMemo(
    () =>
      active && taskId
        ? taskQueryKeys.detail(userId, workspaceId, taskId)
        : ['app-private', 'pending', 'pending', 'tasks', 'detail', 'pending'],
    [active, taskId, userId, workspaceId],
  );
  const query = useQuery({
    enabled: active,
    queryFn: ({ signal }) => {
      if (!taskId) throw new Error('A task id is required to load task detail');
      return api.get(taskId, signal).then(parseTimelineTask);
    },
    queryKey,
    retry: false,
  });
  const { refetch } = query;

  const clearTimer = useCallback(() => {
    if (timerRef.current !== undefined) {
      clearTimeout(timerRef.current);
      timerRef.current = undefined;
    }
  }, []);

  const scheduleNext = useCallback(
    (failureCount: number) => {
      clearTimer();
      if (!active || stoppedRef.current || finalizingRef.current) return;
      const hiddenForMs = hiddenSinceRef.current
        ? Date.now() - hiddenSinceRef.current
        : 0;
      const delay = getTaskPollingDelay({
        failureCount,
        hiddenForMs,
        online: environment.online,
        scope: 'detail',
        visible: environment.visible,
      });
      if (delay === false) {
        if (!environment.online) {
          setPollingStatus('paused');
          return;
        }
        stoppedRef.current = true;
        setPollingStatus('stopped');
        return;
      }
      setPollingStatus('polling');
      timerRef.current = setTimeout(() => {
        timerRef.current = undefined;
        if (!stoppedRef.current && !finalizingRef.current) {
          void refetch();
        }
      }, delay);
    },
    [active, clearTimer, environment, refetch],
  );

  useEffect(() => {
    const updateEnvironment = () => {
      const next = browserEnvironment();
      if (!next.visible) {
        hiddenSinceRef.current ??= Date.now();
      } else {
        hiddenSinceRef.current = undefined;
      }
      setEnvironment(next);
    };
    document.addEventListener('visibilitychange', updateEnvironment);
    window.addEventListener('online', updateEnvironment);
    window.addEventListener('offline', updateEnvironment);
    return () => {
      document.removeEventListener('visibilitychange', updateEnvironment);
      window.removeEventListener('online', updateEnvironment);
      window.removeEventListener('offline', updateEnvironment);
    };
  }, []);

  useEffect(() => {
    const initialEnvironment = browserEnvironment();
    if (!initialEnvironment.visible) hiddenSinceRef.current ??= Date.now();
  }, []);

  useEffect(() => {
    if (!active || stoppedRef.current || finalizingRef.current) return;
    if (query.isError) {
      if (shouldStopTaskPolling(query.error)) {
        stoppedRef.current = true;
        clearTimer();
        setPollingStatus('stopped');
        void queryClient.cancelQueries({ queryKey });
        return;
      }
      scheduleNext(Math.max(1, query.failureCount));
      return;
    }
    if (!query.data || isTaskTerminal(query.data.status)) return;
    scheduleNext(0);
  }, [
    active,
    clearTimer,
    query.data,
    query.error,
    query.failureCount,
    query.isError,
    queryClient,
    queryKey,
    scheduleNext,
  ]);

  useEffect(() => {
    if (!active || !query.data || !isTaskTerminal(query.data.status)) return;
    if (finalizingRef.current) return;
    finalizingRef.current = true;
    clearTimer();
    const controller = new AbortController();
    finalRequestRef.current = controller;
    void finalizerRef.current
      ?.finalize(query.data, clearTimer, controller.signal)
      .then((result) => {
        if (!result || !mountedRef.current) return;
        if (result.kind === 'confirmed') {
          queryClient.setQueryData<TimelineTaskDetail>(queryKey, result.task);
        }
        setFinalization(result);
        setPollingStatus('finalized');
        onFinalized?.(result);
      });
  }, [active, clearTimer, onFinalized, query.data, queryClient, queryKey]);

  const lastEnvironmentRef = useRef(environment);
  useEffect(() => {
    const previous = lastEnvironmentRef.current;
    lastEnvironmentRef.current = environment;
    const resumed =
      (environment.online && !previous.online) ||
      (environment.visible && !previous.visible);
    if (resumed && active && !stoppedRef.current && !finalizingRef.current) {
      clearTimer();
      void refetch();
    }
  }, [active, clearTimer, environment, refetch]);

  useEffect(() => {
    finalizingRef.current = false;
    stoppedRef.current = false;
    setFinalization(undefined);
    setPollingStatus('polling');
    if (taskId) finalizerRef.current?.reset(taskId);
  }, [taskId]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      clearTimer();
      finalRequestRef.current?.abort();
      void queryClient.cancelQueries({ queryKey });
    };
  }, [clearTimer, queryClient, queryKey]);

  const retry = useCallback(() => {
    if (!active || !taskId) return;
    finalRequestRef.current?.abort();
    finalizerRef.current?.reset(taskId);
    finalizingRef.current = false;
    stoppedRef.current = false;
    setFinalization(undefined);
    setPollingStatus('polling');
    clearTimer();
    void refetch();
  }, [active, clearTimer, refetch, taskId]);

  return {
    error: query.error,
    finalization,
    isLoading: query.isLoading,
    pollingStatus,
    retry,
    task: query.data,
  };
}
