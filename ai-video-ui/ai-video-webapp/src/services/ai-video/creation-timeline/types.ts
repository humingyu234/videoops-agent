export type Brand<T, Name extends string> = T & { readonly __brand: Name };

export type DecimalId = Brand<string, 'DecimalId'>;
export type CreationProjectId = Brand<DecimalId, 'CreationProjectId'>;
export type TimelineRevision = Brand<string, 'TimelineRevision'>;
export type TimelineSchemaVersion = 'timeline-1';

export type TimelineTrackType =
  | 'fancy_text'
  | 'subtitle'
  | 'visual_effect'
  | 'image_overlay'
  | 'pip_video'
  | 'main_video'
  | 'primary_audio'
  | 'background_music'
  | 'sound_effect';

export type TimelineElementType =
  | 'fancy_text'
  | 'subtitle'
  | 'visual_effect'
  | 'image_overlay'
  | 'pip_video'
  | 'main_video'
  | 'audio';

export type TimelineCanvas = {
  width: 1080;
  height: 1920;
  frameRate: 30;
  durationMs: number;
  safeMarginRatio: 0.05;
};

type TimelineElementBase = {
  elementId: string;
  startMs: number;
  endMs: number;
  zIndex: number;
  enabled: boolean;
  locked: boolean;
  label: string;
};

export type TimelineTransform = {
  xRatio: number;
  yRatio: number;
  widthRatio: number;
  heightRatio: number;
  rotationDeg: number;
  opacity: number;
};

export type TimelineCrop = {
  xRatio: number;
  yRatio: number;
  widthRatio: number;
  heightRatio: number;
};

export type TimelineFade = { fadeInMs: number; fadeOutMs: number };

export type MainVideoElement = TimelineElementBase & {
  elementType: 'main_video';
  assetId: DecimalId;
  sourceDurationMs: number;
  sourceStartMs: number;
  fitMode: 'contain' | 'cover';
};

export type ImageOverlayElement = TimelineElementBase & {
  elementType: 'image_overlay';
  assetId: DecimalId;
  transform: TimelineTransform;
  fitMode: 'contain' | 'cover';
  crop: TimelineCrop;
  fade: TimelineFade;
  sourceStartOffset: number;
  sourceEndOffset: number;
  adoptedPrompt: string | null;
  sourceTaskId: DecimalId | null;
};

export type PipVideoElement = TimelineElementBase & {
  elementType: 'pip_video';
  assetId: DecimalId;
  transform: TimelineTransform;
  fitMode: 'contain' | 'cover';
  crop: TimelineCrop;
  fade: TimelineFade;
  sourceDurationMs: number;
  sourceStartMs: number;
  loopWhenOverflow: true;
  audioEnabled: false;
};

export type SubtitleElement = TimelineElementBase & {
  elementType: 'subtitle';
  sourceTextSnapshot: string;
  displayText: string;
  sourceStartOffset: number;
  sourceEndOffset: number;
  fontCode: 'noto_sans_cjk_sc_regular' | 'noto_serif_cjk_sc_regular';
  fontVersion: string;
  fontSha256: string;
  fontSizePx: number;
  color: string;
  backgroundEnabled: boolean;
  backgroundColor?: string;
  outlineEnabled: boolean;
  outlineColor?: string;
  outlineWidthPx: number;
  safeAreaAnchor: 'upper' | 'center' | 'lower';
  alignment: 'left' | 'center' | 'right';
};

export type FancyTextElement = TimelineElementBase & {
  elementType: 'fancy_text';
  text: string;
  templateCode: 'keyword_pop' | 'gold_impact' | 'neon_breathe' | 'handwriting_reveal' | 'bubble_bounce' | 'title_wipe';
  fontCode: 'noto_sans_cjk_sc_regular' | 'noto_serif_cjk_sc_regular';
  fontVersion: string;
  fontSha256: string;
  color: string;
  accentColor: string;
  transform: TimelineTransform;
  animationIntensity: 'subtle' | 'normal' | 'strong';
  enterDurationMs: number;
  exitDurationMs: number;
  suggestionTaskId: DecimalId | null;
  suggestionReason: string | null;
};

export type AudioElement = TimelineElementBase & {
  elementType: 'audio';
  assetId: DecimalId;
  usageType: 'primary_audio' | 'background_music' | 'sound_effect';
  sourceDurationMs: number;
  sourceStartMs: number;
  sourceEndMs: number;
  volumeRatio: number;
  fade: TimelineFade;
  loopWhenOverflow: boolean;
  duckingEnabled: boolean;
  targetGainRatio?: number;
  attackMs?: number;
  releaseMs?: number;
};

export type VisualEffectElement = TimelineElementBase & {
  elementType: 'visual_effect';
  effectCode: 'fade_in' | 'fade_out' | 'gentle_zoom_in' | 'gentle_zoom_out' | 'light_blur';
  durationMs: number;
  scale: number | null;
  radius: number | null;
};

export type TimelineElement =
  | MainVideoElement
  | ImageOverlayElement
  | PipVideoElement
  | SubtitleElement
  | FancyTextElement
  | AudioElement
  | VisualEffectElement;

export type TimelineTrack = {
  trackId: string;
  trackType: TimelineTrackType;
  area: 'top' | 'center' | 'bottom';
  order: number;
  locked: boolean;
  muted: boolean;
  elements: TimelineElement[];
};

export type TimelineDocument = {
  schemaVersion: TimelineSchemaVersion;
  canvas: TimelineCanvas;
  tracks: TimelineTrack[];
};

export type CreationProject = {
  projectId: CreationProjectId;
  projectTitle: string;
  sourceType: 'digital_human_job';
  sourceId: DecimalId;
  baseVideoAssetId: DecimalId;
  primaryAudioAssetId?: DecimalId;
  status: 'editing' | 'rendering' | 'ready' | 'archived';
  canvas: Omit<TimelineCanvas, 'safeMarginRatio'>;
  currentDraftRevision: TimelineRevision;
  schemaVersion: TimelineSchemaVersion;
  latestOutputAssetId?: DecimalId;
  createdAt: string;
  updatedAt: string;
};

export type CreationOutput = {
  projectId: CreationProjectId;
  outputAssetId: DecimalId;
  taskId: DecimalId;
  createdAt: string;
};

export type TimelineDraft = {
  projectId: CreationProjectId;
  timelineDraftId: DecimalId;
  revision: TimelineRevision;
  schemaVersion: TimelineSchemaVersion;
  contentHash: string;
  timeline: TimelineDocument;
  savedAt: string;
};

export type TimelineVersion = {
  versionId: DecimalId;
  projectId: CreationProjectId;
  versionNo: string;
  sourceDraftRevision: TimelineRevision;
  schemaVersion: TimelineSchemaVersion;
  contentHash: string;
  versionReason: 'manual_save' | 'restored' | 'render_input' | 'conflict_copy';
  sourceVersionId?: DecimalId;
  createdAt: string;
  replayed?: boolean;
};

export type TimelineSaveStatus =
  | { kind: 'saved'; revision: string; contentHash: string }
  | { kind: 'dirty'; basedOnRevision: string }
  | { kind: 'saving'; requestKey: string; basedOnRevision: string }
  | { kind: 'failed'; requestKey: string; retryable: boolean }
  | {
      kind: 'conflict';
      baseRevision: string;
      serverRevision: string;
      snapshot: TimelineDocument;
    };

export type TimelineOutputConfig = {
  resolutionPreset: 'match_canvas';
  frameRate: 30;
  qualityPreset: 'standard' | 'high';
};

export type TimelineTaskStatus =
  | 'pending'
  | 'queued'
  | 'running'
  | 'success'
  | 'failed'
  | 'cancelled';

export type TimelineTaskCommon = {
  taskId: DecimalId;
  taskType: string;
  resourceType: 'creation_project';
  resourceId: CreationProjectId;
  inputVersionId?: DecimalId;
  status: TimelineTaskStatus;
  stage: string;
  progress: number;
  canCancel: boolean;
  canRetry: boolean;
  errorCode?: string;
  errorSummary?: string;
  createdAt: string;
  startedAt?: string;
  finishedAt?: string;
};

export type TimelineImagePromptSuggestion = {
  prompt: string;
  negativePrompt: string;
  styleTags: string[];
  reason: string;
};

export type TimelineFancyTextSuggestion = {
  sourceText: string;
  sourceStartOffset: number;
  sourceEndOffset: number;
  startMs: number;
  durationMs: number;
  templateCode: 'keyword_pop' | 'gold_impact' | 'neon_breathe' | 'handwriting_reveal' | 'bubble_bounce' | 'title_wipe';
  xRatio: number;
  yRatio: number;
  primaryColor: string;
  accentColor: string;
  reason: string;
};

export type TimelineAlignedSubtitle = {
  sourceStartOffset: number;
  sourceEndOffset: number;
  displayText: string;
  startMs: number;
  endMs: number;
};

export type TimelineTaskDetail =
  | (TimelineTaskCommon & {
      kind: 'image-prompt';
      taskType: 'timeline_image_prompt_generate';
      result?: { taskId: DecimalId; suggestions: TimelineImagePromptSuggestion[] };
    })
  | (TimelineTaskCommon & {
      kind: 'fancy-text';
      taskType: 'timeline_fancy_text_suggest';
      result?: { taskId: DecimalId; suggestions: TimelineFancyTextSuggestion[] };
    })
  | (TimelineTaskCommon & {
      kind: 'subtitle-alignment';
      taskType: 'timeline_subtitle_align';
      result?: { taskId: DecimalId; sourceType: 'trusted_cue' | 'whisper'; subtitles: TimelineAlignedSubtitle[] };
    })
  | (TimelineTaskCommon & {
      kind: 'render';
      taskType: 'timeline_render';
      resultAssetId?: DecimalId;
      resultSchemaVersion?: string;
    })
  | (TimelineTaskCommon & { kind: 'unknown'; taskType: string });

export type TimelineTaskListItem = Omit<TimelineTaskCommon, 'taskType'> & {
  taskType: string;
  kind: TimelineTaskDetail['kind'];
};
