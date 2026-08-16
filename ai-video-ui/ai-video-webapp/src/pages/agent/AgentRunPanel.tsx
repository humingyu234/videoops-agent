import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DownloadOutlined,
  PauseCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Popconfirm,
  Space,
  Spin,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import type {
  AgentRunDetail,
  AgentRunStatus,
} from '@/services/ai-video/agent/types';
import styles from './index.module.css';

const { Paragraph, Text, Title } = Typography;

const STATUS_META: Record<
  AgentRunStatus,
  { color: string; label: string; description: string }
> = {
  queued: {
    color: 'blue',
    label: '准备执行',
    description: '已保存执行合同，正在进入第一步。',
  },
  running: {
    color: 'processing',
    label: '执行中',
    description: 'Agent 正在调用受限工具。',
  },
  waiting_input: {
    color: 'gold',
    label: '等待补充',
    description: '缺少已确认输入，自动执行已暂停。',
  },
  waiting_external_task: {
    color: 'processing',
    label: '等待任务',
    description: '外部任务已提交，正在查询同一个任务。',
  },
  waiting_approval: {
    color: 'orange',
    label: '等待批准',
    description: '已停在人工门禁，批准前不会继续提交。',
  },
  completed: {
    color: 'success',
    label: '已完成',
    description: '成品已通过服务端归属与质量边界。',
  },
  failed: {
    color: 'error',
    label: '需人工处理',
    description: '执行已安全停止，可根据稳定错误事实处理。',
  },
  cancelled: {
    color: 'default',
    label: '已取消',
    description: '执行已停止，不会继续调用工具。',
  },
};

type AgentRunPanelProps = {
  busyAction?: string;
  detail?: AgentRunDetail;
  isCancellable: boolean;
  outputError?: string;
  outputLoading: boolean;
  outputUrl?: string;
  pollingNotice?: string;
  pollingPaused: boolean;
  onApproval: (approved: boolean) => void;
  onCancel: () => void;
  onDownload: () => void;
  onRefresh: (runId: string) => void;
  onResumePolling: () => void;
};

function formatTimestamp(value: string): string {
  const timestamp = new Date(value);
  return Number.isNaN(timestamp.getTime())
    ? value
    : timestamp.toLocaleString('zh-CN', { hour12: false });
}

const AgentRunPanel = ({
  busyAction,
  detail,
  isCancellable,
  outputError,
  outputLoading,
  outputUrl,
  pollingNotice,
  pollingPaused,
  onApproval,
  onCancel,
  onDownload,
  onRefresh,
  onResumePolling,
}: AgentRunPanelProps) => {
  if (!detail) {
    return (
      <Card className={styles.emptyRun} variant="borderless">
        <PauseCircleOutlined />
        <Title level={3}>等待一份已确认输入</Title>
        <Paragraph>
          创建后，这里会显示受限计划、人工门禁、任务状态和持久 Trace。
        </Paragraph>
      </Card>
    );
  }

  const status = STATUS_META[detail.run.status];
  const actionMessage = detail.action?.safeMessage;
  const persistentMessage = detail.run.safeMessage;

  return (
    <>
      <Card className={styles.statusCard} variant="borderless">
        <div className={styles.statusHeader}>
          <div>
            <Text className={styles.sectionKicker}>
              RUN #{detail.run.runId}
            </Text>
            <Title level={2}>{status.label}</Title>
            <Paragraph>{status.description}</Paragraph>
          </div>
          <Tag color={status.color}>{detail.run.status}</Tag>
        </div>
        <div className={styles.runFacts}>
          <span>
            <b>{detail.plan.steps.length}</b> 白名单步骤
          </span>
          <span>
            <b>{detail.plan.requiredProviderSubmissions}</b> 次 Provider 上限
          </span>
          <span>
            <b>{detail.run.qualityRepairCount}</b> 次局部返工
          </span>
        </div>
        <Space wrap>
          <Button
            icon={<ReloadOutlined />}
            loading={busyAction === 'read'}
            onClick={() => onRefresh(detail.run.runId)}
          >
            读取最新事实
          </Button>
          {isCancellable && (
            <Popconfirm
              cancelText="保留执行"
              description="取消后服务端会停止继续推进，不会自动重开。"
              okButtonProps={{ danger: true }}
              okText="确认取消"
              onConfirm={onCancel}
              title="取消当前 AgentRun？"
            >
              <Button danger loading={busyAction === 'cancel'}>
                取消执行
              </Button>
            </Popconfirm>
          )}
        </Space>
      </Card>

      {detail.pendingApproval && (
        <Card className={styles.approvalCard} variant="borderless">
          <Text className={styles.sectionKicker}>HUMAN GATE</Text>
          <Title level={3}>等待{detail.pendingApproval.type}批准</Title>
          <Paragraph>{detail.pendingApproval.requestSummary}</Paragraph>
          <Space wrap>
            <Button
              loading={busyAction === 'approve'}
              onClick={() => onApproval(true)}
              type="primary"
            >
              批准并继续
            </Button>
            <Popconfirm
              cancelText="返回"
              description="拒绝后本次执行将安全停止。"
              okButtonProps={{ danger: true }}
              okText="确认拒绝"
              onConfirm={() => onApproval(false)}
              title="拒绝本次执行？"
            >
              <Button danger loading={busyAction === 'reject'}>
                拒绝并停止
              </Button>
            </Popconfirm>
          </Space>
        </Card>
      )}

      {(actionMessage || persistentMessage) && (
        <Alert
          description={actionMessage ?? persistentMessage}
          showIcon
          title={detail.action?.errorCode ?? detail.run.errorCode ?? '执行状态'}
          type={detail.run.status === 'failed' ? 'error' : 'warning'}
        />
      )}
      {(detail.action?.missingFields.length ?? 0) > 0 && (
        <Alert
          description={detail.action?.missingFields.join('、')}
          showIcon
          title="需要补充确认输入"
          type="warning"
        />
      )}
      {pollingNotice && (
        <Alert
          action={
            pollingPaused ? (
              <Button size="small" onClick={onResumePolling}>
                恢复查询
              </Button>
            ) : undefined
          }
          showIcon
          title={pollingNotice}
          type="info"
        />
      )}

      <Card className={styles.planCard} variant="borderless">
        <div className={styles.sectionHeading}>
          <div>
            <Text className={styles.sectionKicker}>02 · BOUNDED PLAN</Text>
            <Title level={3}>受限执行计划</Title>
          </div>
          <Tag color={detail.plan.executable ? 'green' : 'gold'}>
            {detail.plan.executable ? '输入完整' : '等待输入'}
          </Tag>
        </div>
        <ol className={styles.planList}>
          {detail.plan.steps.map((step) => (
            <li key={`${step.sequence}-${step.toolName}`}>
              <span className={styles.stepNumber}>{step.sequence}</span>
              <div>
                <Text strong>{step.stepType}</Text>
                <Text className={styles.toolName} code>
                  {step.toolName}
                </Text>
                <Text type="secondary">{step.reason}</Text>
              </div>
              <Tag>{step.disposition}</Tag>
            </li>
          ))}
        </ol>
      </Card>

      <Card className={styles.traceCard} variant="borderless">
        <div className={styles.sectionHeading}>
          <div>
            <Text className={styles.sectionKicker}>03 · DURABLE TRACE</Text>
            <Title level={3}>持久事实</Title>
          </div>
          <Tag icon={<ClockCircleOutlined />}>可回放</Tag>
        </div>
        <Timeline
          items={detail.trace.items.map((item) => ({
            color:
              item.status === 'failed'
                ? 'red'
                : item.status === 'completed' || item.status === 'success'
                  ? 'green'
                  : 'blue',
            content: (
              <div className={styles.traceItem}>
                <Space wrap>
                  <Text strong>{item.label}</Text>
                  <Tag>{item.status}</Tag>
                </Space>
                <Text type="secondary">
                  {item.subjectType} #{item.subjectId} ·{' '}
                  {formatTimestamp(item.occurredAt)}
                </Text>
                {(item.errorCode || item.safeMessage) && (
                  <Text type="danger">
                    {[item.errorCode, item.safeMessage]
                      .filter(Boolean)
                      .join(' · ')}
                  </Text>
                )}
              </div>
            ),
          }))}
        />
      </Card>

      {detail.finalOutputAssetId && (
        <Card className={styles.outputCard} variant="borderless">
          <div className={styles.sectionHeading}>
            <div>
              <Text className={styles.sectionKicker}>04 · RESULT</Text>
              <Title level={3}>当前执行的最终成品</Title>
            </div>
            <Tag icon={<CheckCircleOutlined />} color="success">
              Asset #{detail.finalOutputAssetId}
            </Tag>
          </div>
          {outputLoading ? (
            <Spin description="读取授权成品" />
          ) : outputError ? (
            <Alert title={outputError} showIcon type="error" />
          ) : outputUrl ? (
            <>
              {/* biome-ignore lint/a11y/useMediaCaption: captions are visibly burned into the server-owned MP4. */}
              <video
                aria-label="Agent 最终成品"
                className={styles.outputVideo}
                controls
                src={outputUrl}
              />
              <Button
                block
                icon={<DownloadOutlined />}
                onClick={onDownload}
                size="large"
                type="primary"
              >
                下载最终 MP4
              </Button>
            </>
          ) : null}
        </Card>
      )}
    </>
  );
};

export default AgentRunPanel;
