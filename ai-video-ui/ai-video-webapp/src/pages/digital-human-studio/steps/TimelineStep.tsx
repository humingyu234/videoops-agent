import { useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { getHttpStatus } from '@/services/ai-video/core/errors';
import { getRuntimeRuoYiAdapter } from '@/services/ai-video/core/runtimeRuoYiAdapter';
import type { CreationAssetsApi } from '@/services/ai-video/creation-assets/api';
import { createCreationAssetsApi } from '@/services/ai-video/creation-assets/api';
import type { CreationAsset } from '@/services/ai-video/creation-assets/types';
import type { CreationTimelineApi } from '@/services/ai-video/creation-timeline/api';
import { createCreationTimelineApi } from '@/services/ai-video/creation-timeline/api';
import { createIdempotencyKeyStore } from '@/services/ai-video/creation-timeline/idempotency';
import type {
  AudioElement,
  FancyTextElement,
  SubtitleElement,
  TimelineAlignedSubtitle,
  TimelineDocument,
  TimelineDraft,
  TimelineElement,
  TimelineFancyTextSuggestion,
  TimelineImagePromptSuggestion,
  TimelineTaskDetail,
  TimelineTrackType,
  VisualEffectElement,
} from '@/services/ai-video/creation-timeline/types';
import type { TasksApi } from '@/services/ai-video/tasks/api';
import { createTasksApi } from '@/services/ai-video/tasks/api';
import StepFooter from '../components/StepFooter';
import type { StudioState } from '../model';
import AiSuggestionPanel from '../timeline/AiSuggestionPanel';
import CreationAssetPicker, {
  createImageOverlayElement,
  createPictureInPictureElement,
} from '../timeline/CreationAssetPicker';
import type { TimelineAddAction } from '../timeline/ElementAddBar';
import { fixedTimelineFontFor } from '../timeline/fancyTextTemplates';
import {
  createTimelineEditorState,
  timelineReducer,
} from '../timeline/reducer';
import { findTimelineElement } from '../timeline/selectors';
import { normalizeSubtitleText } from '../timeline/subtitle';
import type { TimelineEditorStatus } from '../timeline/TimelineEditor';
import TimelineEditor from '../timeline/TimelineEditor';
import TimelineSaveStatus from '../timeline/TimelineSaveStatus';
import { useTimelineAutosave } from '../timeline/useTimelineAutosave';
import { useTimelineDraft } from '../timeline/useTimelineDraft';
import { useTimelineTask } from '../timeline/useTimelineTask';
import { useTimelineVersions } from '../timeline/useTimelineVersions';

type TimelineDraftApi = Pick<CreationTimelineApi, 'getDraft' | 'saveDraft'>;
type TimelineEditorApi = TimelineDraftApi &
  Partial<
    Pick<
      CreationTimelineApi,
      | 'createConflictCopy'
      | 'createFancyTextSuggestionTask'
      | 'createImagePromptTask'
      | 'createRenderTask'
      | 'createSubtitleAlignmentTask'
      | 'createVersion'
      | 'listVersions'
      | 'restoreVersion'
    >
  >;

type AssetAction = Extract<
  TimelineAddAction,
  'image' | 'picture-in-picture' | 'background-music' | 'sound-effect'
>;

type AssetSelection = {
  action: AssetAction;
  trackType: TimelineTrackType;
};

const unavailableTasksApi: TasksApi = {
  cancel: () => Promise.reject(new Error('任务服务暂不可用')),
  get: () => Promise.reject(new Error('任务服务暂不可用')),
  list: () => Promise.reject(new Error('任务服务暂不可用')),
  retry: () => Promise.reject(new Error('任务服务暂不可用')),
};

interface TimelineStepProps {
  state: StudioState;
  update: (patch: Partial<StudioState>) => void;
  onPrevious: () => void;
  onNext: () => void;
  onFinish: () => void;
  onToast: (message: string) => void;
  projectId?: string;
  sourceText?: string;
  timelineApi?: TimelineEditorApi;
  assetsApi?: CreationAssetsApi;
  tasksApi?: TasksApi;
  taskUserId?: string;
  onRenderTaskChange?: (task: TimelineTaskDetail | undefined) => void;
}

function statusForDraftError(error: unknown): TimelineEditorStatus {
  return getHttpStatus(error) === 403 ? 'forbidden' : 'error';
}

function elementId(prefix: string): string {
  return `${prefix}-${globalThis.crypto?.randomUUID?.() ?? Date.now()}`;
}

function findTrackId(
  tracks: TimelineDocument['tracks'],
  trackType: TimelineTrackType,
): string | undefined {
  return tracks.find((track) => track.trackType === trackType)?.trackId;
}

function defaultSubtitle(
  sourceText: string,
  durationMs: number,
): SubtitleElement | undefined {
  const normalized = normalizeSubtitleText(sourceText);
  if (!normalized) return undefined;
  const font = fixedTimelineFontFor('noto_sans_cjk_sc_regular');
  return {
    elementId: elementId('subtitle'),
    elementType: 'subtitle',
    startMs: 0,
    endMs: Math.min(durationMs, 4_000),
    zIndex: 400,
    enabled: true,
    locked: false,
    label: '新字幕',
    sourceTextSnapshot: sourceText,
    displayText: normalized,
    sourceStartOffset: 0,
    sourceEndOffset: Array.from(sourceText.normalize('NFC')).length,
    fontCode: font.fontCode,
    fontVersion: font.version,
    fontSha256: font.sha256,
    fontSizePx: 48,
    color: '#FFFFFFFF',
    backgroundEnabled: true,
    backgroundColor: '#00000099',
    outlineEnabled: true,
    outlineColor: '#000000FF',
    outlineWidthPx: 2,
    safeAreaAnchor: 'lower',
    alignment: 'center',
  };
}

function defaultFancyText(durationMs: number): FancyTextElement {
  const font = fixedTimelineFontFor('noto_sans_cjk_sc_regular');
  return {
    elementId: elementId('fancy'),
    elementType: 'fancy_text',
    startMs: 0,
    endMs: Math.min(durationMs, 3_000),
    zIndex: 500,
    enabled: true,
    locked: false,
    label: '新花字',
    text: '重点信息',
    templateCode: 'keyword_pop',
    fontCode: font.fontCode,
    fontVersion: font.version,
    fontSha256: font.sha256,
    color: '#FFFFFFFF',
    accentColor: '#FFCC00FF',
    transform: {
      xRatio: 0.15,
      yRatio: 0.12,
      widthRatio: 0.7,
      heightRatio: 0.12,
      rotationDeg: 0,
      opacity: 1,
    },
    animationIntensity: 'normal',
    enterDurationMs: 400,
    exitDurationMs: 300,
    suggestionTaskId: null,
    suggestionReason: null,
  };
}

function defaultVisualEffect(durationMs: number): VisualEffectElement {
  const effectDuration = Math.min(durationMs, 1_500);
  return {
    elementId: elementId('effect'),
    elementType: 'visual_effect',
    startMs: 0,
    endMs: effectDuration,
    zIndex: 300,
    enabled: true,
    locked: false,
    label: '开场淡入',
    effectCode: 'fade_in',
    durationMs: effectDuration,
    scale: null,
    radius: null,
  };
}

function createAudioElement(
  asset: CreationAsset,
  action: Extract<AssetAction, 'background-music' | 'sound-effect'>,
  projectDurationMs: number,
): AudioElement {
  const sourceDurationMs = asset.durationMs;
  if (
    asset.assetType !== 'audio' ||
    !Number.isInteger(sourceDurationMs) ||
    sourceDurationMs === null ||
    sourceDurationMs <= 0
  ) {
    throw new Error('请选择具有有效时长的音频素材');
  }
  const backgroundMusic = action === 'background-music';
  const endMs = backgroundMusic
    ? projectDurationMs
    : Math.min(projectDurationMs, sourceDurationMs);
  return {
    elementId: elementId(backgroundMusic ? 'bgm' : 'sfx'),
    elementType: 'audio',
    startMs: 0,
    endMs,
    zIndex: 0,
    enabled: true,
    locked: false,
    label: asset.originalName,
    assetId: asset.assetId,
    usageType: backgroundMusic ? 'background_music' : 'sound_effect',
    sourceDurationMs,
    sourceStartMs: 0,
    sourceEndMs: sourceDurationMs,
    volumeRatio: backgroundMusic ? 0.3 : 0.8,
    fade: { fadeInMs: 0, fadeOutMs: 0 },
    loopWhenOverflow: backgroundMusic,
    duckingEnabled: backgroundMusic,
    ...(backgroundMusic
      ? { targetGainRatio: 0.35, attackMs: 120, releaseMs: 400 }
      : {}),
  };
}

function canUseVersions(api: TimelineEditorApi): api is CreationTimelineApi {
  return (
    typeof api.createVersion === 'function' &&
    typeof api.listVersions === 'function' &&
    typeof api.restoreVersion === 'function'
  );
}

function sourceRangeForSelectedSubtitle(
  timeline: TimelineDocument,
  selectedElementId: string | undefined,
) {
  const selected = selectedElementId
    ? findTimelineElement(timeline, selectedElementId)?.element
    : undefined;
  if (selected?.elementType !== 'subtitle') return undefined;
  return {
    sourceEndOffset: selected.sourceEndOffset,
    sourceStartOffset: selected.sourceStartOffset,
  };
}

function ProjectTimelineEditor({
  api,
  assetsApi,
  onFinish,
  onNext,
  onRenderTaskChange,
  onPrevious,
  onToast,
  projectId,
  sourceText,
  taskUserId,
  tasksApi,
}: {
  api: TimelineEditorApi;
  assetsApi?: CreationAssetsApi;
  onFinish: () => void;
  onNext: () => void;
  onPrevious: () => void;
  onRenderTaskChange?: (task: TimelineTaskDetail | undefined) => void;
  onToast: (message: string) => void;
  projectId: string;
  sourceText?: string;
  taskUserId?: string;
  tasksApi?: TasksApi;
}) {
  const [editor, dispatch] = useReducer(timelineReducer, undefined, () =>
    createTimelineEditorState(),
  );
  const [assetSelection, setAssetSelection] = useState<AssetSelection>();
  const [acceptedImagePrompt, setAcceptedImagePrompt] = useState<{
    prompt: string;
    sourceEndOffset: number;
    sourceStartOffset: number;
    taskId: TimelineTaskDetail['taskId'];
  }>();
  const [activeTaskId, setActiveTaskId] =
    useState<TimelineTaskDetail['taskId']>();
  const [activeTaskSourceSelection, setActiveTaskSourceSelection] =
    useState<ReturnType<typeof sourceRangeForSelectedSubtitle>>();
  const [rendering, setRendering] = useState(false);
  const actionKeys = useRef(createIdempotencyKeyStore()).current;
  const draft = useTimelineDraft(api as CreationTimelineApi, projectId);
  const loadedRevisionRef = useRef<string | undefined>(undefined);
  const appliedSaveRef = useRef<string | undefined>(undefined);
  const versionsEnabled = canUseVersions(api);
  const versions = useTimelineVersions(
    api as CreationTimelineApi,
    versionsEnabled ? projectId : undefined,
  );
  const timelineTask = useTimelineTask({
    api: tasksApi ?? unavailableTasksApi,
    enabled: Boolean(activeTaskId && taskUserId),
    taskId: activeTaskId,
    userId: taskUserId ?? 'creation-timeline',
    workspaceId: 'creation-timeline',
  });

  const autosave = useTimelineAutosave({
    api,
    projectId,
    revision: editor.revision,
    timeline: draft.data ? editor.timeline : undefined,
  });
  const applyServerDraft = (server: TimelineDraft) => {
    loadedRevisionRef.current = server.revision;
    autosave.rebaseline(server.timeline, server.revision, server.contentHash);
    dispatch({
      type: 'serverLoaded',
      timeline: server.timeline,
      revision: server.revision,
    });
  };

  useEffect(() => {
    if (!draft.data || loadedRevisionRef.current === draft.data.revision) {
      return;
    }
    applyServerDraft(draft.data);
  }, [draft.data]);

  useEffect(() => {
    const saved = autosave.lastSaved;
    if (!saved) return;
    const savedKey = `${saved.revision}:${saved.contentHash}`;
    if (appliedSaveRef.current === savedKey) return;
    appliedSaveRef.current = savedKey;
    loadedRevisionRef.current = saved.revision;
    dispatch({
      type: 'serverNormalized',
      timeline: saved.timeline,
      revision: saved.revision,
    });
  }, [autosave.lastSaved]);
  const expectedRevision =
    autosave.saveStatus.kind === 'saved'
      ? autosave.saveStatus.revision
      : editor.revision;
  const status: TimelineEditorStatus = draft.isPending
    ? 'loading'
    : draft.isError
      ? statusForDraftError(draft.error)
      : draft.data
        ? 'ready'
        : 'empty';

  const addElement = (
    action: TimelineAddAction,
    trackType: TimelineTrackType,
  ) => {
    const trackId = findTrackId(editor.timeline.tracks, trackType);
    if (!trackId) {
      onToast('当前项目没有可用的目标轨道');
      return;
    }
    if (
      action === 'image' ||
      action === 'picture-in-picture' ||
      action === 'background-music' ||
      action === 'sound-effect'
    ) {
      if (!assetsApi) {
        onToast('素材服务暂不可用');
        return;
      }
      setAssetSelection({ action, trackType });
      return;
    }
    if (action === 'subtitle') {
      const element = defaultSubtitle(
        sourceText ?? '',
        editor.timeline.canvas.durationMs,
      );
      if (!element) {
        onToast('请先选择含有项目脚本的字幕来源');
        return;
      }
      dispatch({ type: 'elementAdded', trackId, element });
      dispatch({ type: 'elementSelected', elementId: element.elementId });
      return;
    }
    if (action === 'fancy-text') {
      const element = defaultFancyText(editor.timeline.canvas.durationMs);
      dispatch({ type: 'elementAdded', trackId, element });
      dispatch({ type: 'elementSelected', elementId: element.elementId });
      return;
    }
    const element = defaultVisualEffect(editor.timeline.canvas.durationMs);
    dispatch({ type: 'elementAdded', trackId, element });
    dispatch({ type: 'elementSelected', elementId: element.elementId });
  };

  const selectAsset = (asset: CreationAsset) => {
    if (!assetSelection) return;
    const trackId = findTrackId(
      editor.timeline.tracks,
      assetSelection.trackType,
    );
    if (!trackId) {
      onToast('当前项目没有可用的目标轨道');
      return;
    }
    try {
      const { action } = assetSelection;
      const element: TimelineElement =
        action === 'image'
          ? {
              ...createImageOverlayElement({
                asset,
                elementId: elementId('image'),
                positionMs: 0,
                projectDurationMs: editor.timeline.canvas.durationMs,
              }),
              ...(acceptedImagePrompt
                ? {
                    adoptedPrompt: acceptedImagePrompt.prompt,
                    sourceEndOffset: acceptedImagePrompt.sourceEndOffset,
                    sourceStartOffset: acceptedImagePrompt.sourceStartOffset,
                    sourceTaskId: acceptedImagePrompt.taskId,
                  }
                : {}),
            }
          : action === 'picture-in-picture'
            ? createPictureInPictureElement({
                asset,
                elementId: elementId('pip'),
                positionMs: 0,
                projectDurationMs: editor.timeline.canvas.durationMs,
              })
            : createAudioElement(
                asset,
                action,
                editor.timeline.canvas.durationMs,
              );
      dispatch({ type: 'elementAdded', trackId, element });
      dispatch({ type: 'elementSelected', elementId: element.elementId });
      if (action === 'image') setAcceptedImagePrompt(undefined);
      setAssetSelection(undefined);
    } catch (error) {
      onToast(error instanceof Error ? error.message : '素材无法加入时间轴');
    }
  };

  const startSuggestionTask = async (
    kind: 'fancy-text' | 'image-prompt' | 'subtitle-alignment',
  ) => {
    if (!expectedRevision) {
      onToast('时间轴尚未加载完成');
      return;
    }
    try {
      let task: TimelineTaskDetail;
      let sourceSelection: ReturnType<typeof sourceRangeForSelectedSubtitle>;
      if (kind === 'subtitle-alignment') {
        const subtitleElementIds = editor.timeline.tracks
          .flatMap((track) => track.elements)
          .filter((element) => element.elementType === 'subtitle')
          .map((element) => element.elementId);
        if (
          subtitleElementIds.length === 0 ||
          !api.createSubtitleAlignmentTask
        ) {
          onToast('请先添加字幕后再请求字幕对齐');
          return;
        }
        task = await api.createSubtitleAlignmentTask(projectId, {
          expectedRevision,
          idempotencyKey: actionKeys.beginNewIntent('subtitle-alignment'),
          subtitleElementIds,
        });
      } else {
        sourceSelection = sourceRangeForSelectedSubtitle(
          editor.timeline,
          editor.selectedElementId,
        );
        if (!sourceSelection) {
          onToast('请先选择需要处理的字幕');
          return;
        }
        if (kind === 'fancy-text') {
          if (!api.createFancyTextSuggestionTask) {
            onToast('当前环境不支持花字建议');
            return;
          }
          task = await api.createFancyTextSuggestionTask(projectId, {
            animationIntensity: 'normal',
            expectedRevision,
            idempotencyKey: actionKeys.beginNewIntent('fancy-text-suggestion'),
            sourceSelection,
          });
        } else {
          if (!api.createImagePromptTask) {
            onToast('当前环境不支持图片提示词建议');
            return;
          }
          task = await api.createImagePromptTask(projectId, {
            expectedRevision,
            idempotencyKey: actionKeys.beginNewIntent('image-prompt'),
            sourceSelection,
            style: 'cinematic',
          });
        }
      }
      setActiveTaskId(task.taskId);
      setActiveTaskSourceSelection(sourceSelection);
      onToast('AI 建议任务已创建');
    } catch {
      onToast('AI 建议任务创建失败，请重试');
    }
  };

  const acceptFancyText = (suggestion: TimelineFancyTextSuggestion) => {
    const trackId = findTrackId(editor.timeline.tracks, 'fancy_text');
    if (!trackId || !activeTaskId) return;
    const font = fixedTimelineFontFor('noto_sans_cjk_sc_regular');
    const endMs = Math.min(
      editor.timeline.canvas.durationMs,
      suggestion.startMs + suggestion.durationMs,
    );
    if (endMs <= suggestion.startMs) {
      onToast('花字建议超出项目时长');
      return;
    }
    const element: FancyTextElement = {
      ...defaultFancyText(editor.timeline.canvas.durationMs),
      elementId: elementId('fancy'),
      startMs: suggestion.startMs,
      endMs,
      text: suggestion.sourceText,
      templateCode: suggestion.templateCode,
      fontCode: font.fontCode,
      fontVersion: font.version,
      fontSha256: font.sha256,
      color: suggestion.primaryColor,
      accentColor: suggestion.accentColor,
      transform: {
        xRatio: Math.min(suggestion.xRatio, 0.3),
        yRatio: Math.min(suggestion.yRatio, 0.88),
        widthRatio: 0.7,
        heightRatio: 0.12,
        rotationDeg: 0,
        opacity: 1,
      },
      suggestionTaskId: activeTaskId,
      suggestionReason: suggestion.reason,
    };
    dispatch({ type: 'elementAdded', trackId, element });
    dispatch({ type: 'elementSelected', elementId: element.elementId });
    onToast('已采用花字建议');
  };

  const acceptSubtitle = (suggestion: TimelineAlignedSubtitle) => {
    const trackId = findTrackId(editor.timeline.tracks, 'subtitle');
    if (!trackId || !sourceText) return;
    if (
      !Number.isInteger(suggestion.startMs) ||
      !Number.isInteger(suggestion.endMs) ||
      suggestion.startMs < 0 ||
      suggestion.endMs <= suggestion.startMs ||
      suggestion.endMs > editor.timeline.canvas.durationMs
    ) {
      onToast('字幕建议的时间范围无效');
      return;
    }
    const source = Array.from(sourceText.normalize('NFC'));
    const sourceSlice = source
      .slice(suggestion.sourceStartOffset, suggestion.sourceEndOffset)
      .join('');
    if (normalizeSubtitleText(sourceSlice) !== suggestion.displayText) {
      onToast('字幕建议未通过项目脚本校验');
      return;
    }
    const element = defaultSubtitle(
      sourceText,
      editor.timeline.canvas.durationMs,
    );
    if (!element) return;
    element.elementId = elementId('subtitle');
    element.startMs = suggestion.startMs;
    element.endMs = suggestion.endMs;
    element.displayText = suggestion.displayText;
    element.sourceStartOffset = suggestion.sourceStartOffset;
    element.sourceEndOffset = suggestion.sourceEndOffset;
    element.label = 'AI 对齐字幕';
    dispatch({ type: 'elementAdded', trackId, element });
    dispatch({ type: 'elementSelected', elementId: element.elementId });
    onToast('已采用字幕对齐建议');
  };

  const refreshServer = async () => {
    const result = await draft.refetch();
    if (result.data) applyServerDraft(result.data);
  };

  const restoreServer = () => {
    if (
      typeof window !== 'undefined' &&
      !window.confirm('重新加载会丢弃本地冲突修改，是否继续？')
    ) {
      return;
    }
    void refreshServer().then(
      () => onToast('已重新加载服务器草稿'),
      () => onToast('重新加载服务器草稿失败，请重试'),
    );
  };

  const saveConflictCopy = async () => {
    if (autosave.saveStatus.kind !== 'conflict' || !api.createConflictCopy) {
      return;
    }
    try {
      await api.createConflictCopy(projectId, {
        baseRevision: autosave.saveStatus.baseRevision,
        idempotencyKey: actionKeys.forIntent('conflict-copy'),
        schemaVersion: 'timeline-1',
        timeline: autosave.saveStatus.snapshot,
      });
      actionKeys.forget('conflict-copy');
      await refreshServer();
      onToast('已另存为冲突版本');
    } catch {
      onToast('另存冲突版本失败，请重试');
    }
  };

  const createVersion = async () => {
    if (!versionsEnabled || !expectedRevision || !api.createVersion) return;
    try {
      await api.createVersion(projectId, {
        expectedRevision,
        idempotencyKey: actionKeys.beginNewIntent('manual-version'),
      });
      await versions.refetch();
      onToast('已创建时间轴版本');
    } catch {
      onToast('创建时间轴版本失败，请重试');
    }
  };

  const restoreVersion = async (versionId: string) => {
    if (!versionsEnabled || !expectedRevision) return;
    try {
      const result = await versions.restoreVersion({
        expectedRevision,
        versionId,
      });
      applyServerDraft(result);
      onToast('已恢复时间轴版本');
    } catch {
      onToast('恢复时间轴版本失败，请重试');
    }
  };

  const startRender = async () => {
    if (!expectedRevision || autosave.saveStatus.kind !== 'saved') {
      onToast('请等待当前草稿保存完成后再合成');
      return;
    }
    if (!api.createRenderTask || rendering) {
      return;
    }
    setRendering(true);
    try {
      const task = await api.createRenderTask(projectId, {
        expectedRevision,
        idempotencyKey: actionKeys.forIntent('render'),
        outputConfig: {
          resolutionPreset: 'match_canvas',
          frameRate: 30,
          qualityPreset: 'high',
        },
      });
      actionKeys.forget('render');
      onRenderTaskChange?.(task);
      onNext();
    } catch {
      onToast('合成任务创建失败，请重试');
    } finally {
      setRendering(false);
    }
  };

  const allowedAssetTypes: CreationAsset['assetType'][] = assetSelection
    ? assetSelection.action === 'image'
      ? ['image']
      : assetSelection.action === 'picture-in-picture'
        ? ['video']
        : ['audio']
    : ['image'];

  return (
    <>
      <TimelineEditor
        selectedElementId={editor.selectedElementId}
        status={status}
        timeline={editor.timeline}
        onAddElement={addElement}
        onAction={dispatch}
        onElementChange={(element) => {
          const {
            elementId: changedElementId,
            elementType: _elementType,
            ...patch
          } = element;
          dispatch({
            type: 'elementPatched',
            elementId: changedElementId,
            patch,
          });
        }}
        onSelect={(elementId) =>
          dispatch({ type: 'elementSelected', elementId })
        }
      />
      {status === 'ready' && (
        <section aria-label="时间轴保存与任务" className="timeline-actions">
          <TimelineSaveStatus
            saveStatus={autosave.saveStatus}
            onReloadServer={restoreServer}
            onRetry={autosave.retry}
            onSaveConflictCopy={saveConflictCopy}
          />
          <div>
            <button
              disabled={!versionsEnabled || !expectedRevision}
              type="button"
              onClick={() => void createVersion()}
            >
              保存版本
            </button>
            {versions.data?.rows.map((version) => (
              <button
                key={version.versionId}
                type="button"
                onClick={() => void restoreVersion(version.versionId)}
              >
                恢复版本 {version.versionNo}
              </button>
            ))}
          </div>
          <div>
            <button
              type="button"
              onClick={() => void startSuggestionTask('image-prompt')}
            >
              生成图片提示词建议
            </button>
            <button
              type="button"
              onClick={() => void startSuggestionTask('fancy-text')}
            >
              生成花字建议
            </button>
            <button
              type="button"
              onClick={() => void startSuggestionTask('subtitle-alignment')}
            >
              对齐字幕
            </button>
          </div>
          {activeTaskId && timelineTask.isLoading && (
            <p role="status">正在读取 AI 建议任务</p>
          )}
          {activeTaskId &&
            timelineTask.task?.kind !== 'render' &&
            timelineTask.task?.status === 'success' && (
              <AiSuggestionPanel
                sourceText={sourceText}
                task={timelineTask.task}
                onAcceptFancyText={acceptFancyText}
                onAcceptImagePrompt={(
                  suggestion: TimelineImagePromptSuggestion,
                ) => {
                  if (!activeTaskSourceSelection) {
                    onToast('图片建议缺少已核验的字幕范围');
                    return;
                  }
                  setAcceptedImagePrompt({
                    ...activeTaskSourceSelection,
                    prompt: suggestion.prompt,
                    taskId: activeTaskId,
                  });
                  onToast('已确认图片提示词；请选择图片素材后再加入时间轴');
                }}
                onAcceptSubtitle={acceptSubtitle}
                onReject={() => {
                  setActiveTaskId(undefined);
                  setActiveTaskSourceSelection(undefined);
                  onToast('已拒绝 AI 建议');
                }}
              />
            )}
          {activeTaskId &&
            timelineTask.task &&
            timelineTask.task.status !== 'success' && (
              <p
                role={
                  timelineTask.task.status === 'failed' ||
                  timelineTask.task.status === 'cancelled'
                    ? 'alert'
                    : 'status'
                }
              >
                {timelineTask.task.status === 'failed' ||
                timelineTask.task.status === 'cancelled'
                  ? (timelineTask.task.errorSummary ?? 'AI 建议任务未完成')
                  : 'AI 建议任务处理中'}
              </p>
            )}
          {activeTaskId && timelineTask.error && (
            <button type="button" onClick={timelineTask.retry}>
              重试读取 AI 任务
            </button>
          )}
        </section>
      )}
      {assetsApi && (
        <CreationAssetPicker
          allowedAssetTypes={allowedAssetTypes}
          api={assetsApi}
          open={Boolean(assetSelection)}
          usageIntent="timeline"
          onClose={() => setAssetSelection(undefined)}
          onSelect={selectAsset}
        />
      )}
      <StepFooter
        step={5}
        nextEnabled={
          status === 'ready' &&
          autosave.saveStatus.kind === 'saved' &&
          !rendering
        }
        nextLabel={rendering ? '正在创建合成任务…' : '去预览作品'}
        onFinish={onFinish}
        onNext={() => void startRender()}
        onPrevious={onPrevious}
      />
    </>
  );
}

const TimelineStep = ({
  assetsApi,
  onFinish,
  onNext,
  onPrevious,
  onRenderTaskChange,
  onToast,
  projectId,
  sourceText,
  taskUserId,
  tasksApi,
  timelineApi,
}: TimelineStepProps) => {
  const fallbackTimelineApi = useMemo(
    () =>
      projectId && !timelineApi
        ? createCreationTimelineApi(getRuntimeRuoYiAdapter())
        : undefined,
    [projectId, timelineApi],
  );
  const useRuntimeApis = Boolean(projectId && !timelineApi);
  const fallbackAssetsApi = useMemo(
    () =>
      useRuntimeApis
        ? createCreationAssetsApi(getRuntimeRuoYiAdapter())
        : undefined,
    [useRuntimeApis],
  );
  const fallbackTasksApi = useMemo(
    () =>
      useRuntimeApis ? createTasksApi(getRuntimeRuoYiAdapter()) : undefined,
    [useRuntimeApis],
  );
  const api = timelineApi ?? fallbackTimelineApi;
  const resolvedAssetsApi = assetsApi ?? fallbackAssetsApi;
  const resolvedTasksApi = tasksApi ?? fallbackTasksApi;
  const [localEditor, dispatch] = useReducer(timelineReducer, undefined, () =>
    createTimelineEditorState(),
  );

  if (projectId && api) {
    return (
      <ProjectTimelineEditor
        api={api}
        assetsApi={resolvedAssetsApi}
        projectId={projectId}
        sourceText={sourceText}
        tasksApi={resolvedTasksApi}
        taskUserId={taskUserId}
        onFinish={onFinish}
        onNext={onNext}
        onPrevious={onPrevious}
        onRenderTaskChange={onRenderTaskChange}
        onToast={onToast}
      />
    );
  }

  return (
    <>
      <TimelineEditor
        selectedElementId={localEditor.selectedElementId}
        timeline={localEditor.timeline}
        onAction={dispatch}
        onSelect={(elementId) =>
          dispatch({ type: 'elementSelected', elementId })
        }
      />
      <StepFooter
        step={5}
        nextLabel="去预览作品"
        onFinish={onFinish}
        onNext={onNext}
        onPrevious={onPrevious}
      />
    </>
  );
};

export default TimelineStep;
