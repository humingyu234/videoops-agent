import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Empty, Skeleton } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { getHttpStatus } from '@/services/ai-video/core/errors';
import { getRuntimeRuoYiAdapter } from '@/services/ai-video/core/runtimeRuoYiAdapter';
import {
  type CreationAssetsApi,
  createCreationAssetsApi,
} from '@/services/ai-video/creation-assets/api';
import {
  type CreationTimelineApi,
  createCreationTimelineApi,
} from '@/services/ai-video/creation-timeline/api';
import { timelineQueryKeys } from '@/services/ai-video/creation-timeline/queryKeys';
import type { TimelineTaskDetail } from '@/services/ai-video/creation-timeline/types';
import type { TasksApi } from '@/services/ai-video/tasks/api';
import { tasksApi as runtimeTasksApi } from '@/services/ai-video/tasks/api';
import StepFooter from '../components/StepFooter';
import StudioIcon from '../components/StudioIcon';
import { useTimelineTask } from '../timeline/useTimelineTask';

type OutputTimelineApi = Pick<CreationTimelineApi, 'getLatestOutput'> &
  Partial<Pick<CreationTimelineApi, 'getProject'>>;
type OutputAssetsApi = Pick<CreationAssetsApi, 'content'>;

export interface ExportStepProps {
  onPrevious: () => void;
  onNext: () => void;
  onFinish: () => void;
  onBackToTimeline: () => void;
  onToast: (message: string) => void;
  projectId?: string;
  timelineApi?: OutputTimelineApi;
  assetsApi?: OutputAssetsApi;
  renderTask?: TimelineTaskDetail;
  taskApi?: Pick<TasksApi, 'get'>;
  taskUserId?: string;
}

function getRouteProjectId(): string | undefined {
  if (typeof window === 'undefined') return undefined;
  const projectId = new URLSearchParams(window.location.search)
    .get('projectId')
    ?.trim();
  return projectId || undefined;
}

function getRouteRenderTaskId(): string | undefined {
  if (typeof window === 'undefined') return undefined;
  const taskId = new URLSearchParams(window.location.search)
    .get('renderTaskId')
    ?.trim();
  return taskId || undefined;
}

function isRenderInProgress(task: TimelineTaskDetail | undefined): boolean {
  return (
    task?.kind === 'render' &&
    (task.status === 'pending' ||
      task.status === 'queued' ||
      task.status === 'running')
  );
}

function getOutputErrorMessage(error: unknown): string {
  const status = getHttpStatus(error);
  if (status === 403) return '无权访问该创作项目';
  if (status === 404) return '暂无可用成品';
  return '成品加载失败，请稍后重试';
}

const ExportStep = ({
  onPrevious,
  onNext,
  onFinish,
  onBackToTimeline,
  onToast,
  projectId,
  timelineApi,
  assetsApi,
  renderTask,
  taskApi,
  taskUserId,
}: ExportStepProps) => {
  const resolvedProjectId = projectId ?? getRouteProjectId();
  const resolvedTimelineApi = useMemo<OutputTimelineApi>(
    () => timelineApi ?? createCreationTimelineApi(getRuntimeRuoYiAdapter()),
    [timelineApi],
  );
  const resolvedAssetsApi = useMemo<OutputAssetsApi>(
    () => assetsApi ?? createCreationAssetsApi(getRuntimeRuoYiAdapter()),
    [assetsApi],
  );
  const renderTaskId =
    renderTask?.kind === 'render' ? renderTask.taskId : getRouteRenderTaskId();
  const renderPolling = useTimelineTask({
    api: taskApi ?? runtimeTasksApi,
    enabled: Boolean(renderTaskId && taskUserId),
    taskId: renderTaskId,
    userId: taskUserId ?? 'creation-timeline',
    workspaceId: 'creation-timeline',
  });
  const currentRenderTask =
    renderPolling.task?.kind === 'render' ? renderPolling.task : renderTask;
  const projectQuery = useQuery({
    queryKey: timelineQueryKeys.project(resolvedProjectId ?? 'missing-project'),
    queryFn: () => {
      if (!resolvedProjectId || !resolvedTimelineApi.getProject) {
        throw new Error('A project api is required to load project state');
      }
      return resolvedTimelineApi.getProject(resolvedProjectId);
    },
    enabled: Boolean(resolvedProjectId && resolvedTimelineApi.getProject),
    refetchInterval: (query) =>
      query.state.data?.status === 'rendering' ? 2_000 : false,
    retry: false,
  });
  const isAwaitingRenderTask = Boolean(
    renderTaskId && !currentRenderTask && renderPolling.isLoading,
  );
  const isLoadingProject = Boolean(
    resolvedProjectId &&
      resolvedTimelineApi.getProject &&
      projectQuery.isPending,
  );
  const isAwaitingFinalRenderDetail = Boolean(
    renderTaskId &&
      taskUserId &&
      currentRenderTask?.kind === 'render' &&
      currentRenderTask.status === 'success' &&
      renderPolling.finalization?.kind !== 'confirmed',
  );
  const isRenderStatePending =
    projectQuery.data?.status === 'rendering' ||
    isRenderInProgress(currentRenderTask) ||
    isAwaitingRenderTask;
  const canLoadOutput =
    Boolean(resolvedProjectId) &&
    !isLoadingProject &&
    !projectQuery.isError &&
    !isRenderStatePending &&
    !isAwaitingFinalRenderDetail &&
    !renderPolling.error &&
    (!currentRenderTask || currentRenderTask.status === 'success');
  const outputQuery = useQuery({
    queryKey: timelineQueryKeys.output(resolvedProjectId ?? 'missing-project'),
    queryFn: () => {
      if (!resolvedProjectId) {
        throw new Error('A project id is required to load the final output');
      }
      return resolvedTimelineApi.getLatestOutput(resolvedProjectId);
    },
    enabled: canLoadOutput,
    retry: false,
  });
  const [mediaUrl, setMediaUrl] = useState<string>();
  const [mediaError, setMediaError] = useState<unknown>();
  const [isLoadingMedia, setIsLoadingMedia] = useState(false);

  useEffect(() => {
    const output = outputQuery.data;
    let objectUrl: string | undefined;
    let active = true;

    setMediaUrl(undefined);
    setMediaError(undefined);
    setIsLoadingMedia(Boolean(output));

    if (!output) {
      return () => undefined;
    }

    void resolvedAssetsApi
      .content(output.outputAssetId)
      .then((blob) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setMediaUrl(objectUrl);
      })
      .catch((error: unknown) => {
        if (active) setMediaError(error);
      })
      .finally(() => {
        if (active) setIsLoadingMedia(false);
      });

    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [outputQuery.data, resolvedAssetsApi]);

  const downloadOutput = () => {
    if (!mediaUrl || !outputQuery.data) return;
    const anchor = document.createElement('a');
    anchor.href = mediaUrl;
    anchor.download = `creation-output-${outputQuery.data.outputAssetId}.mp4`;
    anchor.click();
    onToast('已开始下载当前成品');
  };

  const output = outputQuery.data;
  const outputError = outputQuery.error;
  const outputMismatchesRenderTask =
    Boolean(output) &&
    currentRenderTask?.kind === 'render' &&
    currentRenderTask.status === 'success' &&
    (!currentRenderTask.resultAssetId ||
      output?.taskId !== currentRenderTask.taskId ||
      output?.outputAssetId !== currentRenderTask.resultAssetId);

  return (
    <>
      <section aria-label="最终成品预览" className="export-grid">
        {!resolvedProjectId ? (
          <Empty description="未找到创作项目" />
        ) : isLoadingProject || isRenderStatePending ? (
          <Alert
            description="合成任务仍在进行中，完成后会在此显示真实成品。"
            message="合成处理中"
            showIcon
            type="info"
          />
        ) : projectQuery.isError || renderPolling.error ? (
          <Alert
            description={getOutputErrorMessage(
              projectQuery.error ?? renderPolling.error,
            )}
            message="无法确认合成状态"
            showIcon
            type="error"
          />
        ) : isAwaitingFinalRenderDetail ||
          renderPolling.finalization?.kind === 'unconfirmed' ? (
          <Alert
            description="任务终态尚未通过最终详情确认，暂不读取成品。"
            message="最终状态待确认"
            showIcon
            type="warning"
          />
        ) : currentRenderTask?.kind === 'render' &&
          currentRenderTask.status !== 'success' ? (
          <Alert
            description={
              currentRenderTask.errorSummary ?? '合成任务未产生可用成品。'
            }
            message="合成未完成"
            showIcon
            type="error"
          />
        ) : outputQuery.isPending ? (
          <Skeleton active paragraph={{ rows: 6 }} />
        ) : outputError ? (
          <Alert
            description={getOutputErrorMessage(outputError)}
            message="无法读取最终成品"
            showIcon
            type="error"
          />
        ) : outputMismatchesRenderTask ? (
          <Alert
            description="最终任务详情与当前项目成品不一致，请稍后重试。"
            message="成品不一致"
            showIcon
            type="error"
          />
        ) : mediaError ? (
          <Alert
            description="无法读取当前项目的最终素材，请重新生成或联系管理员。"
            message="成品素材不可用"
            showIcon
            type="error"
          />
        ) : isLoadingMedia ? (
          <Skeleton active paragraph={{ rows: 6 }} />
        ) : output && mediaUrl ? (
          <div className="export-grid">
            <div className="preview-stage">
              {/* biome-ignore lint/a11y/useMediaCaption: C0 exposes no caption-track asset; a fabricated VTT would misrepresent the server-owned output. */}
              <video
                aria-label="预览最终成品"
                className="export-video"
                controls
                src={mediaUrl}
              />
            </div>
            <aside className="export-side">
              <div className="card card-tight">
                <div className="prop-title">当前成品</div>
                <div className="prop-row">
                  <span className="prop-label">素材 ID</span>
                  <span className="numeric">{output.outputAssetId}</span>
                </div>
                <div className="prop-row">
                  <span className="prop-label">生成任务</span>
                  <span className="numeric">{output.taskId}</span>
                </div>
                <div className="prop-row">
                  <span className="prop-label">生成时间</span>
                  <time className="numeric" dateTime={output.createdAt}>
                    {output.createdAt}
                  </time>
                </div>
              </div>
              <Button block onClick={downloadOutput} type="primary">
                <StudioIcon name="download" /> 下载当前成品
              </Button>
              <Button block onClick={onBackToTimeline}>
                <StudioIcon name="edit" /> 返回时间轴继续编辑
              </Button>
            </aside>
          </div>
        ) : (
          <Empty description="暂无可用成品" />
        )}
      </section>
      <StepFooter
        step={6}
        onPrevious={onPrevious}
        onNext={onNext}
        onFinish={onFinish}
      />
    </>
  );
};

export default ExportStep;
