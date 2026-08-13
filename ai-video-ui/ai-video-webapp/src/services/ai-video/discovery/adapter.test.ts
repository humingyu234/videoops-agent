import { describe, expect, it, vi } from 'vitest';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';
import { createDiscoveryApi } from './api';

const HASH = `sha256:${'a'.repeat(64)}`;

const media = {
  mediaId: '31',
  mediaType: 'image',
  url: '/media/cover.webp',
  width: 1200,
  height: 900,
  alt: '模板封面',
};

const card = {
  templateId: '101',
  title: '口播工作流模板',
  summary: '普通中文正文可提到工作流和节点',
  channel: 'video_template',
  category: { categoryCode: '11', label: '营销' },
  tags: [{ tagCode: '21', label: '口播' }],
  cover: null,
  preview: media,
  usageCount: '7',
  estimatedDurationSeconds: 30,
  enabledAt: '2026-08-11T09:30:00',
};

const home = {
  banners: [],
  recommendations: [card],
  channels: [
    {
      channel: 'video_template',
      label: '视频模板',
      description: '即用型视频制作工作流',
      templateCount: '1',
    },
  ],
  categories: [{ categoryCode: '11', label: '营销', templateCount: '1' }],
  tags: [{ tagCode: '21', label: '口播' }],
};

const detail = {
  ...card,
  description: '模板详情',
  cases: [media],
  requiredInputs: [
    {
      semanticKey: 'portrait',
      label: '人物图片',
      valueType: 'asset_array',
      assetType: 'image',
      required: true,
    },
  ],
};

const config = {
  templateId: '101',
  schemaVersion: 'workflow-form-1',
  schemaHash: HASH,
  fields: [
    {
      inputKey: 'title',
      label: '标题',
      control: 'text',
      valueType: 'string',
      required: true,
      constraints: { minLength: 1, maxLength: 120 },
    },
  ],
  estimatedDurationSeconds: 30,
  billingPolicy: { mode: 'free' },
};

function apiWith(payload: unknown) {
  const adapter: RuoYiAdapter = {
    request: vi.fn().mockResolvedValue(payload),
  };
  return createDiscoveryApi(adapter);
}

describe('discovery strict response adapter', () => {
  it('accepts the exact phase-one wire, including a nullable cover', async () => {
    await expect(apiWith(home).getHome()).resolves.toEqual(home);
    await expect(apiWith({ rows: [card], total: 1 }).getTemplates({
      pageNum: 1,
      pageSize: 10,
    })).resolves.toEqual({ rows: [card], total: 1 });
    await expect(apiWith(detail).getTemplate('101')).resolves.toEqual(detail);
    await expect(apiWith(config).getCreationConfig('101')).resolves.toEqual(config);
  });

  it('accepts a cover whose source does not provide image dimensions', async () => {
    const cardWithUnknownCoverDimensions = {
      ...card,
      cover: { ...media, width: 0, height: 0 },
    };

    await expect(
      apiWith({ rows: [cardWithUnknownCoverDimensions], total: 1 }).getTemplates({
        pageNum: 1,
        pageSize: 10,
      }),
    ).resolves.toEqual({ rows: [cardWithUnknownCoverDimensions], total: 1 });
  });

  it.each([
    ['self_hosted_comfyui', 'value'],
    ['runninghub_workflow', 'value'],
    ['runninghub_ai_app', 'value'],
    ['providerKind', 'key'],
    ['executionMode', 'key'],
    ['executionPlanId', 'key'],
    ['templateVersionId', 'key'],
    ['workflowId', 'key'],
    ['webAppId', 'key'],
    ['nodeId', 'key'],
    ['runningHubTaskId', 'key'],
  ] as const)('rejects canonical forbidden %s recursively', async (forbidden, kind) => {
    const unsafe = structuredClone(home);
    if (kind === 'value') {
      unsafe.recommendations[0].summary = forbidden;
    } else {
      Object.assign(unsafe.recommendations[0], { [forbidden]: 'hidden' });
    }

    await expect(apiWith(unsafe).getHome()).rejects.toThrow(
      'Invalid discovery response: forbidden wire data',
    );
  });

  it('does not reject ordinary Chinese text or partial key names', async () => {
    const safe = structuredClone(home);
    safe.recommendations[0].summary =
      '用户正文可以讨论工作流、节点和服务选择，但不包含内部枚举值。';
    Object.assign(safe.recommendations[0], {
      providerKindHint: undefined,
    });
    delete (safe.recommendations[0] as Record<string, unknown>).providerKindHint;

    await expect(apiWith(safe).getHome()).resolves.toEqual(safe);
  });

  it('rejects missing and extra properties instead of silently narrowing', async () => {
    const extra = { ...card, extra: true };
    const missing = { ...card } as Record<string, unknown>;
    delete missing.enabledAt;

    await expect(
      apiWith({ rows: [extra], total: 1 }).getTemplates({
        pageNum: 1,
        pageSize: 10,
      }),
    ).rejects.toThrow('Invalid discovery response: expected exact keys');
    await expect(apiWith(missing).getTemplate('101')).rejects.toThrow(
      'Invalid discovery response: expected exact keys',
    );
  });

  it.each([
    [{ ...card, templateId: 'template-1' }, 'templateId'],
    [
      { ...card, category: { categoryCode: 'marketing', label: '营销' } },
      'categoryCode',
    ],
    [
      { ...card, tags: [{ tagCode: 'popular', label: '热门' }] },
      'tagCode',
    ],
  ])('rejects an invalid decimal id in %s', async (invalid) => {
    await expect(
      apiWith({ rows: [invalid], total: 1 }).getTemplates({
        pageNum: 1,
        pageSize: 10,
      }),
    ).rejects.toThrow('must be a decimal id');
  });

  it('rejects unknown enum values and a string page total', async () => {
    await expect(
      apiWith({ rows: [{ ...card, channel: 'provider_gallery' }], total: 1 })
        .getTemplates({ pageNum: 1, pageSize: 10 }),
    ).rejects.toThrow('channel contains an unknown enum value');
    await expect(
      apiWith({ rows: [], total: '1' }).getTemplates({
        pageNum: 1,
        pageSize: 10,
      }),
    ).rejects.toThrow('total must be a safe non-negative integer');
  });

  it.each([
    'http://internal/cover.jpg',
    '//cdn.example.com/cover.jpg',
    'javascript:alert(1)',
    '/media\\cover.jpg',
    '/media/cover.jpg\u0000',
  ])('rejects the unsafe media URL %j', async (url) => {
    await expect(
      apiWith({
        ...detail,
        cases: [{ ...media, url }],
      }).getTemplate('101'),
    ).rejects.toThrow('must be same-origin or HTTPS');
  });

  it('rejects malformed creation schemas and unknown controls', async () => {
    await expect(
      apiWith({ ...config, schemaHash: 'sha256:demo' }).getCreationConfig('101'),
    ).rejects.toThrow('schemaHash is invalid');
    await expect(
      apiWith({
        ...config,
        fields: [{ ...config.fields[0], control: 'provider_select' }],
      }).getCreationConfig('101'),
    ).rejects.toThrow('control contains an unknown enum value');
  });
});
