import type {
  SubtitleElement,
  TimelineDocument,
} from '@/services/ai-video/creation-timeline/types';
import { fontMatchesRegistry } from './fancyTextTemplates';

export const SUBTITLE_FONT_CODES = [
  'noto_sans_cjk_sc_regular',
  'noto_serif_cjk_sc_regular',
] as const;

export type SubtitleFontCode = (typeof SUBTITLE_FONT_CODES)[number];
export type SubtitlePlacement = Pick<
  SubtitleElement,
  'safeAreaAnchor' | 'alignment'
>;

export type SubtitleIntegritySegment = Pick<
  SubtitleElement,
  'sourceStartOffset' | 'sourceEndOffset' | 'displayText'
>;

export type SubtitleIntegrityResult =
  | { valid: true }
  | { valid: false; reason: 'display-text' | 'source-ranges' };

const REMOVABLE_PUNCTUATION = new Set([
  '，',
  '。',
  '！',
  '？',
  '；',
  '：',
  '、',
  ',',
  '.',
  '!',
  '?',
  ';',
  ':',
  '-',
  '—',
  '…',
  '（',
  '）',
  '(',
  ')',
  '【',
  '】',
  '[',
  ']',
  '{',
  '}',
]);

const DEFAULT_BACKGROUND_COLOR = '#00000080';
const DEFAULT_OUTLINE_COLOR = '#000000FF';

function unicodeCodePoints(value: string): string[] {
  return Array.from(value.normalize('NFC'));
}

function isRemovableSubtitleCharacter(character: string): boolean {
  return /\s/u.test(character) || REMOVABLE_PUNCTUATION.has(character);
}

export function codePointLength(value: string): number {
  return unicodeCodePoints(value).length;
}

export function normalizeSubtitleText(source: string): string {
  return unicodeCodePoints(source)
    .filter((character) => !isRemovableSubtitleCharacter(character))
    .join('');
}

export function validateSubtitleIntegrity(
  projectScript: string,
  segments: readonly SubtitleIntegritySegment[],
): SubtitleIntegrityResult {
  const scriptCodePoints = unicodeCodePoints(projectScript);
  let expectedOffset = 0;
  let concatenatedDisplayText = '';

  for (const segment of segments) {
    if (
      !Number.isInteger(segment.sourceStartOffset) ||
      !Number.isInteger(segment.sourceEndOffset) ||
      segment.sourceStartOffset !== expectedOffset ||
      segment.sourceEndOffset <= segment.sourceStartOffset ||
      segment.sourceEndOffset > scriptCodePoints.length
    ) {
      return { valid: false, reason: 'source-ranges' };
    }

    if (
      !segment.displayText ||
      unicodeCodePoints(segment.displayText).some(isRemovableSubtitleCharacter)
    ) {
      return { valid: false, reason: 'display-text' };
    }

    const normalizedSourceSlice = normalizeSubtitleText(
      scriptCodePoints
        .slice(segment.sourceStartOffset, segment.sourceEndOffset)
        .join(''),
    );
    if (normalizedSourceSlice !== segment.displayText) {
      return { valid: false, reason: 'display-text' };
    }

    concatenatedDisplayText += segment.displayText;
    expectedOffset = segment.sourceEndOffset;
  }

  if (
    expectedOffset !== scriptCodePoints.length ||
    concatenatedDisplayText !== normalizeSubtitleText(projectScript)
  ) {
    return { valid: false, reason: 'source-ranges' };
  }

  return { valid: true };
}

export function defaultSubtitlePlacement(): SubtitlePlacement {
  return { safeAreaAnchor: 'lower', alignment: 'center' };
}

export function mapSubtitlePointerToPlacement({
  xRatio,
  yRatio,
  safeMarginRatio,
}: {
  xRatio: number;
  yRatio: number;
  safeMarginRatio: number;
}): SubtitlePlacement {
  const safeMaximum = 1 - safeMarginRatio;
  if (
    !Number.isFinite(xRatio) ||
    !Number.isFinite(yRatio) ||
    !Number.isFinite(safeMarginRatio) ||
    safeMarginRatio < 0 ||
    safeMarginRatio >= 0.5 ||
    xRatio < safeMarginRatio ||
    xRatio > safeMaximum ||
    yRatio < safeMarginRatio ||
    yRatio > safeMaximum
  ) {
    throw new Error('Subtitle placement must remain inside the safe area');
  }

  const safeRange = safeMaximum - safeMarginRatio;
  const normalizedX = (xRatio - safeMarginRatio) / safeRange;
  const normalizedY = (yRatio - safeMarginRatio) / safeRange;
  return {
    safeAreaAnchor:
      normalizedY < 1 / 3 ? 'upper' : normalizedY > 2 / 3 ? 'lower' : 'center',
    alignment:
      normalizedX < 1 / 3 ? 'left' : normalizedX > 2 / 3 ? 'right' : 'center',
  };
}

export function isValidSubtitleColor(value: string | undefined): boolean {
  return /^#[0-9A-F]{8}$/.test(value ?? '');
}

export function sanitizeSubtitleElement(
  element: SubtitleElement,
): SubtitleElement {
  const sanitized: SubtitleElement = {
    elementId: element.elementId,
    elementType: 'subtitle',
    startMs: element.startMs,
    endMs: element.endMs,
    zIndex: element.zIndex,
    enabled: element.enabled,
    locked: element.locked,
    label: element.label,
    sourceTextSnapshot: element.sourceTextSnapshot,
    displayText: element.displayText,
    sourceStartOffset: element.sourceStartOffset,
    sourceEndOffset: element.sourceEndOffset,
    fontCode: element.fontCode,
    fontVersion: element.fontVersion,
    fontSha256: element.fontSha256,
    fontSizePx: element.fontSizePx,
    color: element.color,
    backgroundEnabled: element.backgroundEnabled,
    outlineEnabled: element.outlineEnabled,
    outlineWidthPx: element.outlineEnabled ? element.outlineWidthPx : 0,
    safeAreaAnchor: element.safeAreaAnchor,
    alignment: element.alignment,
  };
  if (element.backgroundEnabled) {
    sanitized.backgroundColor =
      element.backgroundColor ?? DEFAULT_BACKGROUND_COLOR;
  }
  if (element.outlineEnabled) {
    sanitized.outlineColor = element.outlineColor ?? DEFAULT_OUTLINE_COLOR;
  }
  return sanitized;
}

export function validateSubtitleStyle(
  element: SubtitleElement,
): string | undefined {
  if (!SUBTITLE_FONT_CODES.includes(element.fontCode as SubtitleFontCode)) {
    return '字体不在允许的字幕字体列表中';
  }
  if (!fontMatchesRegistry(element)) {
    return '字体与固定登记表不匹配，已阻止保存。';
  }
  if (
    !Number.isInteger(element.fontSizePx) ||
    element.fontSizePx < 12 ||
    element.fontSizePx > 120
  ) {
    return '字体大小必须在 12 到 120 之间';
  }
  if (!isValidSubtitleColor(element.color)) {
    return '颜色必须为 #RRGGBBAA 大写格式';
  }
  if (
    element.backgroundEnabled &&
    !isValidSubtitleColor(element.backgroundColor)
  ) {
    return '背景颜色必须为 #RRGGBBAA 大写格式';
  }
  if (element.outlineEnabled && !isValidSubtitleColor(element.outlineColor)) {
    return '描边颜色必须为 #RRGGBBAA 大写格式';
  }
  if (
    !Number.isInteger(element.outlineWidthPx) ||
    element.outlineWidthPx < 0 ||
    element.outlineWidthPx > 8
  ) {
    return '描边宽度必须在 0 到 8 之间';
  }
  return undefined;
}

export function subtitleWouldOverflow({
  displayText,
  fontSizePx,
  safeAreaWidthPx,
  measureText,
}: {
  displayText: string;
  fontSizePx: number;
  safeAreaWidthPx: number;
  measureText: (text: string, fontSizePx: number) => number;
}): boolean {
  return measureText(displayText, fontSizePx) > safeAreaWidthPx;
}

export function applySubtitleServerNormalization(
  timeline: TimelineDocument,
  normalizationChanges: readonly { elementId: string }[],
): {
  timeline: TimelineDocument;
  normalizedElementIds: Set<string>;
} {
  return {
    timeline,
    normalizedElementIds: new Set(
      normalizationChanges.map((change) => change.elementId),
    ),
  };
}
