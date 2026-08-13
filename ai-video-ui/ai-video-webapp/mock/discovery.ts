import type { Request, Response } from 'express';

type Channel = 'video_template' | 'workflow_inspiration';

const source = [
  ['101', '护肤产品氛围广告', '一张产品图生成柔光质感的品牌短片', 'skincare.webp', '11', '产品广告', 'video_template'],
  ['102', '美食动效商业片', '把餐品素材转成富有食欲的电影级动效', 'food-commercial.webp', '12', '美食电商', 'video_template'],
  ['103', '霓虹时装转场', '高对比灯光与节奏卡点的时尚短片', 'neon-fashion.webp', '13', '时尚大片', 'video_template'],
  ['104', '旅行航拍叙事', '从单张风景图延展出平滑航拍镜头', 'travel-aerial.webp', '14', '旅行风光', 'video_template'],
  ['105', '黏土定格故事', '把人物或物品变成温暖的黏土动画', 'clay-story.webp', '15', '风格化', 'workflow_inspiration'],
  ['106', '未来科技发布片', '金属、粒子和体积光组成的科技视觉', 'future-tech.webp', '11', '产品广告', 'workflow_inspiration'],
  ['107', '室内空间漫游', '从设计图生成自然稳定的空间运镜', 'modern-interior.webp', '16', '空间建筑', 'video_template'],
  ['108', '水墨记忆短片', '将照片演绎为留白流动的东方叙事', 'ink-story.webp', '15', '风格化', 'workflow_inspiration'],
  ['109', '开箱种草视频', '为电商商品生成干净利落的开箱展示', 'unboxing.webp', '17', '电商带货', 'video_template'],
  ['110', '虚拟主播口播', '上传人物图和音频，生成自然口型与镜头表现', 'presenter-studio.webp', '18', '人物口播', 'workflow_inspiration'],
] as const;

function media(id: string, file: string, alt: string, index: number) {
  return {
    mediaId: id,
    mediaType: 'image' as const,
    url: `/discovery/${file}`,
    width: 1200,
    height: index % 3 === 1 ? 1500 : 1000,
    alt,
  };
}

const templates = source.map(
  ([id, title, summary, file, categoryCode, categoryLabel, channel], index) => ({
    templateId: id,
    title,
    summary,
    channel: channel as Channel,
    category: { categoryCode, label: categoryLabel },
    tags: [
      { tagCode: index % 2 ? '22' : '21', label: index % 2 ? '电影感' : '热门' },
      { tagCode: '23', label: '快速开始' },
    ],
    cover: index === 6 ? null : media(String(301 + index), file, `${title}效果预览`, index),
    usageCount: String(1280 + index * 437),
    estimatedDurationSeconds: 30 + index * 5,
    enabledAt: `2026-08-${String(index + 1).padStart(2, '0')}T08:00:00`,
  }),
);

function ok(res: Response, data: unknown) {
  res.send({ code: 200, msg: '操作成功', data });
}

function unavailable(res: Response) {
  res.status(404).send({ code: 46501, msg: '模板不存在或已下架', data: null });
}

function getDetail(id: string) {
  const template = templates.find((item) => item.templateId === id);
  if (!template) return undefined;
  return {
    ...template,
    description: `${template.title}是一套已经准备好的创作模板。按页面提示准备素材，即可在制作功能开放后继续创作。`,
    cases: templates
      .filter((item) => item.templateId !== id && item.cover !== null)
      .slice(0, 3)
      .map((item) => item.cover),
    requiredInputs: [
      {
        semanticKey: 'source_image',
        label: '主体图片',
        valueType: 'asset_array',
        assetType: 'image',
        required: true,
      },
      {
        semanticKey: 'creative_direction',
        label: '画面描述',
        valueType: 'string',
        required: true,
      },
    ],
  };
}

export default {
  'GET /api/discovery/home': (_req: Request, res: Response) =>
    ok(res, {
      banners: [
        {
          bannerId: '201',
          title: '让产品图动起来',
          subtitle: '高级商业光影，快速预览品牌短片方向',
          target: { type: 'template', templateId: '101' },
          media: media('401', 'skincare.webp', '护肤产品氛围广告', 0),
        },
        {
          bannerId: '202',
          title: '探索电影感视频',
          subtitle: '精选镜头语言创作模板',
          target: { type: 'channel', channel: 'video_template' },
          media: media('402', 'neon-fashion.webp', '霓虹时尚大片', 1),
        },
      ],
      recommendations: templates.slice(0, 6),
      channels: [
        {
          channel: 'video_template',
          label: '视频模板',
          description: '即用型视频制作模板',
          templateCount: '6',
        },
        {
          channel: 'workflow_inspiration',
          label: '创作灵感',
          description: '探索更多创意表达',
          templateCount: '4',
        },
      ],
      categories: [
        { categoryCode: '11', label: '产品广告', templateCount: '2' },
        { categoryCode: '13', label: '时尚大片', templateCount: '1' },
        { categoryCode: '15', label: '风格化', templateCount: '2' },
        { categoryCode: '14', label: '旅行风光', templateCount: '1' },
        { categoryCode: '18', label: '人物口播', templateCount: '1' },
      ],
      tags: [
        { tagCode: '21', label: '热门' },
        { tagCode: '22', label: '电影感' },
        { tagCode: '23', label: '快速开始' },
      ],
    }),

  'GET /api/discovery/templates/:templateId/creation-config': (
    req: Request,
    res: Response,
  ) => {
    const templateId = String(req.params.templateId);
    if (!getDetail(templateId)) {
      unavailable(res);
      return;
    }
    ok(res, {
      templateId,
      schemaVersion: 'workflow-form-1',
      schemaHash: `sha256:${'a'.repeat(64)}`,
      fields: [
        {
          inputKey: 'creative_direction',
          semanticKey: 'creative_direction',
          label: '画面描述',
          description: '描述希望呈现的画面与运动效果',
          control: 'textarea',
          valueType: 'string',
          required: true,
          placeholder: '例如：柔和光影，镜头缓慢推进',
          constraints: { minLength: 1, maxLength: 2000 },
        },
      ],
      estimatedDurationSeconds: 60,
      billingPolicy: { mode: 'free' },
    });
  },

  'GET /api/discovery/templates/:templateId': (req: Request, res: Response) => {
    const detail = getDetail(String(req.params.templateId));
    if (!detail) {
      unavailable(res);
      return;
    }
    ok(res, detail);
  },

  'GET /api/discovery/templates': (req: Request, res: Response) => {
    const keyword = String(req.query.keyword ?? '').trim().toLowerCase();
    const channel = String(req.query.channel ?? '');
    const categoryCode = String(req.query.categoryCode ?? '');
    const tagCodes = String(req.query.tagCodes ?? '')
      .split(',')
      .filter(Boolean);
    const rows = templates.filter(
      (item) =>
        (!channel || item.channel === channel) &&
        (!categoryCode || item.category.categoryCode === categoryCode) &&
        (!tagCodes.length ||
          tagCodes.every((tagCode) =>
            item.tags.some((tag) => tag.tagCode === tagCode),
          )) &&
        (!keyword ||
          `${item.title}${item.summary}${item.category.label}`
            .toLowerCase()
            .includes(keyword)),
    );
    const pageNum = Math.max(1, Number(req.query.pageNum ?? 1));
    const pageSize = Math.min(50, Math.max(1, Number(req.query.pageSize ?? 10)));
    const start = (pageNum - 1) * pageSize;
    ok(res, { rows: rows.slice(start, start + pageSize), total: rows.length });
  },
};
