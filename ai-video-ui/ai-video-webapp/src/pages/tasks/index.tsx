import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useModel } from '@umijs/max';
import {
  Alert,
  Button,
  Card,
  Empty,
  List,
  Modal,
  Pagination,
  Select,
  Skeleton,
  Space,
  Tag,
  Typography,
} from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import CreatorWorkspaceShell from '@/components/CreatorWorkspaceShell';
import { tasksApi } from '@/services/ai-video/tasks/api';
import {
  getTaskPollingDelay,
  shouldStopTaskPolling,
} from '@/services/ai-video/tasks/polling';
import { taskQueryKeys } from '@/services/ai-video/tasks/queryKeys';
import type { TaskListItem, TaskStatus } from '@/services/ai-video/tasks/types';
import styles from './index.module.css';

const statusLabels: Record<TaskStatus, string> = {
  cancelled: '已取消',
  failed: '失败',
  pending: '待提交',
  queued: '排队中',
  running: '处理中',
  success: '已完成',
};

const statusColors: Record<TaskStatus, string> = {
  cancelled: 'default',
  failed: 'error',
  pending: 'default',
  queued: 'processing',
  running: 'processing',
  success: 'success',
};

const taskLabels: Record<string, string> = {
  timeline_fancy_text_suggest: 'AI 花字建议',
  timeline_image_prompt_generate: 'AI 生图提示词',
  timeline_render: '时间轴合成',
  timeline_subtitle_align: 'AI 字幕对齐',
};

const statusOptions = [
  { label: '全部状态', value: 'all' },
  ...Object.entries(statusLabels).map(([value, label]) => ({ label, value })),
];

function taskLabel(task: TaskListItem): string {
  return taskLabels[task.taskType] ?? task.taskType;
}

function createIdempotencyKey(): string {
  const key = globalThis.crypto?.randomUUID?.();
  if (!key) throw new Error('当前环境无法创建幂等键');
  return key;
}

type PollingEnvironment = {
  hiddenForMs: number;
  online: boolean;
  visible: boolean;
};

function readPollingEnvironment(
  hiddenSince: number | undefined,
): PollingEnvironment {
  if (typeof document === 'undefined' || typeof navigator === 'undefined') {
    return { hiddenForMs: 0, online: true, visible: true };
  }
  const visible = document.visibilityState !== 'hidden';
  return {
    hiddenForMs:
      visible || hiddenSince === undefined ? 0 : Date.now() - hiddenSince,
    online: navigator.onLine,
    visible,
  };
}

function useTaskListPollingEnvironment(): PollingEnvironment {
  const hiddenSinceRef = useRef<number | undefined>(
    typeof document !== 'undefined' && document.visibilityState === 'hidden'
      ? Date.now()
      : undefined,
  );
  const [environment, setEnvironment] = useState(() =>
    readPollingEnvironment(hiddenSinceRef.current),
  );

  useEffect(() => {
    let longHiddenTimer: number | undefined;
    const clearLongHiddenTimer = () => {
      if (longHiddenTimer !== undefined) window.clearTimeout(longHiddenTimer);
      longHiddenTimer = undefined;
    };
    const publish = () =>
      setEnvironment(readPollingEnvironment(hiddenSinceRef.current));
    const scheduleLongHiddenStop = () => {
      clearLongHiddenTimer();
      if (document.visibilityState !== 'hidden') return;
      const hiddenSince = hiddenSinceRef.current ?? Date.now();
      hiddenSinceRef.current = hiddenSince;
      const remaining = Math.max(0, 5 * 60_000 - (Date.now() - hiddenSince));
      longHiddenTimer = window.setTimeout(publish, remaining);
    };
    const updateVisibility = () => {
      if (document.visibilityState === 'hidden') {
        hiddenSinceRef.current ??= Date.now();
        scheduleLongHiddenStop();
      } else {
        hiddenSinceRef.current = undefined;
        clearLongHiddenTimer();
      }
      publish();
    };

    document.addEventListener('visibilitychange', updateVisibility);
    window.addEventListener('online', publish);
    window.addEventListener('offline', publish);
    scheduleLongHiddenStop();
    publish();
    return () => {
      clearLongHiddenTimer();
      document.removeEventListener('visibilitychange', updateVisibility);
      window.removeEventListener('online', publish);
      window.removeEventListener('offline', publish);
    };
  }, []);

  return environment;
}

export default function TasksPage() {
  const { initialState } = useModel('@@initialState');
  const queryClient = useQueryClient();
  const userId = initialState?.currentUser?.id ?? '';
  const workspaceId = initialState?.currentUser?.workspace?.id ?? '';
  const [pageNum, setPageNum] = useState(1);
  const [status, setStatus] = useState<TaskStatus>();
  const [modal, contextHolder] = Modal.useModal();
  const taskActionInFlightRef = useRef(false);
  const pollingEnvironment = useTaskListPollingEnvironment();
  const params = useMemo(
    () => ({ pageNum, pageSize: 10, status }),
    [pageNum, status],
  );
  const queryKey = taskQueryKeys.list(userId, workspaceId, params);
  const query = useQuery({
    enabled: Boolean(userId),
    queryFn: ({ signal }) => tasksApi.list(params, signal),
    queryKey,
    refetchInterval: (scheduledQuery) => {
      if (
        scheduledQuery.state.error &&
        shouldStopTaskPolling(scheduledQuery.state.error)
      ) {
        return false;
      }
      return getTaskPollingDelay({
        failureCount: scheduledQuery.state.fetchFailureCount,
        scope: 'list',
        ...pollingEnvironment,
      });
    },
    refetchIntervalInBackground: true,
    refetchOnReconnect: false,
    refetchOnWindowFocus: false,
    retry: false,
  });
  const action = useMutation({
    mutationFn: ({
      kind,
      taskId,
    }: {
      kind: 'cancel' | 'retry';
      taskId: string;
    }) =>
      kind === 'cancel'
        ? tasksApi.cancel(taskId, { idempotencyKey: createIdempotencyKey() })
        : tasksApi.retry(taskId, { idempotencyKey: createIdempotencyKey() }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey }),
  });
  const executeTaskAction = useCallback(
    async (kind: 'cancel' | 'retry', taskId: string) => {
      if (taskActionInFlightRef.current) return;
      taskActionInFlightRef.current = true;
      try {
        await action.mutateAsync({ kind, taskId });
      } finally {
        taskActionInFlightRef.current = false;
      }
    },
    [action.mutateAsync],
  );
  const confirmTaskAction = useCallback(
    (kind: 'cancel' | 'retry', taskId: string) => {
      if (action.isPending || taskActionInFlightRef.current) return;
      const isCancellation = kind === 'cancel';
      void modal.confirm({
        cancelText: '返回',
        content: isCancellation
          ? `任务编号：${taskId}。取消后任务将停止后续处理。`
          : `任务编号：${taskId}。重试可能重新执行任务并消耗额度。`,
        okText: isCancellation ? '确认取消' : '确认重试',
        okType: isCancellation ? 'danger' : 'primary',
        title: `确认${isCancellation ? '取消' : '重试'}任务 ${taskId}？`,
        onOk: () => executeTaskAction(kind, taskId),
      });
    },
    [action.isPending, executeTaskAction, modal],
  );

  return (
    <CreatorWorkspaceShell
      activeKey="tasks"
      description="追踪当前账号的统一生成任务"
      title="任务中心"
    >
      {contextHolder}
      <div className={styles.page}>
        <Card className={styles.filters} size="small">
          <Space>
            <Typography.Text strong>任务状态</Typography.Text>
            <Select
              options={statusOptions}
              value={status ?? 'all'}
              onChange={(value) => {
                setStatus(value === 'all' ? undefined : (value as TaskStatus));
                setPageNum(1);
              }}
            />
          </Space>
        </Card>
        {query.isLoading ? (
          <Skeleton active />
        ) : query.isError ? (
          <Alert
            action={<Button onClick={() => void query.refetch()}>重试</Button>}
            message="任务加载失败"
            showIcon
            type="error"
          />
        ) : !query.data?.rows.length ? (
          <Empty description="暂无符合条件的任务" />
        ) : (
          <>
            {action.isError && (
              <Alert message="任务操作失败" showIcon type="error" />
            )}
            <List
              dataSource={query.data.rows}
              rowKey="taskId"
              renderItem={(task) => (
                <List.Item
                  actions={[
                    ...(task.canCancel
                      ? [
                          <Button
                            aria-label={`取消任务 ${task.taskId}`}
                            disabled={action.isPending}
                            danger
                            key="cancel"
                            loading={action.isPending}
                            onClick={() => confirmTaskAction('cancel', task.taskId)}
                          >
                            取消
                          </Button>,
                        ]
                      : []),
                    ...(task.canRetry
                      ? [
                          <Button
                            aria-label={`重试任务 ${task.taskId}`}
                            disabled={action.isPending}
                            key="retry"
                            loading={action.isPending}
                            onClick={() => confirmTaskAction('retry', task.taskId)}
                          >
                            重试
                          </Button>,
                        ]
                      : []),
                  ]}
                >
                  <List.Item.Meta
                    description={
                      <Space orientation="vertical" size={0}>
                        <Typography.Text type="secondary">
                          {task.taskType} · {task.stage} · {task.progress}%
                        </Typography.Text>
                        <Typography.Text type="secondary">
                          {task.createdAt}
                        </Typography.Text>
                        {task.errorSummary && (
                          <Typography.Text type="danger">
                            {task.errorSummary}
                          </Typography.Text>
                        )}
                      </Space>
                    }
                    title={
                      <Space>
                        <Typography.Text>{taskLabel(task)}</Typography.Text>
                        <Tag color={statusColors[task.status]}>
                          {statusLabels[task.status]}
                        </Tag>
                      </Space>
                    }
                  />
                </List.Item>
              )}
            />
            <Pagination
              current={pageNum}
              pageSize={10}
              showSizeChanger={false}
              total={query.data.total}
              onChange={setPageNum}
            />
          </>
        )}
      </div>
    </CreatorWorkspaceShell>
  );
}
