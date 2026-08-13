import {
  assertRecord,
  readArray,
  readDecimalString,
  readEnum,
  readString,
} from '../core/wire';
import { parseAiTaskVOWire } from '../tasks/adapter';
import type {
  CreationOutput,
  CreationProject,
  CreationProjectId,
  DecimalId,
  TimelineCanvas,
  TimelineDocument,
  TimelineDraft,
  TimelineElement,
  TimelineElementType,
  TimelineTaskCommon,
  TimelineTaskDetail,
  TimelineTaskListItem,
  TimelineTrack,
  TimelineTrackType,
  TimelineVersion,
} from './types';

type WireRecord = Record<string, unknown>;

const trackTypes = [
  'fancy_text', 'subtitle', 'visual_effect', 'image_overlay', 'pip_video',
  'main_video', 'primary_audio', 'background_music', 'sound_effect',
] as const;
const elementTypes = [
  'fancy_text', 'subtitle', 'visual_effect', 'image_overlay', 'pip_video',
  'main_video', 'audio',
] as const;
const expectedElementTypeByTrack: Partial<Record<TimelineTrackType, TimelineElementType>> = {
  fancy_text: 'fancy_text',
  subtitle: 'subtitle',
  visual_effect: 'visual_effect',
  image_overlay: 'image_overlay',
  pip_video: 'pip_video',
  main_video: 'main_video',
  primary_audio: 'audio',
  background_music: 'audio',
  sound_effect: 'audio',
};
const elementKeys: Record<TimelineElementType, readonly string[]> = {
  main_video: ['elementId', 'elementType', 'startMs', 'endMs', 'zIndex', 'enabled', 'locked', 'label', 'assetId', 'sourceDurationMs', 'sourceStartMs', 'fitMode'],
  image_overlay: ['elementId', 'elementType', 'startMs', 'endMs', 'zIndex', 'enabled', 'locked', 'label', 'assetId', 'transform', 'fitMode', 'crop', 'fade', 'sourceStartOffset', 'sourceEndOffset', 'adoptedPrompt', 'sourceTaskId'],
  pip_video: ['elementId', 'elementType', 'startMs', 'endMs', 'zIndex', 'enabled', 'locked', 'label', 'assetId', 'transform', 'fitMode', 'crop', 'fade', 'sourceDurationMs', 'sourceStartMs', 'loopWhenOverflow', 'audioEnabled'],
  subtitle: ['elementId', 'elementType', 'startMs', 'endMs', 'zIndex', 'enabled', 'locked', 'label', 'sourceTextSnapshot', 'displayText', 'sourceStartOffset', 'sourceEndOffset', 'fontCode', 'fontVersion', 'fontSha256', 'fontSizePx', 'color', 'backgroundEnabled', 'backgroundColor', 'outlineEnabled', 'outlineColor', 'outlineWidthPx', 'safeAreaAnchor', 'alignment'],
  fancy_text: ['elementId', 'elementType', 'startMs', 'endMs', 'zIndex', 'enabled', 'locked', 'label', 'text', 'templateCode', 'fontCode', 'fontVersion', 'fontSha256', 'color', 'accentColor', 'transform', 'animationIntensity', 'enterDurationMs', 'exitDurationMs', 'suggestionTaskId', 'suggestionReason'],
  audio: ['elementId', 'elementType', 'startMs', 'endMs', 'zIndex', 'enabled', 'locked', 'label', 'assetId', 'usageType', 'sourceDurationMs', 'sourceStartMs', 'sourceEndMs', 'volumeRatio', 'fade', 'loopWhenOverflow', 'duckingEnabled', 'targetGainRatio', 'attackMs', 'releaseMs'],
  visual_effect: ['elementId', 'elementType', 'startMs', 'endMs', 'zIndex', 'enabled', 'locked', 'label', 'effectCode', 'durationMs', 'scale', 'radius'],
};

const requiredElementKeys: Record<TimelineElementType, readonly string[]> = {
  main_video: elementKeys.main_video,
  image_overlay: elementKeys.image_overlay,
  pip_video: elementKeys.pip_video,
  subtitle: ['elementId', 'elementType', 'startMs', 'endMs', 'zIndex', 'enabled', 'locked', 'label', 'sourceTextSnapshot', 'displayText', 'sourceStartOffset', 'sourceEndOffset', 'fontCode', 'fontVersion', 'fontSha256', 'fontSizePx', 'color', 'backgroundEnabled', 'outlineEnabled', 'outlineWidthPx', 'safeAreaAnchor', 'alignment'],
  fancy_text: elementKeys.fancy_text,
  audio: ['elementId', 'elementType', 'startMs', 'endMs', 'zIndex', 'enabled', 'locked', 'label', 'assetId', 'usageType', 'sourceDurationMs', 'sourceStartMs', 'sourceEndMs', 'volumeRatio', 'fade', 'loopWhenOverflow', 'duckingEnabled'],
  visual_effect: elementKeys.visual_effect,
};

const areaByTrack: Record<TimelineTrackType, 'top' | 'center' | 'bottom'> = {
  fancy_text: 'top',
  subtitle: 'top',
  visual_effect: 'top',
  image_overlay: 'top',
  pip_video: 'top',
  main_video: 'center',
  primary_audio: 'bottom',
  background_music: 'bottom',
  sound_effect: 'bottom',
};

function assertOnlyKnownKeys(record: WireRecord, allowed: readonly string[], field: string): void {
  if (Object.keys(record).some((key) => !allowed.includes(key))) {
    throw new Error(`Invalid wire response: ${field} contains an unknown field`);
  }
}

function assertRequiredKeys(record: WireRecord, required: readonly string[], field: string): void {
  const missing = required.find((key) => !Object.hasOwn(record, key));
  if (missing) throw new Error(`Invalid wire response: ${field}.${missing} is required`);
}

function readBoolean(record: WireRecord, key: string): boolean {
  if (typeof record[key] !== 'boolean') throw new Error(`Invalid wire response: ${key} must be a boolean`);
  return record[key] as boolean;
}

function readInteger(record: WireRecord, key: string, minimum: number, maximum: number): number {
  const value = record[key];
  if (!Number.isSafeInteger(value) || (value as number) < minimum || (value as number) > maximum) {
    throw new Error(`Invalid wire response: ${key} must be an in-range integer`);
  }
  return value as number;
}

function readPositiveDecimalId(record: WireRecord, key: string): string {
  const value = readDecimalString(record, key);
  if (!/^[1-9]\d*$/.test(value)) throw new Error(`Invalid wire response: ${key} must be a positive decimal string`);
  return value;
}

function readNumber(record: WireRecord, key: string, minimum: number, maximum: number): number {
  const value = record[key];
  if (typeof value !== 'number' || !Number.isFinite(value) || value < minimum || value > maximum) {
    throw new Error(`Invalid wire response: ${key} must be an in-range number`);
  }
  return value;
}

function readNullableString(record: WireRecord, key: string): string | null {
  const value = record[key];
  if (value !== null && typeof value !== 'string') throw new Error(`Invalid wire response: ${key} must be a string or null`);
  return value;
}

function readNullableNumber(record: WireRecord, key: string, minimum: number, maximum: number): number | null {
  const value = record[key];
  if (value === null) return null;
  if (typeof value !== 'number' || !Number.isFinite(value) || value < minimum || value > maximum) {
    throw new Error(`Invalid wire response: ${key} must be an in-range number or null`);
  }
  return value;
}

function readKey(record: WireRecord, key: string): string {
  const value = readString(record, key);
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(value)) throw new Error(`Invalid wire response: ${key} must be a timeline key`);
  return value;
}

function readColor(record: WireRecord, key: string): string {
  const value = readString(record, key);
  if (!/^#[0-9A-F]{8}$/.test(value)) throw new Error(`Invalid wire response: ${key} must be an RGBA color`);
  return value;
}

function readSha256(record: WireRecord, key: string): string {
  const value = readString(record, key);
  if (!/^[0-9a-f]{64}$/.test(value)) throw new Error(`Invalid wire response: ${key} must be a SHA-256`);
  return value;
}

function assertDecimalStringOrNull(record: WireRecord, key: string): void {
  if (record[key] === null) return;
  readPositiveDecimalId(record, key);
}

function assertTransform(value: unknown): void {
  const record = assertRecord(value, 'transform');
  assertOnlyKnownKeys(record, ['xRatio', 'yRatio', 'widthRatio', 'heightRatio', 'rotationDeg', 'opacity'], 'transform');
  assertRequiredKeys(record, ['xRatio', 'yRatio', 'widthRatio', 'heightRatio', 'rotationDeg', 'opacity'], 'transform');
  readNumber(record, 'xRatio', 0, 1);
  readNumber(record, 'yRatio', 0, 1);
  readNumber(record, 'widthRatio', Number.MIN_VALUE, 1);
  readNumber(record, 'heightRatio', Number.MIN_VALUE, 1);
  readNumber(record, 'rotationDeg', -180, 180);
  readNumber(record, 'opacity', 0, 1);
}

function assertCrop(value: unknown): void {
  const record = assertRecord(value, 'crop');
  assertOnlyKnownKeys(record, ['xRatio', 'yRatio', 'widthRatio', 'heightRatio'], 'crop');
  assertRequiredKeys(record, ['xRatio', 'yRatio', 'widthRatio', 'heightRatio'], 'crop');
  const xRatio = readNumber(record, 'xRatio', 0, 0.9999);
  const yRatio = readNumber(record, 'yRatio', 0, 0.9999);
  const widthRatio = readNumber(record, 'widthRatio', Number.MIN_VALUE, 1);
  const heightRatio = readNumber(record, 'heightRatio', Number.MIN_VALUE, 1);
  if (xRatio + widthRatio > 1 || yRatio + heightRatio > 1) throw new Error('Invalid wire response: crop exceeds the source bounds');
}

function assertFade(value: unknown): void {
  const record = assertRecord(value, 'fade');
  assertOnlyKnownKeys(record, ['fadeInMs', 'fadeOutMs'], 'fade');
  assertRequiredKeys(record, ['fadeInMs', 'fadeOutMs'], 'fade');
  readInteger(record, 'fadeInMs', 0, 120000);
  readInteger(record, 'fadeOutMs', 0, 120000);
}

function asDecimalId(value: string): DecimalId { return value as DecimalId; }

function parseCanvas(value: unknown, timeline: boolean): TimelineCanvas | CreationProject['canvas'] {
  const record = assertRecord(value, 'canvas');
  assertOnlyKnownKeys(record, timeline ? ['width', 'height', 'frameRate', 'durationMs', 'safeMarginRatio'] : ['width', 'height', 'frameRate', 'durationMs'], 'canvas');
  if (readInteger(record, 'width', 1080, 1080) !== 1080 || readInteger(record, 'height', 1920, 1920) !== 1920 || readInteger(record, 'frameRate', 30, 30) !== 30) throw new Error('Invalid wire response: canvas is not timeline-1');
  const canvas = { width: 1080 as const, height: 1920 as const, frameRate: 30 as const, durationMs: readInteger(record, 'durationMs', 1, 120000) };
  if (!timeline) return canvas;
  if (record.safeMarginRatio !== 0.05) throw new Error('Invalid wire response: safeMarginRatio is not timeline-1');
  return { ...canvas, safeMarginRatio: 0.05 as const };
}

function parseElement(value: unknown, index: number): TimelineElement {
  const record = assertRecord(value, `elements[${index}]`);
  const elementType = readEnum(record, 'elementType', elementTypes);
  assertOnlyKnownKeys(record, elementKeys[elementType], `elements[${index}]`);
  assertRequiredKeys(record, requiredElementKeys[elementType], `elements[${index}]`);
  readKey(record, 'elementId');
  const startMs = readInteger(record, 'startMs', 0, 119999);
  const endMs = readInteger(record, 'endMs', 1, 120000);
  const zIndex = readInteger(record, 'zIndex', 0, 999);
  readBoolean(record, 'enabled');
  readBoolean(record, 'locked');
  if (readString(record, 'label').length === 0 || readString(record, 'label').length > 128) throw new Error('Invalid wire response: label must be non-empty and bounded');
  if (startMs >= endMs) throw new Error('Invalid wire response: element startMs must precede endMs');

  switch (elementType) {
    case 'main_video':
      readPositiveDecimalId(record, 'assetId');
      readInteger(record, 'sourceDurationMs', 1, 120000);
      readInteger(record, 'sourceStartMs', 0, 119999);
      readEnum(record, 'fitMode', ['contain', 'cover'] as const);
      if (zIndex !== 0 || record.enabled !== true || record.locked !== true) throw new Error('Invalid wire response: main_video invariants are invalid');
      break;
    case 'image_overlay':
      readPositiveDecimalId(record, 'assetId');
      assertTransform(record.transform);
      readEnum(record, 'fitMode', ['contain', 'cover'] as const);
      assertCrop(record.crop);
      assertFade(record.fade);
      readInteger(record, 'sourceStartOffset', 0, 50000);
      readInteger(record, 'sourceEndOffset', 0, 50000);
      readNullableString(record, 'adoptedPrompt');
      assertDecimalStringOrNull(record, 'sourceTaskId');
      break;
    case 'pip_video':
      readPositiveDecimalId(record, 'assetId');
      assertTransform(record.transform);
      readEnum(record, 'fitMode', ['contain', 'cover'] as const);
      assertCrop(record.crop);
      assertFade(record.fade);
      readInteger(record, 'sourceDurationMs', 1, 120000);
      readInteger(record, 'sourceStartMs', 0, 119999);
      if (record.loopWhenOverflow !== true || record.audioEnabled !== false) throw new Error('Invalid wire response: pip_video invariants are invalid');
      break;
    case 'subtitle': {
      const backgroundEnabled = readBoolean(record, 'backgroundEnabled');
      const outlineEnabled = readBoolean(record, 'outlineEnabled');
      if (readString(record, 'sourceTextSnapshot').length === 0 || readString(record, 'displayText').length === 0) throw new Error('Invalid wire response: subtitle text must be non-empty');
      readInteger(record, 'sourceStartOffset', 0, 50000);
      readInteger(record, 'sourceEndOffset', 1, 50000);
      readEnum(record, 'fontCode', ['noto_sans_cjk_sc_regular', 'noto_serif_cjk_sc_regular'] as const);
      if (!/^[0-9]+\.[0-9]{3}$/.test(readString(record, 'fontVersion'))) throw new Error('Invalid wire response: fontVersion is invalid');
      readSha256(record, 'fontSha256');
      readInteger(record, 'fontSizePx', 12, 120);
      readColor(record, 'color');
      if (backgroundEnabled !== Object.hasOwn(record, 'backgroundColor')) throw new Error('Invalid wire response: subtitle background color does not match its flag');
      if (backgroundEnabled) readColor(record, 'backgroundColor');
      if (outlineEnabled !== Object.hasOwn(record, 'outlineColor')) throw new Error('Invalid wire response: subtitle outline color does not match its flag');
      if (outlineEnabled) readColor(record, 'outlineColor');
      readInteger(record, 'outlineWidthPx', 0, 8);
      readEnum(record, 'safeAreaAnchor', ['upper', 'center', 'lower'] as const);
      readEnum(record, 'alignment', ['left', 'center', 'right'] as const);
      break;
    }
    case 'fancy_text':
      if (readString(record, 'text').length === 0 || readString(record, 'text').length > 128) throw new Error('Invalid wire response: fancy text must be non-empty and bounded');
      readEnum(record, 'templateCode', ['keyword_pop', 'gold_impact', 'neon_breathe', 'handwriting_reveal', 'bubble_bounce', 'title_wipe'] as const);
      readEnum(record, 'fontCode', ['noto_sans_cjk_sc_regular', 'noto_serif_cjk_sc_regular'] as const);
      if (!/^[0-9]+\.[0-9]{3}$/.test(readString(record, 'fontVersion'))) throw new Error('Invalid wire response: fontVersion is invalid');
      readSha256(record, 'fontSha256');
      readColor(record, 'color');
      readColor(record, 'accentColor');
      assertTransform(record.transform);
      readEnum(record, 'animationIntensity', ['subtle', 'normal', 'strong'] as const);
      readInteger(record, 'enterDurationMs', 0, 3000);
      readInteger(record, 'exitDurationMs', 0, 3000);
      assertDecimalStringOrNull(record, 'suggestionTaskId');
      readNullableString(record, 'suggestionReason');
      break;
    case 'audio': {
      readPositiveDecimalId(record, 'assetId');
      const usageType = readEnum(record, 'usageType', ['primary_audio', 'background_music', 'sound_effect'] as const);
      readInteger(record, 'sourceDurationMs', 1, 120000);
      const sourceStartMs = readInteger(record, 'sourceStartMs', 0, 119999);
      const sourceEndMs = readInteger(record, 'sourceEndMs', 1, 120000);
      if (sourceStartMs >= sourceEndMs) throw new Error('Invalid wire response: audio source range is invalid');
      readNumber(record, 'volumeRatio', 0, 1);
      assertFade(record.fade);
      readBoolean(record, 'loopWhenOverflow');
      readBoolean(record, 'duckingEnabled');
      const hasDuckingProperties = ['targetGainRatio', 'attackMs', 'releaseMs'].some((key) => Object.hasOwn(record, key));
      if (usageType === 'background_music') {
        assertRequiredKeys(record, ['targetGainRatio', 'attackMs', 'releaseMs'], 'audio');
        if (record.volumeRatio !== 0.3 || record.loopWhenOverflow !== true || record.duckingEnabled !== true || record.targetGainRatio !== 0.35 || record.attackMs !== 120 || record.releaseMs !== 400) throw new Error('Invalid wire response: background music invariants are invalid');
      } else if (hasDuckingProperties || record.loopWhenOverflow !== false || record.duckingEnabled !== false) {
        throw new Error('Invalid wire response: non-background audio invariants are invalid');
      }
      break;
    }
    case 'visual_effect': {
      const effectCode = readEnum(record, 'effectCode', ['fade_in', 'fade_out', 'gentle_zoom_in', 'gentle_zoom_out', 'light_blur'] as const);
      readInteger(record, 'durationMs', 100, 3000);
      const scale = readNullableNumber(record, 'scale', 1, 1.2);
      const radius = readNullableNumber(record, 'radius', 0.5, 12);
      if ((effectCode.startsWith('fade') && (scale !== null || radius !== null)) || (effectCode.startsWith('gentle_zoom') && (scale === null || radius !== null)) || (effectCode === 'light_blur' && (scale !== null || radius === null))) throw new Error('Invalid wire response: visual effect parameters do not match effectCode');
      break;
    }
  }
  return record as TimelineElement;
}

function parseTrack(value: unknown, index: number): TimelineTrack {
  const record = assertRecord(value, `tracks[${index}]`);
  assertOnlyKnownKeys(record, ['trackId', 'trackType', 'area', 'order', 'locked', 'muted', 'elements'], `tracks[${index}]`);
  const trackType = readEnum(record, 'trackType', trackTypes);
  const area = readEnum(record, 'area', ['top', 'center', 'bottom'] as const);
  if (area !== areaByTrack[trackType]) throw new Error('Invalid wire response: track area does not match trackType');
  const order = readInteger(record, 'order', 0, 31);
  const locked = readBoolean(record, 'locked');
  const elements = readArray(record, 'elements', parseElement);
  if (elements.length === 0) throw new Error('Invalid wire response: track elements must not be empty');
  const expectedElementType = expectedElementTypeByTrack[trackType];
  if (
    elements.some((element) =>
      element.elementType !== expectedElementType ||
      (element.elementType === 'audio' && element.usageType !== trackType),
    )
  ) {
    throw new Error('Invalid wire response: trackType does not match element usage');
  }
  if (trackType === 'main_video' && (order !== 0 || !locked)) throw new Error('Invalid wire response: main_video track invariants are invalid');
  return { trackId: readKey(record, 'trackId'), trackType, area, order, locked, muted: readBoolean(record, 'muted'), elements };
}

function parseTimeline(value: unknown): TimelineDocument {
  const record = assertRecord(value, 'timeline');
  assertOnlyKnownKeys(record, ['schemaVersion', 'canvas', 'tracks'], 'timeline');
  if (readString(record, 'schemaVersion') !== 'timeline-1') throw new Error('Invalid wire response: timeline schemaVersion is unsupported');
  const canvas = parseCanvas(record.canvas, true) as TimelineCanvas;
  const tracks = readArray(record, 'tracks', parseTrack);
  if (tracks.length === 0 || tracks.length > 32) throw new Error('Invalid wire response: timeline track count is invalid');
  if (tracks.filter((track) => track.trackType === 'main_video').length !== 1) throw new Error('Invalid wire response: timeline must contain exactly one main_video track');
  const trackIds = new Set(tracks.map((track) => track.trackId));
  const elements = tracks.flatMap((track) => track.elements);
  if (trackIds.size !== tracks.length || new Set(elements.map((element) => element.elementId)).size !== elements.length || elements.length > 2000) throw new Error('Invalid wire response: timeline identifiers or element count are invalid');
  if (elements.some((element) => element.endMs > canvas.durationMs)) throw new Error('Invalid wire response: element exceeds canvas duration');
  return { schemaVersion: 'timeline-1', canvas, tracks };
}

export function parseCreationProjectWire(value: unknown): CreationProject {
  const record = assertRecord(value, 'creationProject');
  assertOnlyKnownKeys(record, ['projectId', 'projectTitle', 'sourceType', 'sourceId', 'baseVideoAssetId', 'primaryAudioAssetId', 'status', 'canvas', 'currentDraftRevision', 'schemaVersion', 'latestOutputAssetId', 'createdAt', 'updatedAt'], 'creationProject');
  if (readString(record, 'schemaVersion') !== 'timeline-1') throw new Error('Invalid wire response: schemaVersion is unsupported');
  const primaryAudioAssetId = readOptionalPositiveDecimalId(record, 'primaryAudioAssetId');
  const latestOutputAssetId = readOptionalPositiveDecimalId(record, 'latestOutputAssetId');
  return { projectId: asDecimalId(readPositiveDecimalId(record, 'projectId')) as CreationProjectId, projectTitle: readString(record, 'projectTitle'), sourceType: readEnum(record, 'sourceType', ['digital_human_job'] as const), sourceId: asDecimalId(readPositiveDecimalId(record, 'sourceId')), baseVideoAssetId: asDecimalId(readPositiveDecimalId(record, 'baseVideoAssetId')), ...(primaryAudioAssetId ? { primaryAudioAssetId } : {}), status: readEnum(record, 'status', ['editing', 'rendering', 'ready', 'archived'] as const), canvas: parseCanvas(record.canvas, false) as CreationProject['canvas'], currentDraftRevision: readPositiveDecimalId(record, 'currentDraftRevision') as CreationProject['currentDraftRevision'], schemaVersion: 'timeline-1', ...(latestOutputAssetId ? { latestOutputAssetId } : {}), createdAt: readString(record, 'createdAt'), updatedAt: readString(record, 'updatedAt') };
}

export function parseCreationOutputWire(value: unknown): CreationOutput {
  const record = assertRecord(value, 'creationOutput');
  const fields = ['projectId', 'outputAssetId', 'taskId', 'createdAt'];
  assertOnlyKnownKeys(record, fields, 'creationOutput');
  assertRequiredKeys(record, fields, 'creationOutput');
  return {
    projectId: asDecimalId(readPositiveDecimalId(record, 'projectId')) as CreationProjectId,
    outputAssetId: asDecimalId(readPositiveDecimalId(record, 'outputAssetId')),
    taskId: asDecimalId(readPositiveDecimalId(record, 'taskId')),
    createdAt: readString(record, 'createdAt'),
  };
}

export function parseTimelineDraftWire(value: unknown): TimelineDraft {
  const record = assertRecord(value, 'timelineDraft');
  assertOnlyKnownKeys(record, ['projectId', 'timelineDraftId', 'revision', 'schemaVersion', 'contentHash', 'timeline', 'savedAt'], 'timelineDraft');
  if (readString(record, 'schemaVersion') !== 'timeline-1') throw new Error('Invalid wire response: schemaVersion is unsupported');
  return { projectId: asDecimalId(readPositiveDecimalId(record, 'projectId')) as CreationProjectId, timelineDraftId: asDecimalId(readPositiveDecimalId(record, 'timelineDraftId')), revision: readPositiveDecimalId(record, 'revision') as TimelineDraft['revision'], schemaVersion: 'timeline-1', contentHash: readString(record, 'contentHash'), timeline: parseTimeline(record.timeline), savedAt: readString(record, 'savedAt') };
}

export function parseTimelineVersionWire(value: unknown): TimelineVersion {
  const record = assertRecord(value, 'timelineVersion');
  const allowed = ['versionId', 'projectId', 'versionNo', 'sourceDraftRevision', 'schemaVersion', 'contentHash', 'versionReason', 'sourceVersionId', 'createdAt', 'replayed'];
  assertOnlyKnownKeys(record, allowed, 'timelineVersion');
  assertRequiredKeys(record, ['versionId', 'projectId', 'versionNo', 'sourceDraftRevision', 'schemaVersion', 'contentHash', 'versionReason', 'createdAt'], 'timelineVersion');
  if (readString(record, 'schemaVersion') !== 'timeline-1') throw new Error('Invalid wire response: schemaVersion is unsupported');
  const sourceVersionId = readOptionalPositiveDecimalId(record, 'sourceVersionId');
  const replayed = Object.hasOwn(record, 'replayed') ? readBoolean(record, 'replayed') : undefined;
  return {
    versionId: asDecimalId(readPositiveDecimalId(record, 'versionId')),
    projectId: asDecimalId(readPositiveDecimalId(record, 'projectId')) as CreationProjectId,
    versionNo: readPositiveDecimalId(record, 'versionNo'),
    sourceDraftRevision: readPositiveDecimalId(record, 'sourceDraftRevision') as TimelineVersion['sourceDraftRevision'],
    schemaVersion: 'timeline-1',
    contentHash: readString(record, 'contentHash'),
    versionReason: readEnum(record, 'versionReason', ['manual_save', 'restored', 'render_input', 'conflict_copy'] as const),
    ...(sourceVersionId ? { sourceVersionId } : {}),
    createdAt: readString(record, 'createdAt'),
    ...(replayed === undefined ? {} : { replayed }),
  };
}

const timelineTaskTypes = [
  'timeline_image_prompt_generate',
  'timeline_fancy_text_suggest',
  'timeline_subtitle_align',
  'timeline_render',
] as const;

function readOptionalPositiveDecimalId(record: WireRecord, key: string): DecimalId | undefined {
  if (!Object.hasOwn(record, key)) return undefined;
  return asDecimalId(readPositiveDecimalId(record, key));
}

function readOptionalWireString(record: WireRecord, key: string): string | undefined {
  if (!Object.hasOwn(record, key)) return undefined;
  return readString(record, key);
}

function parseTimelineTaskCommon(value: unknown): { record: WireRecord; common: TimelineTaskCommon } {
  const record = assertRecord(value, 'timelineTask');
  const task = parseAiTaskVOWire(record);
  if (task.resourceType !== 'creation_project') {
    throw new Error('Invalid wire response: resourceType must be creation_project');
  }
  if (!timelineTaskTypes.includes(task.taskType as (typeof timelineTaskTypes)[number])) {
    throw new Error('Invalid wire response: taskType must be a supported timeline task');
  }
  const common: TimelineTaskCommon = {
    taskId: asDecimalId(task.taskId),
    taskType: task.taskType,
    resourceType: 'creation_project',
    resourceId: asDecimalId(task.resourceId) as CreationProjectId,
    status: task.status,
    stage: task.stage,
    progress: task.progress,
    canCancel: task.canCancel,
    canRetry: task.canRetry,
    createdAt: task.createdAt,
  };
  if (task.inputVersionId) common.inputVersionId = asDecimalId(task.inputVersionId);
  if (task.errorCode) common.errorCode = task.errorCode;
  if (task.errorSummary) common.errorSummary = task.errorSummary;
  if (task.startedAt) common.startedAt = task.startedAt;
  if (task.finishedAt) common.finishedAt = task.finishedAt;
  return { record, common };
}

function parseTaskResult(record: WireRecord, taskType: string): unknown {
  if (!Object.hasOwn(record, 'result')) return undefined;
  const result = assertRecord(record.result, 'result');
  const taskId = asDecimalId(readPositiveDecimalId(result, 'taskId'));
  if (taskType === 'timeline_image_prompt_generate') {
    assertOnlyKnownKeys(result, ['taskId', 'suggestions'], 'imagePromptResult');
    return {
      taskId,
      suggestions: readArray(result, 'suggestions', (item) => {
        const suggestion = assertRecord(item, 'imagePromptSuggestion');
        assertOnlyKnownKeys(suggestion, ['prompt', 'negativePrompt', 'styleTags', 'reason'], 'imagePromptSuggestion');
        assertRequiredKeys(suggestion, ['prompt', 'negativePrompt', 'styleTags', 'reason'], 'imagePromptSuggestion');
        return {
          prompt: readString(suggestion, 'prompt'),
          negativePrompt: readString(suggestion, 'negativePrompt'),
          styleTags: readArray(suggestion, 'styleTags', (tag) => {
            if (typeof tag !== 'string') throw new Error('Invalid wire response: styleTags must contain strings');
            return tag;
          }),
          reason: readString(suggestion, 'reason'),
        };
      }),
    };
  }
  if (taskType === 'timeline_fancy_text_suggest') {
    assertOnlyKnownKeys(result, ['taskId', 'suggestions'], 'fancyTextResult');
    return {
      taskId,
      suggestions: readArray(result, 'suggestions', (item) => {
        const suggestion = assertRecord(item, 'fancyTextSuggestion');
        assertOnlyKnownKeys(suggestion, ['sourceText', 'sourceStartOffset', 'sourceEndOffset', 'startMs', 'durationMs', 'templateCode', 'xRatio', 'yRatio', 'primaryColor', 'accentColor', 'reason'], 'fancyTextSuggestion');
        assertRequiredKeys(suggestion, ['sourceText', 'sourceStartOffset', 'sourceEndOffset', 'startMs', 'durationMs', 'templateCode', 'xRatio', 'yRatio', 'primaryColor', 'accentColor', 'reason'], 'fancyTextSuggestion');
        return {
          sourceText: readString(suggestion, 'sourceText'),
          sourceStartOffset: readInteger(suggestion, 'sourceStartOffset', 0, 50000),
          sourceEndOffset: readInteger(suggestion, 'sourceEndOffset', 0, 50000),
          startMs: readInteger(suggestion, 'startMs', 0, 119999),
          durationMs: readInteger(suggestion, 'durationMs', 1, 120000),
          templateCode: readEnum(suggestion, 'templateCode', ['keyword_pop', 'gold_impact', 'neon_breathe', 'handwriting_reveal', 'bubble_bounce', 'title_wipe'] as const),
          xRatio: readNumber(suggestion, 'xRatio', 0, 1),
          yRatio: readNumber(suggestion, 'yRatio', 0, 1),
          primaryColor: readColor(suggestion, 'primaryColor'),
          accentColor: readColor(suggestion, 'accentColor'),
          reason: readString(suggestion, 'reason'),
        };
      }),
    };
  }
  if (taskType === 'timeline_subtitle_align') {
    assertOnlyKnownKeys(result, ['taskId', 'sourceType', 'subtitles'], 'subtitleAlignmentResult');
    return {
      taskId,
      sourceType: readEnum(result, 'sourceType', ['trusted_cue', 'whisper'] as const),
      subtitles: readArray(result, 'subtitles', (item) => {
        const subtitle = assertRecord(item, 'alignedSubtitle');
        assertOnlyKnownKeys(subtitle, ['sourceStartOffset', 'sourceEndOffset', 'displayText', 'startMs', 'endMs'], 'alignedSubtitle');
        assertRequiredKeys(subtitle, ['sourceStartOffset', 'sourceEndOffset', 'displayText', 'startMs', 'endMs'], 'alignedSubtitle');
        return {
          sourceStartOffset: readInteger(subtitle, 'sourceStartOffset', 0, 50000),
          sourceEndOffset: readInteger(subtitle, 'sourceEndOffset', 0, 50000),
          displayText: readString(subtitle, 'displayText'),
          startMs: readInteger(subtitle, 'startMs', 0, 119999),
          endMs: readInteger(subtitle, 'endMs', 1, 120000),
        };
      }),
    };
  }
  return undefined;
}

export function parseTimelineTaskDetailWire(value: unknown): TimelineTaskDetail {
  const { record, common } = parseTimelineTaskCommon(value);
  const resultAssetId = readOptionalPositiveDecimalId(record, 'resultAssetId');
  const resultSchemaVersion = readOptionalWireString(record, 'resultSchemaVersion');
  const result = parseTaskResult(record, common.taskType);
  if (common.status === 'success') {
    const requiresAsset = common.taskType === 'timeline_render';
    if (requiresAsset ? !resultAssetId || Object.hasOwn(record, 'result') : !result || resultAssetId) {
      throw new Error('Invalid wire response: successful timeline task has an invalid result channel');
    }
  }
  if (common.status !== 'success' && (resultAssetId || Object.hasOwn(record, 'result'))) {
    throw new Error('Invalid wire response: incomplete timeline task must not expose a result');
  }
  switch (common.taskType) {
    case 'timeline_image_prompt_generate':
      return { ...common, kind: 'image-prompt', taskType: common.taskType, ...(result ? { result } : {}) } as TimelineTaskDetail;
    case 'timeline_fancy_text_suggest':
      return { ...common, kind: 'fancy-text', taskType: common.taskType, ...(result ? { result } : {}) } as TimelineTaskDetail;
    case 'timeline_subtitle_align':
      return { ...common, kind: 'subtitle-alignment', taskType: common.taskType, ...(result ? { result } : {}) } as TimelineTaskDetail;
    case 'timeline_render':
      return { ...common, kind: 'render', taskType: common.taskType, ...(resultAssetId ? { resultAssetId } : {}), ...(resultSchemaVersion ? { resultSchemaVersion } : {}) };
    default:
      throw new Error('Invalid wire response: taskType must be a supported timeline task');
  }
}

export function parseTimelineTaskListItemWire(value: unknown): TimelineTaskListItem {
  const { common } = parseTimelineTaskCommon(value);
  const kindByTaskType: Record<string, TimelineTaskListItem['kind']> = {
    timeline_image_prompt_generate: 'image-prompt',
    timeline_fancy_text_suggest: 'fancy-text',
    timeline_subtitle_align: 'subtitle-alignment',
    timeline_render: 'render',
  };
  return { ...common, kind: kindByTaskType[common.taskType] };
}
