import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { describe, expect, it, vi } from 'vitest';
import {
  FANCY_TEXT_TEMPLATES,
  FIXED_TIMELINE_FONTS,
  getFancyTextTemplate,
  loadTimelineFonts,
} from './fancyTextTemplates';

const contractRegistryPath = resolve(
  process.cwd(),
  '../../docs/contracts/creation-timeline/font-registry.json',
);
const publicFontDirectory = resolve(process.cwd(), 'public/timeline-fonts');

describe('fancy text templates and fonts', () => {
  it('keeps exactly the six C0 templates and rejects unknown codes', () => {
    expect(
      FANCY_TEXT_TEMPLATES.map(({ code, label, previewDescription }) => ({
        code,
        label,
        previewDescription,
      })),
    ).toEqual([
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
      { code: 'bubble_bounce', label: '气泡弹跳', previewDescription: '轻弹' },
      { code: 'title_wipe', label: '标题横扫', previewDescription: '横向遮罩' },
    ]);
    expect(() => getFancyTextTemplate('arbitrary_css_template')).toThrow(
      'Unknown fancy text template',
    );
  });

  it('ships the C0 registry byte-for-byte with verified fixed font assets', async () => {
    const [
      contractRegistry,
      publicRegistry,
      sans,
      serif,
      license,
      sums,
      attributes,
    ] =
      await Promise.all([
        readFile(contractRegistryPath),
        readFile(resolve(publicFontDirectory, 'font-registry.json')),
        readFile(resolve(publicFontDirectory, 'NotoSansCJKsc-Regular.otf')),
        readFile(resolve(publicFontDirectory, 'NotoSerifCJKsc-Regular.otf')),
        readFile(resolve(publicFontDirectory, 'OFL.txt')),
        readFile(resolve(publicFontDirectory, 'SHA256SUMS'), 'utf8'),
        readFile(resolve(publicFontDirectory, '.gitattributes'), 'utf8'),
      ]);
    expect(publicRegistry.equals(contractRegistry)).toBe(true);
    expect(createHash('sha256').update(sans).digest('hex')).toBe(
      '2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b',
    );
    expect(createHash('sha256').update(serif).digest('hex')).toBe(
      '2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca',
    );
    expect(createHash('sha256').update(license).digest('hex')).toBe(
      '6a73f9541c2de74158c0e7cf6b0a58ef774f5a780bf191f2d7ec9cc53efe2bf2',
    );
    expect(license.includes(0x0d)).toBe(false);
    expect(attributes).toBe('OFL.txt text eol=lf\n');
    expect(sums).toContain(
      '2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b  NotoSansCJKsc-Regular.otf',
    );
    expect(sums).toContain(
      '2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca  NotoSerifCJKsc-Regular.otf',
    );
    expect(sums).toContain(
      '6a73f9541c2de74158c0e7cf6b0a58ef774f5a780bf191f2d7ec9cc53efe2bf2  OFL.txt',
    );
  });

  it('marks fonts invalid when hash verification or loading fails instead of falling back', async () => {
    const registry = {
      registryVersion: 'timeline-fonts-1',
      fonts: FIXED_TIMELINE_FONTS,
    };
    const sansBytes = new Uint8Array([1]).buffer;
    const serifBytes = new Uint8Array([2]).buffer;
    const fromHex = (value: string) =>
      Uint8Array.from(value.match(/.{2}/g) ?? [], (part) =>
        Number.parseInt(part, 16),
      ).buffer;
    const fetcher = vi.fn(async (url: string) =>
      url.endsWith('font-registry.json')
        ? {
            ok: true,
            json: async () => registry,
            arrayBuffer: async () => sansBytes,
          }
        : {
            ok: true,
            json: async () => registry,
            arrayBuffer: async () =>
              url.includes('NotoSans') ? sansBytes : serifBytes,
          },
    );
    const createFontFace = vi.fn(() => ({ load: async () => undefined }));

    const ready = await loadTimelineFonts({
      fetcher,
      digest: async (bytes) =>
        new Uint8Array(bytes)[0] === 1
          ? fromHex(FIXED_TIMELINE_FONTS[0].sha256)
          : fromHex(FIXED_TIMELINE_FONTS[1].sha256),
      createFontFace,
      fontSet: { add: vi.fn() },
    });
    expect(ready.status).toBe('ready');
    expect(createFontFace).toHaveBeenCalledTimes(2);

    const invalid = await loadTimelineFonts({
      fetcher,
      digest: async () => new Uint8Array(32).fill(0xbb).buffer,
      createFontFace,
      fontSet: { add: vi.fn() },
    });
    expect(invalid).toMatchObject({ status: 'font-invalid' });

    const unavailable = await loadTimelineFonts({
      fetcher: async () => ({
        ok: false,
        json: async () => registry,
        arrayBuffer: async () => sansBytes,
      }),
      digest: async () => fromHex(FIXED_TIMELINE_FONTS[0].sha256),
      createFontFace,
      fontSet: { add: vi.fn() },
    });
    expect(unavailable).toMatchObject({ status: 'font-invalid' });

    const loadFailed = await loadTimelineFonts({
      fetcher,
      digest: async (bytes) =>
        new Uint8Array(bytes)[0] === 1
          ? fromHex(FIXED_TIMELINE_FONTS[0].sha256)
          : fromHex(FIXED_TIMELINE_FONTS[1].sha256),
      createFontFace: () => ({
        load: async () => {
          throw new Error('font load failed');
        },
      }),
      fontSet: { add: vi.fn() },
    });
    expect(loadFailed).toMatchObject({ status: 'font-invalid' });
  });
});
