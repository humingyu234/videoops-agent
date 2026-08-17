import { RobotOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Select,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import type { AgentApi } from '@/services/ai-video/agent/api';
import type { CreationAssetsApi } from '@/services/ai-video/creation-assets/api';
import {
  type PortraitApi,
  portraitApi,
} from '@/services/ai-video/portrait/api';
import type { Portrait } from '@/services/ai-video/portrait/types';
import { type VoiceApi, voiceApi } from '@/services/ai-video/voice/api';
import type { Voice } from '@/services/ai-video/voice/types';
import AgentRunPanel from './AgentRunPanel';
import styles from './index.module.css';
import { type AgentFormValues, useAgentRun } from './useAgentRun';

const { Paragraph, Text, Title } = Typography;

type AgentPageProps = {
  agentClient?: AgentApi;
  assetsClient?: Pick<CreationAssetsApi, 'content'>;
  portraitClient?: Pick<PortraitApi, 'list'>;
  voiceClient?: Pick<VoiceApi, 'list'>;
  pollingIntervalMs?: number;
};

const AgentPage = ({
  agentClient,
  assetsClient,
  portraitClient = portraitApi,
  voiceClient = voiceApi,
  pollingIntervalMs,
}: AgentPageProps) => {
  const [form] = Form.useForm<AgentFormValues>();
  const [portraits, setPortraits] = useState<Portrait[]>([]);
  const [voices, setVoices] = useState<Voice[]>([]);
  const [assetsLoading, setAssetsLoading] = useState(true);
  const [assetsError, setAssetsError] = useState<string>();
  const run = useAgentRun({ agentClient, assetsClient, pollingIntervalMs });
  const startAt = Form.useWatch('startAt', form) ?? 'new';

  const loadAssets = useCallback(async () => {
    setAssetsLoading(true);
    setAssetsError(undefined);
    const [portraitResult, voiceResult] = await Promise.allSettled([
      portraitClient.list({
        availabilityStatus: 'ready',
        pageNum: 1,
        pageSize: 100,
      }),
      voiceClient.list({ voiceType: 'origin', pageNum: 1, pageSize: 100 }),
    ]);
    if (portraitResult.status === 'fulfilled') {
      const readyPortraits = portraitResult.value.rows.filter(
        (portrait) => portrait.availabilityStatus === 'ready',
      );
      setPortraits(readyPortraits);
      if (readyPortraits.length === 1 && !form.getFieldValue('portraitId')) {
        form.setFieldsValue({ portraitId: readyPortraits[0]?.portraitId });
      }
    }
    if (voiceResult.status === 'fulfilled') {
      const originVoices = voiceResult.value.rows.filter(
        (voice) => voice.voiceType === 'origin',
      );
      setVoices(originVoices);
      if (
        originVoices.length === 1 &&
        !form.getFieldValue('referenceVoiceId')
      ) {
        form.setFieldsValue({ referenceVoiceId: originVoices[0]?.voiceId });
      }
    }
    if (
      portraitResult.status === 'rejected' ||
      voiceResult.status === 'rejected'
    ) {
      setAssetsError('人物或原声音加载失败，请刷新后重试。');
    }
    setAssetsLoading(false);
  }, [form, portraitClient, voiceClient]);

  useEffect(() => {
    void loadAssets();
  }, [loadAssets]);

  return (
    <div className={styles.page}>
      <header className={styles.hero}>
        <div className={styles.brand}>
          <span className={styles.brandMark}>
            <RobotOutlined />
          </span>
          <div>
            <Text className={styles.eyebrow}>VIDEOOPS AGENT</Text>
            <Title level={1}>从一句交付目标，到可追踪成品</Title>
            <Paragraph>
              只调用服务端白名单工具；任务、批准和结果都来自持久事实。
            </Paragraph>
          </div>
        </div>
        <a className={styles.studioLink} href="/studio">
          人工工作台
        </a>
      </header>

      <main className={styles.content}>
        <section className={styles.composeColumn} aria-label="创建 AgentRun">
          <Card className={styles.composeCard} variant="borderless">
            <div className={styles.sectionHeading}>
              <div>
                <Text className={styles.sectionKicker}>
                  01 · DELIVERY BRIEF
                </Text>
                <Title level={2}>确认本次口播交付</Title>
              </div>
              <Tag icon={<SafetyCertificateOutlined />} color="blue">
                服务端归属校验
              </Tag>
            </div>

            {run.actionError && (
              <Alert
                className={styles.formAlert}
                showIcon
                title={run.actionError}
                type="error"
              />
            )}
            {assetsError && (
              <Alert
                action={
                  <Button size="small" onClick={() => void loadAssets()}>
                    重试
                  </Button>
                }
                showIcon
                title={assetsError}
                type="warning"
              />
            )}

            <Spin spinning={assetsLoading} description="读取可用人物与原声音">
              <Form<AgentFormValues>
                form={form}
                initialValues={{
                  startAt: 'new',
                  projectTitle: 'VideoOps Agent 黄金链',
                  scriptText:
                    '大家好，我是由 VideoOps Agent 驱动的数字人。它能把一句视频交付目标拆成清晰步骤，选择真实人物和声音，调用数字人、字幕与渲染工具，持续跟踪任务状态，并在出现问题时保留证据、局部修复。现在你看到的内容，不是演示数据，而是一条真实、可追踪、可复现的视频生产链路。',
                }}
                layout="vertical"
                onFinish={(values) => void run.createRun(values)}
              >
                <Form.Item label="执行起点" name="startAt" required>
                  <Select
                    options={[
                      { label: '从头创建（默认）', value: 'new' },
                      { label: '复用成功声音任务', value: 'voice_job' },
                      { label: '复用成功视频任务', value: 'video_job' },
                      { label: '复用已有项目版本', value: 'project' },
                      { label: '复用成功渲染任务', value: 'render_task' },
                    ]}
                  />
                </Form.Item>
                {startAt !== 'new' && (
                  <Alert
                    className={styles.formAlert}
                    showIcon
                    title="只复用当前账号已成功的持久任务；服务端会核对类型、状态和输入摘要，不会重提已跳过的 Provider。"
                    type="info"
                  />
                )}
                {['new', 'voice_job', 'video_job'].includes(startAt) && (
                  <Form.Item
                    label="项目标题"
                    name="projectTitle"
                    rules={[
                      {
                        required: true,
                        whitespace: true,
                        message: '请输入项目标题',
                      },
                    ]}
                  >
                    <Input maxLength={128} placeholder="例如：新品发布口播" />
                  </Form.Item>
                )}
                {startAt === 'new' && (
                  <Form.Item
                    label="中文口播脚本"
                    name="scriptText"
                    rules={[
                      {
                        required: true,
                        whitespace: true,
                        message: '请输入口播脚本',
                      },
                    ]}
                  >
                    <Input.TextArea
                      maxLength={1_000}
                      placeholder="输入已确认的交付内容"
                      rows={7}
                      showCount
                    />
                  </Form.Item>
                )}
                {startAt === 'voice_job' && (
                  <Form.Item
                    label="成功声音任务 ID"
                    name="voiceJobId"
                    rules={[
                      { required: true, message: '请输入成功声音任务 ID' },
                    ]}
                  >
                    <Input inputMode="numeric" placeholder="正十进制任务 ID" />
                  </Form.Item>
                )}
                {startAt === 'video_job' && (
                  <Form.Item
                    label="成功视频任务 ID"
                    name="videoJobId"
                    rules={[
                      { required: true, message: '请输入成功视频任务 ID' },
                    ]}
                  >
                    <Input inputMode="numeric" placeholder="正十进制任务 ID" />
                  </Form.Item>
                )}
                {startAt === 'project' && (
                  <div className={styles.selectorGrid}>
                    <Form.Item
                      label="项目 ID"
                      name="projectId"
                      rules={[{ required: true, message: '请输入项目 ID' }]}
                    >
                      <Input
                        inputMode="numeric"
                        placeholder="正十进制项目 ID"
                      />
                    </Form.Item>
                    <Form.Item
                      label="草稿版本"
                      name="expectedRevision"
                      rules={[{ required: true, message: '请输入草稿版本' }]}
                    >
                      <Input inputMode="numeric" placeholder="例如：1" />
                    </Form.Item>
                  </div>
                )}
                {startAt === 'render_task' && (
                  <Form.Item
                    label="成功渲染任务 ID"
                    name="taskId"
                    rules={[
                      { required: true, message: '请输入成功渲染任务 ID' },
                    ]}
                  >
                    <Input inputMode="numeric" placeholder="正十进制任务 ID" />
                  </Form.Item>
                )}
                {['new', 'voice_job'].includes(startAt) && (
                  <div className={styles.selectorGrid}>
                    <Form.Item
                      label="Ready 人物"
                      name="portraitId"
                      rules={[{ required: true, message: '请选择人物' }]}
                    >
                      <Select
                        options={portraits.map((portrait) => ({
                          label: portrait.name,
                          value: portrait.portraitId,
                        }))}
                        placeholder="选择当前账号人物"
                      />
                    </Form.Item>
                    {startAt === 'new' && (
                      <Form.Item
                        label="Origin 原声音"
                        name="referenceVoiceId"
                        rules={[{ required: true, message: '请选择原声音' }]}
                      >
                        <Select
                          options={voices.map((voice) => ({
                            label: voice.name,
                            value: voice.voiceId,
                          }))}
                          placeholder="选择当前账号原声音"
                        />
                      </Form.Item>
                    )}
                  </div>
                )}
                <Button
                  block
                  htmlType="submit"
                  loading={run.busyAction === 'create'}
                  size="large"
                  type="primary"
                >
                  创建并开始受限执行
                </Button>
              </Form>
            </Spin>

            {!run.detail && run.lastRunId && (
              <Button
                className={styles.resumeButton}
                loading={run.busyAction === 'read'}
                onClick={() => void run.loadOwnedRun(run.lastRunId as string)}
                type="link"
              >
                恢复最近执行 #{run.lastRunId}
              </Button>
            )}
          </Card>
        </section>

        <section
          className={styles.runColumn}
          aria-label="AgentRun 状态与 Trace"
        >
          <AgentRunPanel
            busyAction={run.busyAction}
            detail={run.detail}
            isCancellable={run.isCancellable}
            outputError={run.outputError}
            outputLoading={run.outputLoading}
            outputUrl={run.outputUrl}
            pollingNotice={run.pollingNotice}
            pollingPaused={run.pollingPaused}
            onApproval={(approved) => void run.decideApproval(approved)}
            onCancel={() => void run.cancelRun()}
            onDownload={run.downloadOutput}
            onRefresh={(runId) => void run.loadOwnedRun(runId)}
            onResumePolling={run.resumePolling}
          />
        </section>
      </main>
    </div>
  );
};

export default AgentPage;
