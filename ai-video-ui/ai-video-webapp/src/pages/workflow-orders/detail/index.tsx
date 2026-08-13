import { ReloadOutlined, StopOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { history, useModel, useParams } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Card,
  Empty,
  Image,
  Progress,
  Result,
  Skeleton,
  Space,
  Tag,
  Typography,
} from 'antd';
import CreatorWorkspaceShell from '@/components/CreatorWorkspaceShell';
import { workflowOrdersApi } from '@/services/ai-video/workflow-orders/api';
import { workflowOrderQueryKeys } from '@/services/ai-video/workflow-orders/queryKeys';
import type {
  WorkflowOrderAsset,
  WorkflowOrderStatus,
  WorkflowTaskStage,
} from '@/services/ai-video/workflow-orders/types';
import styles from './index.module.css';

const terminal = new Set<WorkflowOrderStatus>([
  'success',
  'failed',
  'cancelled',
]);
const statusText: Record<WorkflowOrderStatus, string> = {
  pending: '待提交',
  queued: '排队中',
  running: '制作中',
  success: '已完成',
  failed: '失败',
  cancelled: '已取消',
};
const statusColor: Record<WorkflowOrderStatus, string> = {
  pending: 'default',
  queued: 'processing',
  running: 'processing',
  success: 'success',
  failed: 'error',
  cancelled: 'default',
};
const stageText: Record<WorkflowTaskStage, string> = {
  waiting_for_dispatch: '等待调度',
  preparing_inputs: '正在准备输入素材',
  submitting_to_provider: '正在提交制作任务',
  confirming_provider_acceptance: '正在确认任务受理结果',
  provider_processing: '工作流处理中',
  processing_results: '正在整理制作结果',
  completed: '制作完成',
  failed: '制作失败',
  cancelled: '已取消',
};

function ResultAsset({
  asset,
  scope,
}: {
  asset: WorkflowOrderAsset;
  scope: { userId: string; workspaceId: string };
}) {
  const access = useQuery({
    enabled: asset.status === 'ready',
    queryKey: [
      'app-private',
      scope.userId,
      scope.workspaceId,
      'asset-access',
      asset.assetId,
    ],
    queryFn: () => workflowOrdersApi.getAssetAccessUrl(asset.assetId),
    staleTime: 60_000,
  });

  return (
    <Card title={asset.primary ? `${asset.label} · 主结果` : asset.label}>
      {access.isLoading ? (
        <Skeleton active paragraph={{ rows: 3 }} />
      ) : access.isError || !access.data ? (
        <Alert message="结果地址获取失败" showIcon type="warning" />
      ) : asset.mediaType === 'image' ? (
        <Image alt={asset.label} src={access.data.url} />
      ) : asset.mediaType === 'video' ? (
        // biome-ignore lint/a11y/useMediaCaption: The workflow result contract does not expose timed-text resources.
        <video className={styles.media} controls src={access.data.url} />
      ) : asset.mediaType === 'audio' ? (
        // biome-ignore lint/a11y/useMediaCaption: The workflow result contract does not expose timed-text resources.
        <audio className={styles.media} controls src={access.data.url} />
      ) : (
        <Button href={access.data.url} target="_blank">
          下载文件
        </Button>
      )}
    </Card>
  );
}

export default function WorkflowOrderDetailPage() {
  const { orderId = '' } = useParams<{ orderId: string }>();
  const { initialState } = useModel('@@initialState');
  const scope = {
    userId: initialState?.currentUser?.id ?? '',
    workspaceId: initialState?.currentUser?.workspace?.id ?? '',
  };
  const key = workflowOrderQueryKeys.detail(
    scope.userId,
    scope.workspaceId,
    orderId,
  );
  const queryClient = useQueryClient();
  const { modal } = App.useApp();
  const orderQuery = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => workflowOrdersApi.getDetail(orderId, signal),
    enabled: Boolean(orderId),
    refetchInterval: (query) =>
      query.state.data && !terminal.has(query.state.data.task.status)
        ? 3000
        : false,
  });
  const cancel = useMutation({
    mutationFn: () => workflowOrdersApi.cancel(orderId),
    onSuccess: (data) => queryClient.setQueryData(key, data),
  });

  return (
    <CreatorWorkspaceShell
      activeKey="tasks"
      description="模板运行状态与生成结果"
      title="模板结果"
    >
      <div className={styles.page}>
        {orderQuery.isLoading ? (
          <Skeleton active />
        ) : orderQuery.isError || !orderQuery.data ? (
          <Alert
            action={
              <Button onClick={() => void orderQuery.refetch()}>重试</Button>
            }
            message="模板结果加载失败"
            showIcon
            type="error"
          />
        ) : (() => {
            const order = orderQuery.data;
            const status = order.task.status;
            return (
              <>
                <Card className={styles.hero}>
                  <Space direction="vertical" size="middle">
                    <Space>
                      <Typography.Title className={styles.title} level={3}>
                        {order.template.title}
                      </Typography.Title>
                      <Tag color={statusColor[status]}>{statusText[status]}</Tag>
                    </Space>
                    <Typography.Text type="secondary">
                      订单号 {order.orderNo} · {stageText[order.task.stage]}
                    </Typography.Text>
                    {order.task.progressPercent !== undefined && (
                      <Progress
                        percent={order.task.progressPercent}
                        status={status === 'failed' ? 'exception' : undefined}
                      />
                    )}
                    <Space>
                      {order.canCancel && (
                        <Button
                          danger
                          icon={<StopOutlined />}
                          loading={cancel.isPending}
                          onClick={() =>
                            modal.confirm({
                              title: '确认取消这次制作？',
                              content: '取消后不能恢复当前任务。',
                              okText: '确认取消',
                              okButtonProps: { danger: true },
                              cancelText: '继续制作',
                              onOk: () => cancel.mutateAsync(),
                            })
                          }
                        >
                          取消任务
                        </Button>
                      )}
                      {order.canRemake && (
                        <Button
                          icon={<ReloadOutlined />}
                          onClick={() =>
                            history.push(
                              `/discover/templates/${encodeURIComponent(
                                order.template.templateId,
                              )}`,
                            )
                          }
                          type="primary"
                        >
                          再次制作
                        </Button>
                      )}
                    </Space>
                  </Space>
                </Card>
                {status === 'failed' && (
                  <Result
                    status="error"
                    subTitle={order.task.failureMessage ?? '请稍后重试'}
                    title="制作失败"
                  />
                )}
                <section>
                  <Typography.Title level={4}>制作结果</Typography.Title>
                  <div className={styles.results}>
                    {order.outputs.length === 0 ? (
                      <div className={styles.emptyResults}>
                        <Empty
                          description={
                            terminal.has(status)
                              ? '暂无结果'
                              : '结果生成后将在这里展示'
                          }
                        />
                      </div>
                    ) : (
                      order.outputs.map((asset) => (
                        <ResultAsset
                          asset={asset}
                          key={asset.assetId}
                          scope={scope}
                        />
                      ))
                    )}
                  </div>
                </section>
              </>
            );
          })()}
      </div>
    </CreatorWorkspaceShell>
  );
}
