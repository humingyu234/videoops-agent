import { history, Outlet, useLocation, useModel } from '@umijs/max';
import { ConfigProvider, Modal, message } from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import type { AppAuthState } from '@/services/ai-video/auth/authState';
import { getErrorMessage } from '@/services/ai-video/core/errors';
import { getRuntimeRuoYiAdapter } from '@/services/ai-video/core/runtimeRuoYiAdapter';
import { createCreationTimelineApi } from '@/services/ai-video/creation-timeline/api';
import { createIdempotencyKeyStore } from '@/services/ai-video/creation-timeline/idempotency';
import type { TimelineTaskDetail } from '@/services/ai-video/creation-timeline/types';
import { isSucceededDigitalHumanJob } from '@/services/ai-video/digitalHuman/types';
import type { QuestionnaireAnswerHistory } from '@/services/ai-video/questionnaire/types';
import { scriptGenerationApi } from '@/services/ai-video/script-generation/api';
import { voiceApi } from '@/services/ai-video/voice/api';
import AvatarSpaceView from './avatar-space/AvatarSpaceView';
import type { AvatarSpaceSource } from './avatar-space/model';
import LibraryView, { type DetailRequest } from './components/LibraryView';
import StudioDetailDrawer from './components/StudioDetailDrawer';
import StudioIcon from './components/StudioIcon';
import StudioSider from './components/StudioSider';
import StudioTopbar from './components/StudioTopbar';
import VoiceFilePreview from './components/VoiceFilePreview';
import WorkflowSteps from './components/WorkflowSteps';
import {
  initialStudioState,
  ROUTE_META,
  type StudioRoute,
  type StudioState,
} from './model';
import { isQuestionnaireOtherOption } from './questionnaireAnswers';
import StudioAuthGate from './StudioAuthGate';
import AssetStep from './steps/AssetStep';
import BaseStep from './steps/BaseStep';
import DemandStep from './steps/DemandStep';
import ExportStep from './steps/ExportStep';
import ScriptStep from './steps/ScriptStep';
import TimelineStep from './steps/TimelineStep';
import VoiceStep from './steps/VoiceStep';
import { usePersonalQuotaAccount } from './usePersonalQuotaAccount';
import './style.css';

type ModalType = 'avatar' | 'voice' | undefined;

const STUDIO_ROUTES: readonly StudioRoute[] = [
  'create',
  'avatars',
  'voices',
  'scripts',
  'works',
];

function readInitialStudioState(): StudioState {
  const parameters = new URLSearchParams(history.location.search);
  const requestedRoute = parameters.get('view');
  const route = STUDIO_ROUTES.includes(requestedRoute as StudioRoute)
    ? (requestedRoute as StudioRoute)
    : initialStudioState.route;
  const requestedStep = Number(parameters.get('step'));
  const step =
    route === 'create' &&
    Number.isInteger(requestedStep) &&
    requestedStep >= 0 &&
    requestedStep < 7
      ? requestedStep
      : initialStudioState.step;
  const timelineProjectId =
    route === 'create' ? parameters.get('projectId')?.trim() || null : null;

  return { ...initialStudioState, route, step, timelineProjectId };
}

function replaceStudioRoute(route: StudioRoute): void {
  history.replace(`/studio?view=${route}`);
}

function replaceCreationRoute(
  step: number,
  projectId: string,
  renderTaskId?: string,
): void {
  const parameters = new URLSearchParams({
    view: 'create',
    step: String(step),
    projectId,
  });
  if (renderTaskId) {
    parameters.set('renderTaskId', renderTaskId);
  }
  history.replace(`/studio?${parameters.toString()}`);
}

const notifications = [
  {
    type: 'success',
    title: '夏季新品口播 v3 生成完成',
    desc: '数字人底片 base-001 已合成，可前往时间轴编辑',
    time: '5 分钟前',
    unread: true,
  },
  {
    type: 'task',
    title: '探店打卡 v2 重合成中',
    desc: '当前进度 65%，预计还需 2 分钟',
    time: '10 分钟前',
    unread: true,
  },
  {
    type: 'quota',
    title: '视频时长额度本月已用 78%',
    desc: '剩余 132 分钟，超出将无法创建新任务',
    time: '2 小时前',
    unread: true,
  },
  {
    type: 'risk',
    title: '内容风险提示',
    desc: '文案「失眠科普 v3」包含医疗建议措辞，已自动改写',
    time: '昨天 16:20',
    unread: false,
  },
];

const SCRIPT_GENERATION_CONTEXT_KEYS: readonly (keyof StudioState)[] = [
  'industry',
  'purpose',
  'customIndustry',
  'customPurpose',
  'survey',
  'surveyOtherAnswers',
  'questionnaire',
  'duration',
  'supplement',
];

function buildAnswerHistory(state: StudioState): QuestionnaireAnswerHistory[] {
  return (state.questionnaire?.questions ?? []).flatMap((question) => {
    const selectedValues = state.survey[question.id] ?? [];
    if (selectedValues.length === 0) {
      return [];
    }

    const selected = selectedValues.map((value) => {
      const option = question.options?.find((item) => item.value === value);
      const otherAnswer = state.surveyOtherAnswers[question.id]?.trim();
      const isOther = isQuestionnaireOtherOption(
        option ?? { label: '', value },
      );
      if (isOther && otherAnswer) {
        return { label: otherAnswer, value: otherAnswer };
      }
      return { label: option?.label ?? value, value };
    });

    return [
      {
        questionId: question.id,
        questionTitle: question.title,
        selectedLabels: selected.map((item) => item.label),
        selectedValues: selected.map((item) => item.value),
      },
    ];
  });
}

const StudioWorkspace: React.FC<{
  discoveryContent?: React.ReactNode;
  isDiscovery: boolean;
}> = ({ discoveryContent, isDiscovery }) => {
  const { initialState } = useModel('@@initialState') as {
    initialState?: AppAuthState;
  };
  const currentUser = initialState?.currentUser;
  const quotaQuery = usePersonalQuotaAccount(currentUser?.id);
  const [state, setState] = useState<StudioState>(readInitialStudioState);
  const [collapsed, setCollapsed] = useState(false);
  const [detail, setDetail] = useState<DetailRequest>();
  const [modal, setModal] = useState<ModalType>();
  const [isScriptGenerating, setIsScriptGenerating] = useState(false);
  const [renderTask, setRenderTask] = useState<TimelineTaskDetail>();
  const renderTaskIdRef = useRef<TimelineTaskDetail['taskId'] | undefined>(
    undefined,
  );
  const scriptGenerationInFlight = useRef(false);
  const scriptGenerationRef = useRef(0);
  const timelineProjectCreationInFlight = useRef(false);
  const timelineProjectSession = useRef(0);
  const timelineProjectKeys = useRef(createIdempotencyKeyStore()).current;
  const [avatarSpaceOpen, setAvatarSpaceOpen] = useState(false);
  const [avatarSpaceSource, setAvatarSpaceSource] =
    useState<AvatarSpaceSource>();
  const [messageApi, messageContext] = message.useMessage();
  const [voiceFile, setVoiceFile] = useState<File>();
  const [voiceName, setVoiceName] = useState('');
  const [voiceNote, setVoiceNote] = useState('');
  const [voiceUploading, setVoiceUploading] = useState(false);
  const voiceFileInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const saved = window.localStorage.getItem('dh-sidebar-collapsed');
    setCollapsed(saved === '1');
  }, []);

  const invalidateScriptGeneration = useCallback(() => {
    if (!scriptGenerationInFlight.current) {
      return;
    }
    scriptGenerationRef.current += 1;
    scriptGenerationInFlight.current = false;
    setIsScriptGenerating(false);
    messageApi.destroy?.('studio-script-generation');
  }, [messageApi]);

  const update = useCallback(
    (patch: Partial<StudioState>) => {
      if (
        SCRIPT_GENERATION_CONTEXT_KEYS.some((key) => Object.hasOwn(patch, key))
      ) {
        invalidateScriptGeneration();
      }
      setState((current) => ({ ...current, ...patch }));
    },
    [invalidateScriptGeneration],
  );

  const toast = useCallback(
    (content: string, type: 'success' | 'error' = 'success') => {
      void messageApi.open({ content, type });
    },
    [messageApi],
  );

  const switchRoute = (route: StudioRoute) => {
    setAvatarSpaceOpen(false);
    setAvatarSpaceSource(undefined);
    update({ route });
    replaceStudioRoute(route);
    setDetail(undefined);
    window.scrollTo({ top: 0, behavior: 'instant' });
  };

  const openDiscovery = () => {
    setAvatarSpaceOpen(false);
    setAvatarSpaceSource(undefined);
    setDetail(undefined);
    setModal(undefined);
    history.push('/discover');
    window.scrollTo({ top: 0, behavior: 'instant' });
  };

  const navigateCreate = (step: number) => {
    update({ route: 'create', step });
    replaceStudioRoute('create');
    setDetail(undefined);
    window.scrollTo({ top: 0, behavior: 'instant' });
  };

  const newProject = () => {
    invalidateScriptGeneration();
    timelineProjectSession.current += 1;
    renderTaskIdRef.current = undefined;
    setRenderTask(undefined);
    setState({ ...initialStudioState });
    replaceStudioRoute('create');
    setDetail(undefined);
    toast('已创建新项目');
  };

  const openNotifications = () =>
    setDetail({
      title: '消息中心',
      subtitle: '任务进度 · 额度提醒 · 风险提示 · 系统通知',
      content: (
        <>
          <div className="notification-overview">
            <span>3 条未读</span>
            <button
              className="btn btn-ghost btn-sm"
              type="button"
              onClick={() => toast('已全部标为已读')}
            >
              全部标为已读
            </button>
          </div>
          {notifications.map((item) => (
            <div className="notif-item" key={item.title}>
              <span className={`notif-icon ${item.type}`}>
                <StudioIcon
                  name={
                    item.type === 'success'
                      ? 'check'
                      : item.type === 'risk'
                        ? 'warning'
                        : item.type === 'quota'
                          ? 'clock'
                          : 'refresh'
                  }
                />
              </span>
              <div>
                <b>{item.title}</b>
                <p>{item.desc}</p>
              </div>
              <aside>
                <small>{item.time}</small>
                {item.unread && <i />}
              </aside>
            </div>
          ))}
        </>
      ),
      footer: (
        <button
          className="btn btn-ghost btn-sm"
          type="button"
          onClick={() => setDetail(undefined)}
        >
          关闭
        </button>
      ),
    });

  const previous = () => {
    const step = Math.max(0, state.step - 1);
    update({ step });
    if (state.timelineProjectId) {
      replaceCreationRoute(step, state.timelineProjectId);
    }
  };
  const next = async () => {
    if (state.step === 4) {
      const videoJob = state.videoJob;
      if (!isSucceededDigitalHumanJob(videoJob) || !videoJob.outputAvailable) {
        void messageApi.open({
          content: '请先完成数字人底片生成并确认真实媒体可用',
          type: 'error',
        });
        return;
      }
      if (state.timelineProjectId) {
        update({ step: 5, timelineSourceTaskId: videoJob.jobId });
        replaceCreationRoute(5, state.timelineProjectId);
        return;
      }
      if (timelineProjectCreationInFlight.current) {
        return;
      }

      timelineProjectCreationInFlight.current = true;
      const intent = `create-project:${timelineProjectSession.current}:${videoJob.jobId}`;
      try {
        const project = await createCreationTimelineApi(
          getRuntimeRuoYiAdapter(),
        ).createProject({
          sourceId: videoJob.jobId,
          sourceType: 'digital_human_job',
          idempotencyKey: timelineProjectKeys.forIntent(intent),
        });
        timelineProjectKeys.forget(intent);
        update({
          step: 5,
          timelineProjectId: project.projectId,
          timelineSourceTaskId: videoJob.jobId,
        });
        replaceCreationRoute(5, project.projectId);
      } catch (error) {
        void messageApi.open({
          content: getErrorMessage(error, '创建时间轴项目失败，请重试'),
          type: 'error',
        });
      } finally {
        timelineProjectCreationInFlight.current = false;
      }
      return;
    }
    if (state.step !== 0) {
      const step = Math.min(6, state.step + 1);
      update({ step });
      if (state.timelineProjectId) {
        replaceCreationRoute(
          step,
          state.timelineProjectId,
          step === 6 ? renderTaskIdRef.current : undefined,
        );
      }
      return;
    }
    if (scriptGenerationInFlight.current) {
      return;
    }
    if (!state.industry || !state.purpose) {
      void messageApi.open({
        content: '请先完成需求问卷',
        type: 'error',
      });
      return;
    }

    scriptGenerationInFlight.current = true;
    const scriptGeneration = ++scriptGenerationRef.current;
    setIsScriptGenerating(true);
    void messageApi.open({
      content: '正在生成文案…',
      duration: 0,
      key: 'studio-script-generation',
      type: 'loading',
    });
    try {
      const result = await scriptGenerationApi.generate({
        answerHistory: buildAnswerHistory(state),
        demandText: state.supplement,
        durationSeconds: state.duration,
        industryCode:
          state.industry === 'custom'
            ? state.customIndustry.trim()
            : state.industry,
        purposeCode:
          state.purpose === '__custom'
            ? state.customPurpose.trim()
            : state.purpose,
      });
      if (scriptGeneration !== scriptGenerationRef.current) {
        return;
      }
      if (result.modelMode !== 'deepseek') {
        throw new Error('文案生成未使用 DeepSeek，请重试');
      }
      const scripts = result.scripts.filter((script) => script.body.trim());
      if (scripts.length === 0) {
        throw new Error('DeepSeek 未返回有效文案，请重试');
      }
      update({
        scriptBodies: scripts.map((script) => script.body),
        scriptVersions: scripts,
        selectedScript: 0,
        step: 1,
      });
      void messageApi.open({
        content: '文案已生成',
        key: 'studio-script-generation',
        type: 'success',
      });
    } catch (error) {
      if (scriptGeneration !== scriptGenerationRef.current) {
        return;
      }
      void messageApi.open({
        content: getErrorMessage(error, 'DeepSeek 文案生成失败，请稍后重试'),
        key: 'studio-script-generation',
        type: 'error',
      });
    } finally {
      if (scriptGeneration === scriptGenerationRef.current) {
        scriptGenerationInFlight.current = false;
        setIsScriptGenerating(false);
      }
    }
  };
  const finish = () => {
    update({ route: 'works' });
    replaceStudioRoute('works');
    toast('作品已保存到「作品」');
  };

  const stepProps = {
    state,
    update,
    onPrevious: previous,
    onNext: next,
    onFinish: finish,
    onToast: toast,
  };

  const renderStep = () => {
    if (state.step === 0) {
      return (
        <DemandStep {...stepProps} isGeneratingScript={isScriptGenerating} />
      );
    }
    if (state.step === 1) return <ScriptStep {...stepProps} />;
    if (state.step === 2) {
      return (
        <AssetStep
          {...stepProps}
          onAddAvatar={() => setModal('avatar')}
          onAddVoice={() => setModal('voice')}
        />
      );
    }
    if (state.step === 3) return <VoiceStep {...stepProps} />;
    if (state.step === 4) return <BaseStep {...stepProps} />;
    if (state.step === 5) {
      return (
        <TimelineStep
          {...stepProps}
          projectId={state.timelineProjectId ?? undefined}
          sourceText={state.scriptBodies[state.selectedScript]}
          taskUserId={currentUser?.id}
          onRenderTaskChange={(task) => {
            renderTaskIdRef.current =
              task?.kind === 'render' ? task.taskId : undefined;
            setRenderTask(task);
          }}
        />
      );
    }
    return (
      <ExportStep
        projectId={state.timelineProjectId ?? undefined}
        renderTask={renderTask}
        taskUserId={currentUser?.id}
        onBackToTimeline={() => {
          renderTaskIdRef.current = undefined;
          update({ step: 5 });
          if (state.timelineProjectId) {
            replaceCreationRoute(5, state.timelineProjectId);
          }
        }}
        onFinish={finish}
        onNext={next}
        onPrevious={previous}
        onToast={toast}
      />
    );
  };

  const meta = ROUTE_META[state.route];

  if (!currentUser) {
    return null;
  }

  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#0071e3',
          borderRadius: 10,
          colorText: '#1d1d1f',
          colorBorder: '#d2d2d7',
          fontFamily:
            '"SF Pro Text","Helvetica Neue",Helvetica,Arial,sans-serif',
        },
      }}
    >
      {messageContext}
      <div className={`studio-shell app ${collapsed ? 'collapsed' : ''}`}>
        <StudioSider
          activeKey={isDiscovery ? 'discover' : state.route}
          collapsed={collapsed}
          currentUser={currentUser}
          quotaState={quotaQuery.state}
          onCollapsedChange={(value) => {
            setCollapsed(value);
            window.localStorage.setItem(
              'dh-sidebar-collapsed',
              value ? '1' : '0',
            );
          }}
          onDiscover={openDiscovery}
          onRetryQuota={quotaQuery.retry}
          onRouteChange={switchRoute}
        />
        <div
          className={`main ${!isDiscovery && avatarSpaceOpen ? 'avatar-space-main' : ''}`}
        >
          {isDiscovery ? (
            discoveryContent
          ) : avatarSpaceOpen ? (
            <AvatarSpaceView
              initialAvatar={avatarSpaceSource}
              onBack={() => {
                setAvatarSpaceOpen(false);
                setAvatarSpaceSource(undefined);
              }}
              onToast={toast}
            />
          ) : (
            <>
              <StudioTopbar
                description={meta.description}
                title={meta.title}
                onNewProject={newProject}
                onNotifications={openNotifications}
              />
              <main className="content">
                {state.route === 'create' ? (
                  <>
                    <WorkflowSteps
                      current={state.step}
                      onBlocked={() => toast('请先完成当前步骤')}
                      onChange={(step) => update({ step })}
                    />
                    {renderStep()}
                  </>
                ) : (
                  <LibraryView
                    route={state.route}
                    onAddAvatar={() => setModal('avatar')}
                    onOpenAvatarSpace={(source) => {
                      setAvatarSpaceSource(source);
                      setAvatarSpaceOpen(true);
                    }}
                    onAddVoice={() => setModal('voice')}
                    onDetail={setDetail}
                    onNavigateCreate={navigateCreate}
                    onToast={toast}
                  />
                )}
              </main>
            </>
          )}
        </div>
      </div>
      <StudioDetailDrawer
        detail={detail}
        onClose={() => setDetail(undefined)}
      />
      <Modal
        centered
        confirmLoading={voiceUploading}
        open={Boolean(modal)}
        title={modal === 'avatar' ? '新增人物形象' : '新增原声音'}
        okText="上传并保存"
        cancelText="取消"
        onCancel={() => {
          if (!voiceUploading) {
            setModal(undefined);
            setVoiceFile(undefined);
          }
        }}
        onOk={async () => {
          if (modal !== 'voice') {
            toast('形象已上传，正在处理');
            setModal(undefined);
            return;
          }
          if (!voiceFile) {
            toast('请选择 MP3、WAV 或 M4A 声音文件', 'error');
            return;
          }
          if (!voiceName.trim()) {
            toast('请输入声音名称', 'error');
            return;
          }
          setVoiceUploading(true);
          try {
            await voiceApi.upload(voiceFile, {
              idempotencyKey: window.crypto.randomUUID(),
              name: voiceName.trim(),
              gender: 'unspecified',
              tags: [],
              note: voiceNote.trim() || undefined,
            });
            toast('原声音已上传，正在解析文本');
            setModal(undefined);
            setVoiceFile(undefined);
            setVoiceName('');
            setVoiceNote('');
            window.dispatchEvent(new Event('aivideo:voice-changed'));
          } catch (error) {
            toast(
              getErrorMessage(error, '声音上传失败，请检查文件后重试'),
              'error',
            );
          } finally {
            setVoiceUploading(false);
          }
        }}
      >
        <div className="upload-modal-content">
          <button
            className="upload-dropzone"
            type="button"
            onClick={() =>
              modal === 'voice'
                ? voiceFileInput.current?.click()
                : toast('请选择图片')
            }
          >
            <StudioIcon name="upload" />
            <b>
              {modal === 'avatar'
                ? '点击或拖拽上传人物照片'
                : '点击或拖拽上传参考音频'}
            </b>
            <small>
              {modal === 'avatar'
                ? '支持 PNG / JPG / WebP · 最短边 ≥ 512px · ≤ 10MB'
                : '支持 WAV / MP3 / M4A · ≤ 100MB'}
            </small>
          </button>
          {modal === 'voice' && (
            <input
              ref={voiceFileInput}
              accept=".mp3,.wav,.m4a,audio/mpeg,audio/wav,audio/mp4"
              hidden
              type="file"
              onChange={(event) => setVoiceFile(event.target.files?.[0])}
            />
          )}
          {modal === 'voice' && voiceFile && (
            <VoiceFilePreview file={voiceFile} />
          )}
          <label className="field">
            <span className="field-label">
              {modal === 'avatar' ? '形象名称' : '声音名称'}
            </span>
            <input
              className="input"
              placeholder={
                modal === 'avatar' ? '例如：亲切女主播' : '例如：亲切女声'
              }
              value={modal === 'voice' ? voiceName : undefined}
              onChange={(event) => {
                if (modal === 'voice') setVoiceName(event.target.value);
              }}
            />
          </label>
          <label className="field">
            <span className="field-label">备注（可选）</span>
            <input
              className="input"
              placeholder="用途、风格等"
              value={modal === 'voice' ? voiceNote : undefined}
              onChange={(event) => {
                if (modal === 'voice') setVoiceNote(event.target.value);
              }}
            />
          </label>
          {modal === 'voice' && (
            <div className="upload-warning">
              <StudioIcon name="warning" />
              上传后将由服务器本地 Whisper 在后台解析声音文本。
            </div>
          )}
        </div>
      </Modal>
    </ConfigProvider>
  );
};

const Studio: React.FC = () => {
  const { pathname } = useLocation();
  const isDiscovery = pathname.startsWith('/discover');

  return (
    <StudioAuthGate>
      <StudioWorkspace
        discoveryContent={isDiscovery ? <Outlet /> : undefined}
        isDiscovery={isDiscovery}
      />
      {!isDiscovery ? <Outlet /> : null}
    </StudioAuthGate>
  );
};

export default Studio;
