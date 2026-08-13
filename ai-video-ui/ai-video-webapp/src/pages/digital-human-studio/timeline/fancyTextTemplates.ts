import type {
  FancyTextElement,
  TimelineTransform,
} from '@/services/ai-video/creation-timeline/types';

export type FancyTextTemplate = {
  code: FancyTextElement['templateCode'];
  label: string;
  previewDescription: string;
};

export type RegisteredTimelineFont = {
  fontCode: FancyTextElement['fontCode'];
  familyName: string;
  version: string;
  fileName: string;
  sha256: string;
};

export type TimelineFontStatus =
  | { status: 'ready'; fonts: RegisteredTimelineFont[] }
  | { status: 'font-invalid'; reason: string };

export const FANCY_TEXT_TEMPLATES: readonly FancyTextTemplate[] = [
  {
    code: 'keyword_pop',
    label: '关键词弹入',
    previewDescription: '放大回弹',
  },
  {
    code: 'gold_impact',
    label: '金色冲击',
    previewDescription: '金色描边与短闪',
  },
  {
    code: 'neon_breathe',
    label: '霓虹呼吸',
    previewDescription: '渐变发光',
  },
  {
    code: 'handwriting_reveal',
    label: '手写描边',
    previewDescription: '方向揭示',
  },
  {
    code: 'bubble_bounce',
    label: '气泡弹跳',
    previewDescription: '轻弹',
  },
  {
    code: 'title_wipe',
    label: '标题横扫',
    previewDescription: '横向遮罩',
  },
];

export const FIXED_TIMELINE_FONTS: readonly RegisteredTimelineFont[] = [
  {
    fontCode: 'noto_sans_cjk_sc_regular',
    familyName: 'Noto Sans CJK SC',
    version: '2.004',
    fileName: 'NotoSansCJKsc-Regular.otf',
    sha256: '2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b',
  },
  {
    fontCode: 'noto_serif_cjk_sc_regular',
    familyName: 'Noto Serif CJK SC',
    version: '2.003',
    fileName: 'NotoSerifCJKsc-Regular.otf',
    sha256: '2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca',
  },
];

type FontFetchResponse = {
  ok: boolean;
  json: () => Promise<unknown>;
  arrayBuffer: () => Promise<ArrayBuffer>;
};

type FontFaceLike = { load: () => Promise<unknown> };

export type LoadTimelineFontsOptions = {
  fetcher?: (url: string) => Promise<FontFetchResponse>;
  digest?: (bytes: ArrayBuffer) => Promise<ArrayBuffer>;
  createFontFace?: (familyName: string, bytes: ArrayBuffer) => FontFaceLike;
  fontSet?: { add: (font: FontFaceLike) => unknown };
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isFontCode(value: unknown): value is FancyTextElement['fontCode'] {
  return (
    value === 'noto_sans_cjk_sc_regular' ||
    value === 'noto_serif_cjk_sc_regular'
  );
}

function toHex(bytes: ArrayBuffer): string {
  return Array.from(new Uint8Array(bytes), (byte) =>
    byte.toString(16).padStart(2, '0'),
  ).join('');
}

function defaultFetcher(url: string): Promise<FontFetchResponse> {
  return fetch(url);
}

function defaultDigest(bytes: ArrayBuffer): Promise<ArrayBuffer> {
  return crypto.subtle.digest('SHA-256', bytes);
}

function defaultFontFaceFactory(
  familyName: string,
  bytes: ArrayBuffer,
): FontFaceLike {
  if (typeof FontFace === 'undefined') {
    throw new Error('FontFace is unavailable');
  }
  return new FontFace(familyName, bytes);
}

function defaultFontSet(): { add: (font: FontFaceLike) => unknown } {
  if (typeof document === 'undefined' || !document.fonts) {
    throw new Error('FontFaceSet is unavailable');
  }
  return document.fonts as unknown as { add: (font: FontFaceLike) => unknown };
}

export function getFancyTextTemplate(code: string): FancyTextTemplate {
  const template = FANCY_TEXT_TEMPLATES.find(
    (candidate) => candidate.code === code,
  );
  if (!template) {
    throw new Error('Unknown fancy text template');
  }
  return template;
}

export function parseTimelineFontRegistry(
  value: unknown,
): RegisteredTimelineFont[] {
  if (!isRecord(value) || value.registryVersion !== 'timeline-fonts-1') {
    throw new Error('Invalid timeline font registry');
  }
  if (!Array.isArray(value.fonts) || value.fonts.length !== 2) {
    throw new Error('Invalid timeline font registry');
  }
  const fonts = value.fonts.map((font): RegisteredTimelineFont => {
    if (
      !isRecord(font) ||
      !isFontCode(font.fontCode) ||
      typeof font.familyName !== 'string' ||
      typeof font.version !== 'string' ||
      typeof font.fileName !== 'string' ||
      typeof font.sha256 !== 'string' ||
      !/^[0-9a-f]{64}$/.test(font.sha256)
    ) {
      throw new Error('Invalid timeline font registry');
    }
    return {
      fontCode: font.fontCode,
      familyName: font.familyName,
      version: font.version,
      fileName: font.fileName,
      sha256: font.sha256,
    };
  });
  const codes = new Set(fonts.map((font) => font.fontCode));
  if (codes.size !== 2) throw new Error('Invalid timeline font registry');
  return fonts;
}

export function fontMatchesRegistry(
  element: Pick<FancyTextElement, 'fontCode' | 'fontVersion' | 'fontSha256'>,
  fonts: readonly RegisteredTimelineFont[] = FIXED_TIMELINE_FONTS,
): boolean {
  return fonts.some(
    (font) =>
      font.fontCode === element.fontCode &&
      font.version === element.fontVersion &&
      font.sha256 === element.fontSha256,
  );
}

export function fixedTimelineFontFor(
  fontCode: FancyTextElement['fontCode'],
): RegisteredTimelineFont {
  const font = FIXED_TIMELINE_FONTS.find(
    (candidate) => candidate.fontCode === fontCode,
  );
  if (!font) throw new Error('Unknown timeline font');
  return font;
}

function matchesFixedFontRegistry(
  fonts: readonly RegisteredTimelineFont[],
): boolean {
  return (
    fonts.length === FIXED_TIMELINE_FONTS.length &&
    FIXED_TIMELINE_FONTS.every((expected) =>
      fonts.some(
        (actual) =>
          actual.fontCode === expected.fontCode &&
          actual.familyName === expected.familyName &&
          actual.version === expected.version &&
          actual.fileName === expected.fileName &&
          actual.sha256 === expected.sha256,
      ),
    )
  );
}

export function isValidTimelineColor(value: string): boolean {
  return /^#[0-9A-F]{8}$/.test(value);
}

function isValidTransform(transform: TimelineTransform): boolean {
  return (
    transform.xRatio >= 0 &&
    transform.yRatio >= 0 &&
    transform.widthRatio > 0 &&
    transform.heightRatio > 0 &&
    transform.xRatio + transform.widthRatio <= 1 &&
    transform.yRatio + transform.heightRatio <= 1 &&
    transform.rotationDeg >= -180 &&
    transform.rotationDeg <= 180 &&
    transform.opacity >= 0 &&
    transform.opacity <= 1
  );
}

export function validateFancyTextElement(
  element: FancyTextElement,
): string | undefined {
  try {
    getFancyTextTemplate(element.templateCode);
  } catch {
    return '花字模板不在允许列表中';
  }
  const textLength = Array.from(element.text.normalize('NFC')).length;
  if (textLength < 1 || textLength > 128) {
    return '花字文字必须在 1 到 128 个字符之间';
  }
  if (
    !isValidTimelineColor(element.color) ||
    !isValidTimelineColor(element.accentColor)
  ) {
    return '颜色必须为 #RRGGBBAA 大写格式';
  }
  if (!fontMatchesRegistry(element)) {
    return '字体不可用，已阻止保存和合成。';
  }
  if (!['subtle', 'normal', 'strong'].includes(element.animationIntensity)) {
    return '动画强度不在允许列表中';
  }
  if (
    !Number.isInteger(element.enterDurationMs) ||
    !Number.isInteger(element.exitDurationMs) ||
    element.enterDurationMs < 0 ||
    element.exitDurationMs < 0 ||
    element.enterDurationMs > 3_000 ||
    element.exitDurationMs > 3_000
  ) {
    return '入场和退场时长必须在 0 到 3000 毫秒之间';
  }
  if (!isValidTransform(element.transform)) {
    return '花字位置必须完全落在画布内';
  }
  return undefined;
}

export async function loadTimelineFonts(
  options: LoadTimelineFontsOptions = {},
): Promise<TimelineFontStatus> {
  const fetcher = options.fetcher ?? defaultFetcher;
  const digest = options.digest ?? defaultDigest;
  const createFontFace = options.createFontFace ?? defaultFontFaceFactory;
  try {
    const registryResponse = await fetcher(
      '/timeline-fonts/font-registry.json',
    );
    if (!registryResponse.ok) {
      return { status: 'font-invalid', reason: 'registry-unavailable' };
    }
    const fonts = parseTimelineFontRegistry(await registryResponse.json());
    if (!matchesFixedFontRegistry(fonts)) {
      return { status: 'font-invalid', reason: 'registry-mismatch' };
    }
    const fontSet = options.fontSet ?? defaultFontSet();
    for (const font of fonts) {
      const fontResponse = await fetcher(`/timeline-fonts/${font.fileName}`);
      if (!fontResponse.ok) {
        return { status: 'font-invalid', reason: 'font-unavailable' };
      }
      const bytes = await fontResponse.arrayBuffer();
      if (toHex(await digest(bytes)) !== font.sha256) {
        return { status: 'font-invalid', reason: 'hash-mismatch' };
      }
      const face = createFontFace(font.familyName, bytes);
      await face.load();
      fontSet.add(face);
    }
    return { status: 'ready', fonts };
  } catch {
    return { status: 'font-invalid', reason: 'font-load-failed' };
  }
}
