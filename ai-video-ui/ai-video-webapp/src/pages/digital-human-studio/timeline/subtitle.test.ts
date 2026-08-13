import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  codePointLength,
  defaultSubtitlePlacement,
  mapSubtitlePointerToPlacement,
  normalizeSubtitleText,
  validateSubtitleStyle,
  validateSubtitleIntegrity,
} from './subtitle';
import { fixedTimelineFontFor } from './fancyTextTemplates';
import type { SubtitleElement } from '@/services/ai-video/creation-timeline/types';

const contractDirectory = resolve(
  process.cwd(),
  '../../docs/contracts/creation-timeline',
);

describe('subtitle normalization', () => {
  it('reads the C0 fixture and normalizes NFC text by Unicode code point', async () => {
    const fixture = JSON.parse(
      await readFile(
        resolve(contractDirectory, 'subtitle-normalization.example.json'),
        'utf8',
      ),
    ) as {
      examples: Array<{ source: string; normalized: string }>;
    };

    for (const example of fixture.examples) {
      expect(normalizeSubtitleText(example.source)).toBe(example.normalized);
    }
    expect(normalizeSubtitleText('Cafe\u0301, 😀 3.14%')).toBe('Café😀314%');
    expect(codePointLength('Café😀')).toBe(5);
  });

  it('requires contiguous Unicode-code-point source ranges with no omission, duplication, blank display, or line break', () => {
    const script = 'A B，C';
    const valid = [
      { sourceStartOffset: 0, sourceEndOffset: 3, displayText: 'AB' },
      { sourceStartOffset: 3, sourceEndOffset: 5, displayText: 'C' },
    ];

    expect(validateSubtitleIntegrity(script, valid)).toEqual({ valid: true });
    expect(
      validateSubtitleIntegrity(script, [
        { sourceStartOffset: 0, sourceEndOffset: 3, displayText: 'AB' },
        { sourceStartOffset: 4, sourceEndOffset: 5, displayText: 'C' },
      ]),
    ).toMatchObject({ valid: false, reason: 'source-ranges' });
    expect(
      validateSubtitleIntegrity(script, [
        { sourceStartOffset: 0, sourceEndOffset: 3, displayText: 'A\nB' },
        { sourceStartOffset: 3, sourceEndOffset: 5, displayText: 'C' },
      ]),
    ).toMatchObject({ valid: false, reason: 'display-text' });
    expect(
      validateSubtitleIntegrity(script, [
        { sourceStartOffset: 0, sourceEndOffset: 3, displayText: 'AB' },
        { sourceStartOffset: 0, sourceEndOffset: 5, displayText: 'ABC' },
      ]),
    ).toMatchObject({ valid: false, reason: 'source-ranges' });
    expect(
      validateSubtitleIntegrity(script, [
        { sourceStartOffset: 3, sourceEndOffset: 5, displayText: 'C' },
        { sourceStartOffset: 0, sourceEndOffset: 3, displayText: 'AB' },
      ]),
    ).toMatchObject({ valid: false, reason: 'source-ranges' });
  });

  it('maps subtitle placement to the C0 anchor and alignment fields without adding a transform', () => {
    expect(defaultSubtitlePlacement()).toEqual({
      safeAreaAnchor: 'lower',
      alignment: 'center',
    });
    expect(
      mapSubtitlePointerToPlacement({
        xRatio: 0.5,
        yRatio: 0.82,
        safeMarginRatio: 0.05,
      }),
    ).toEqual({ safeAreaAnchor: 'lower', alignment: 'center' });
    expect(() =>
      mapSubtitlePointerToPlacement({
        xRatio: 0.02,
        yRatio: 0.5,
        safeMarginRatio: 0.05,
      }),
    ).toThrow('safe area');
  });

  it('rejects a mixed font registry triple before subtitle save validation', () => {
    const sans = fixedTimelineFontFor('noto_sans_cjk_sc_regular');
    const serif = fixedTimelineFontFor('noto_serif_cjk_sc_regular');
    const subtitle: SubtitleElement = {
      elementId: 'subtitle-font',
      elementType: 'subtitle',
      startMs: 0,
      endMs: 1_000,
      zIndex: 0,
      enabled: true,
      locked: false,
      label: 'subtitle-font',
      sourceTextSnapshot: '字体',
      displayText: '字体',
      sourceStartOffset: 0,
      sourceEndOffset: 2,
      fontCode: sans.fontCode,
      fontVersion: sans.version,
      fontSha256: sans.sha256,
      fontSizePx: 42,
      color: '#FFFFFFFF',
      backgroundEnabled: false,
      outlineEnabled: true,
      outlineColor: '#000000FF',
      outlineWidthPx: 2,
      safeAreaAnchor: 'lower',
      alignment: 'center',
    };

    expect(
      validateSubtitleStyle({ ...subtitle, fontCode: serif.fontCode }),
    ).toMatch(/字体/);
    expect(
      validateSubtitleStyle({
        ...subtitle,
        fontCode: serif.fontCode,
        fontVersion: serif.version,
        fontSha256: serif.sha256,
      }),
    ).toBeUndefined();
  });
});
