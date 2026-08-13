import type { DigitalHumanJob } from '@/services/ai-video/digitalHuman/types';
import type { GeneratedQuestionnaire } from '@/services/ai-video/questionnaire/types';
import type { GeneratedScript } from '@/services/ai-video/script-generation/types';
import type { VoiceWord } from './voices/voiceTimeline';

export type StudioRoute = 'create' | 'avatars' | 'voices' | 'scripts' | 'works';
export type AssetOwner = 'custom' | 'public';
export type AssetStatus = 'verified' | 'pending' | 'failed';
export type VoiceType = 'clone' | 'origin' | 'public';

export interface VoiceGenerationIntent {
  idempotencyKey: string;
  referenceVoiceId?: string;
  referenceAudio?: File;
  scriptText: string;
}

export interface VideoGenerationIntent {
  idempotencyKey: string;
  portraitId?: string;
  portraitImage?: File;
  voiceJobId: string;
}

export interface VoiceItem {
  id: string;
  assetId?: string;
  recordRevision?: string;
  transcriptionStatus?:
    | 'unparsed'
    | 'pending'
    | 'transcribing'
    | 'ready'
    | 'failed';
  timeline?: VoiceWord[];
  timelineExact?: boolean;
  name: string;
  meta: string;
  dur: string;
  script: string;
  secs: number;
  sents: string[];
  owner: AssetOwner;
  status: AssetStatus;
  type: VoiceType;
}

export interface StudioState {
  route: StudioRoute;
  step: number;
  industry: string | null;
  purpose: string | null;
  customIndustry: string;
  customPurpose: string;
  survey: Record<string, string[]>;
  surveyOtherAnswers: Record<string, string>;
  surveyCursor: number;
  questionnaire: GeneratedQuestionnaire | null;
  duration: number;
  supplement: string;
  selectedScript: number;
  scriptVersions: GeneratedScript[];
  scriptBodies: string[];
  selectedAvatar: string | null;
  selectedVoice: string | null;
  portraitImage: File | null;
  referenceAudio: File | null;
  voiceGenerationIntent: VoiceGenerationIntent | null;
  voiceJob: DigitalHumanJob | null;
  videoGenerationIntent: VideoGenerationIntent | null;
  videoJob: DigitalHumanJob | null;
  /** 服务端时间轴项目是第 6、7 步的唯一身份来源，不能由本地草稿替代。 */
  timelineProjectId: string | null;
  /** 创建当前时间轴项目所使用的成功数字人底片任务。 */
  timelineSourceTaskId: string | null;
}

export const initialStudioState: StudioState = {
  route: 'create',
  step: 0,
  industry: null,
  purpose: null,
  customIndustry: '',
  customPurpose: '',
  survey: {},
  surveyOtherAnswers: {},
  surveyCursor: 0,
  questionnaire: null,
  duration: 60,
  supplement: '',
  selectedScript: 0,
  scriptVersions: [],
  scriptBodies: [],
  selectedAvatar: null,
  selectedVoice: 'vs-003',
  portraitImage: null,
  referenceAudio: null,
  voiceGenerationIntent: null,
  voiceJob: null,
  videoGenerationIntent: null,
  videoJob: null,
  timelineProjectId: null,
  timelineSourceTaskId: null,
};

export const STEPS = [
  '说需求',
  '确认文案',
  '选择形象与声音',
  '生成并确认声音',
  '生成数字人底片',
  '时间轴编辑',
  '预览与下载',
];

export const ROUTE_META: Record<
  StudioRoute,
  { title: string; description: string }
> = {
  create: { title: '创作', description: '智能生成 9:16 数字人口播视频' },
  avatars: {
    title: '形象',
    description: '管理当前工作区的人物形象照片',
  },
  voices: { title: '声音', description: '管理原声音与克隆声音资产' },
  scripts: { title: '文案', description: '管理口播文案版本及其关联资产' },
  works: { title: '作品', description: '管理最终作品、历史版本与下载' },
};

export const INDUSTRIES = [
  {
    id: 'ecommerce',
    name: '电商零售',
    icon: 'shopping',
    desc: '带货、种草、产品介绍',
  },
  {
    id: 'education',
    name: '教育培训',
    icon: 'book',
    desc: '课程讲解、知识科普',
  },
  { id: 'food', name: '餐饮美食', icon: 'food', desc: '探店、菜单、活动' },
  { id: 'home', name: '家居装修', icon: 'home', desc: '案例、设计、施工' },
  { id: 'local', name: '本地生活', icon: 'map', desc: '到店引流、服务推广' },
  { id: 'custom', name: '自定义行业', icon: 'edit', desc: '手动输入行业名' },
];

export const PURPOSES: Record<string, string[]> = {
  ecommerce: ['获客咨询', '产品介绍', '活动宣传', '涨粉种草', '案例分享'],
  education: ['课程讲解', '知识分享', '招生引流', '学员答疑'],
  food: ['到店引流', '新品推荐', '套餐活动', '探店打卡'],
  home: ['案例展示', '设计讲解', '获客咨询', '施工工艺'],
  local: ['到店引流', '服务介绍', '活动宣传', '用户好评'],
  custom: [],
};

export const SURVEY_ORDER = ['audience', 'goal', 'highlight', 'style'];

export interface SurveyQuestion {
  text: string;
  hint: string;
  options: string[];
}

export const getSurveyQuestion = (
  key: string,
  industry: string,
  purpose: string,
  survey: Record<string, string[]>,
): SurveyQuestion => {
  if (key === 'audience') {
    const byIndustry: Record<string, SurveyQuestion> = {
      ecommerce: {
        text: '你的目标客户主要是哪类人群？',
        hint: '影响文案语气与切入角度',
        options: [
          '宝妈/家庭主妇',
          '年轻白领',
          '学生/求职者',
          '中老年用户',
          '小企业主',
        ],
      },
      education: {
        text: '学员主要是哪类人群？',
        hint: '决定讲解深度与术语选择',
        options: ['中小学生', '大学生', '职场进阶', '兴趣学习者', '中老年'],
      },
      food: {
        text: '希望吸引哪类顾客到店？',
        hint: '影响菜品推荐与活动设计',
        options: ['家庭聚餐', '年轻情侣', '上班族', '学生党', '游客'],
      },
      home: {
        text: '目标客户处于什么阶段？',
        hint: '决定案例切入方式',
        options: [
          '新房装修',
          '旧房改造',
          '软装搭配',
          '局部翻新',
          '设计方案咨询',
        ],
      },
      local: {
        text: '服务覆盖哪类客户？',
        hint: '影响推广话术与渠道',
        options: ['周边居民', '上班族', '家庭客户', '企业团购', '流动人群'],
      },
      custom: {
        text: '你的目标受众是？',
        hint: '越具体，文案越精准',
        options: ['年轻白领', '家庭主妇', '学生', '企业主', '中老年'],
      },
    };
    return byIndustry[industry] ?? byIndustry.custom;
  }
  if (key === 'goal') {
    const audience = survey.audience ?? [];
    const options =
      audience.includes('宝妈/家庭主妇') || audience.includes('家庭客户')
        ? ['解决日常痛点', '省时省力', '性价比高', '家人健康']
        : audience.includes('年轻白领') || audience.includes('上班族')
          ? ['提升效率', '品质生活', '性价比', '社交分享']
          : [
              '直接促成下单',
              '建立品牌认知',
              '活动短期引爆',
              '积累粉丝长期种草',
            ];
    return {
      text: '他们最关心什么？',
      hint: 'AI 将围绕核心诉求组织文案',
      options,
    };
  }
  if (key === 'highlight') {
    const options = ['活动宣传', '套餐活动', '门店引流'].includes(purpose)
      ? ['优惠力度（限时/限量）', '产品特色', '使用场景', '用户好评']
      : ['涨粉种草', '科普种草'].includes(purpose)
        ? ['独特卖点', '使用前后对比', '权威背书', '情感共鸣']
        : ['核心功能', '价格优势', '品质保证', '服务承诺'];
    return {
      text: '想重点突出什么？',
      hint: '可多选，AI 会综合权重排序',
      options,
    };
  }
  const audience = survey.audience ?? [];
  return {
    text: '希望文案的整体风格？',
    hint: '影响句式与词汇选择',
    options:
      audience.includes('宝妈/家庭主妇') || audience.includes('中老年用户')
        ? ['亲切口语', '温情故事', '专业可信', '理性科普']
        : ['专业可信', '激情带货', '理性科普', '亲切口语'],
  };
};

export const SCRIPT_VERSIONS = [
  {
    id: 0,
    title: '直击痛点版',
    duration: '约 62 秒',
    body: '还在为家里地板总是黏糊糊发愁吗？普通拖把越拖越脏，细菌根本清不掉。\n\n我们的纳米抑菌拖把采用日本进口超细纤维，一拖即净，抑菌率高达 99.9%。\n\n限时活动价 89 元，包邮到家。点击下方链接，今晚下单再送替换芯 3 个，活动仅剩 3 天，错过再等一年。',
  },
  {
    id: 1,
    title: '温情故事版',
    duration: '约 57 秒',
    body: '上周闺蜜来我家，进门就问：你家地板怎么这么干净？\n\n我笑了笑没说话，其实秘密全在这把纳米拖把里。自从换了它，每天五分钟，地板像新的一样，孩子光脚跑也不担心。\n\n如果你也想轻松拥有干净的家，试试看，89 元包邮到家。',
  },
  {
    id: 2,
    title: '对比测评版',
    duration: '约 65 秒',
    body: '普通拖把 vs 纳米抑菌拖把，差距到底有多大？\n\n我们做了个实验。同样一滩酱油渍，普通拖把来回 6 次还有残留，纳米拖把一次就干净。\n\n秘密在于日本进口超细纤维，吸附力提升 3 倍，抑菌率 99.9%。\n\n89 元，可以用一年。点击链接下单，今晚发顺丰。',
  },
];

export const AVATARS = [
  {
    id: 'av-002',
    name: '亲切女主播',
    owner: 'custom',
    gender: 'female',
    scenes: '带货 · 母婴 · 生活',
    status: 'verified',
    style: 'linear-gradient(160deg,#f4d4c4,#c98a6e)',
  },
  {
    id: 'av-007',
    name: '干练男主持',
    owner: 'custom',
    gender: 'male',
    scenes: '商务 · 科技 · 教育',
    status: 'verified',
    style: 'linear-gradient(160deg,#c5d4e8,#5a7fa8)',
  },
  {
    id: 'av-008',
    name: '活力小姐姐',
    owner: 'custom',
    gender: 'female',
    scenes: '美妆 · 时尚 · 探店',
    status: 'pending',
    style: 'linear-gradient(160deg,#f4c4d8,#c46a8a)',
  },
  {
    id: 'av-101',
    name: '小雅',
    owner: 'public',
    gender: 'female',
    scenes: '通用 · 口播',
    status: 'verified',
    style: 'linear-gradient(160deg,#d4e8c4,#6e9c4a)',
  },
  {
    id: 'av-102',
    name: '阿俊',
    owner: 'public',
    gender: 'male',
    scenes: '通用 · 知识',
    status: 'verified',
    style: 'linear-gradient(160deg,#e8e4c4,#a89a4a)',
  },
  {
    id: 'av-103',
    name: 'Mia',
    owner: 'public',
    gender: 'female',
    scenes: '通用 · 带货',
    status: 'verified',
    style: 'linear-gradient(160deg,#d4c4e8,#7a5a9c)',
  },
] as const;

export const VOICES: readonly VoiceItem[] = [
  {
    id: 'vs-003',
    name: '夏季新品 60秒 克隆',
    type: 'clone',
    owner: 'custom',
    meta: '源自：亲切女声（参考） · 4.8MB',
    dur: '01:02',
    secs: 62,
    status: 'verified',
    script:
      '夏季新品正式上线，限时三天全场八折。这次我们准备了超多惊喜，更有隐藏福利等你来发现。',
    sents: [
      '夏季新品正式上线，',
      '限时三天全场八折。',
      '这次我们准备了超多惊喜，',
      '更有隐藏福利等你来发现。',
    ],
  },
  {
    id: 'vs-004',
    name: '门店引流 30秒 克隆',
    type: 'clone',
    owner: 'custom',
    meta: '源自：商务原声 A · 2.6MB',
    dur: '00:31',
    secs: 31,
    status: 'verified',
    script: '本周末门店引流活动开启，到店即送精美礼品一份，数量有限先到先得。',
    sents: [
      '本周末门店引流活动开启，',
      '到店即送精美礼品一份，',
      '数量有限先到先得。',
    ],
  },
  {
    id: 'vs-001',
    name: '亲切女声（参考）',
    type: 'origin',
    owner: 'custom',
    meta: '3.6MB',
    dur: '00:45',
    secs: 45,
    status: 'verified',
    script:
      '大家好，欢迎来到我们的直播间。今天给大家带来一款非常好用的清洁产品，记得点赞收藏哦。',
    sents: [
      '大家好，欢迎来到我们的直播间。',
      '今天给大家带来一款非常好用的清洁产品，',
      '记得点赞收藏哦。',
    ],
  },
  {
    id: 'vs-002',
    name: '商务原声 A',
    type: 'origin',
    owner: 'custom',
    meta: '2.4MB',
    dur: '00:32',
    secs: 32,
    status: 'verified',
    script: '专注于企业级解决方案，我们致力于为客户提供最优质的服务体验。',
    sents: ['专注于企业级解决方案，', '我们致力于为客户提供最优质的服务体验。'],
  },
  {
    id: 'vs-005',
    name: '温柔讲书声',
    type: 'origin',
    owner: 'custom',
    meta: '5.8MB',
    dur: '01:12',
    secs: 72,
    status: 'pending',
    script:
      '夜深了，城市的喧嚣渐渐平息。在这个安静的时刻，让我为你读一段温暖的故事，陪你度过这个美好的夜晚。',
    sents: [
      '夜深了，城市的喧嚣渐渐平息。',
      '在这个安静的时刻，',
      '让我为你读一段温暖的故事，',
      '陪你度过这个美好的夜晚。',
    ],
  },
  {
    id: 'vs-201',
    name: '清亮女声',
    type: 'public',
    owner: 'public',
    meta: '3.9MB',
    dur: '00:47',
    secs: 47,
    status: 'verified',
    script:
      '探店打卡新地标，这家店真的太出片了。每一处角落都是绝佳拍照点，快约上闺蜜一起冲。',
    sents: [
      '探店打卡新地标，',
      '这家店真的太出片了。',
      '每一处角落都是绝佳拍照点，',
      '快约上闺蜜一起冲。',
    ],
  },
  {
    id: 'vs-202',
    name: '磁性男声',
    type: 'public',
    owner: 'public',
    meta: '3.1MB',
    dur: '00:38',
    secs: 38,
    status: 'verified',
    script: '失眠困扰着很多人，今天分享三个调理小方法，帮你找回安稳睡眠。',
    sents: [
      '失眠困扰着很多人，',
      '今天分享三个调理小方法，',
      '帮你找回安稳睡眠。',
    ],
  },
];

export const WORKS = [
  {
    id: 'w-001',
    name: '夏季新品口播 v3',
    status: 'published',
    dur: '01:02',
    dim: '1080×1920',
    size: '24.6MB',
    updated: '今天 15:48',
    cover: 'linear-gradient(160deg,#3a4a5e,#1a2230)',
  },
  {
    id: 'w-002',
    name: '门店引流 v1',
    status: 'published',
    dur: '00:31',
    dim: '1080×1920',
    size: '12.3MB',
    updated: '昨天 11:02',
    cover: 'linear-gradient(160deg,#5e3a4a,#302028)',
  },
  {
    id: 'w-003',
    name: '探店打卡 v2',
    status: 'processing',
    dur: '00:47',
    dim: '1080×1920',
    size: '处理中',
    updated: '10 分钟前',
    cover: 'linear-gradient(160deg,#4a5e3a,#283020)',
  },
  {
    id: 'w-004',
    name: '失眠科普 v3',
    status: 'draft',
    dur: '01:18',
    dim: '1080×1920',
    size: '草稿',
    updated: '昨天 09:48',
    cover: 'linear-gradient(160deg,#3a4a5e,#1a2230)',
  },
  {
    id: 'w-005',
    name: '课程招生 v1',
    status: 'draft',
    dur: '00:52',
    dim: '1080×1920',
    size: '草稿',
    updated: '前天',
    cover: 'linear-gradient(160deg,#5e4a3a,#302820)',
  },
] as const;
