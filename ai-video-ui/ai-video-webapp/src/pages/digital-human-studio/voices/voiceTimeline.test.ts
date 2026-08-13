import { describe, expect, it } from 'vitest';

import { VOICES } from '../model';
import {
  buildVoiceTicks,
  clampVoicePercent,
  formatVoiceSeconds,
  getExactVoiceWords,
  getVoiceWords,
  splitVoiceSentences,
} from './voiceTimeline';

describe('voice timeline helpers', () => {
  it('keeps real Whisper cue boundaries instead of averaging characters', () => {
    expect(getExactVoiceWords([
      { text: '微信', startMillis: 120, endMillis: 480 },
      { text: '公众号', startMillis: 500, endMillis: 920 },
    ])).toEqual([
      { word: '微信', start: 0.12, dur: 0.36, isPunct: false },
      { word: '公众号', start: 0.5, dur: 0.42, isPunct: false },
    ]);
  });

  it('allocates all characters continuously across the requested duration', () => {
    const words = getVoiceWords(['你好，', '世界真的好。'], 12);

    expect(words.map(({ word }) => word).join('')).toBe('你好，世界真的好。');
    words.slice(1).forEach((word, index) => {
      const previousWord = words[index];
      expect(word.start).toBe(previousWord.start + previousWord.dur);
    });
    expect(words.reduce((total, item) => total + item.dur, 0)).toBeCloseTo(12);
  });

  it('marks supported Chinese punctuation', () => {
    const words = getVoiceWords(['你，好。吗！是？的、吧；啊：'], 12);

    expect(
      words.filter(({ isPunct }) => isPunct).map(({ word }) => word),
    ).toEqual(['，', '。', '！', '？', '、', '；', '：']);
  });

  it('returns no words when content or duration is empty', () => {
    expect(getVoiceWords([], 12)).toEqual([]);
    expect(getVoiceWords([''], 12)).toEqual([]);
    expect(getVoiceWords(['内容'], 0)).toEqual([]);
  });

  it.each([
    [-1, '0:00'],
    [0, '0:00'],
    [62.9, '1:02'],
  ])('formats %s seconds as %s', (seconds, expected) => {
    expect(formatVoiceSeconds(seconds)).toBe(expected);
  });

  it('removes whitespace and splits text at Chinese punctuation', () => {
    expect(
      splitVoiceSentences(' 大家好，\n欢迎 来到直播间。真的 好吗？ 是的！'),
    ).toEqual(['大家好，', '欢迎来到直播间。', '真的好吗？', '是的！']);
    expect(splitVoiceSentences(' \n\t ')).toEqual([]);
  });

  it('builds five-second ticks for a 31-second voice', () => {
    const ticks = buildVoiceTicks(31);

    expect(ticks.map(({ seconds }) => seconds)).toEqual([0, 5, 10, 15, 20, 25, 30]);
    expect(ticks.filter(({ major }) => major).map(({ seconds }) => seconds)).toEqual([
      0, 10, 20, 30,
    ]);
    expect(ticks.filter(({ major }) => major).map(({ label }) => label)).toEqual([
      '0:00',
      '0:10',
      '0:20',
      '0:30',
    ]);
    expect(ticks[1]?.leftPercent).toBeCloseTo((5 / 31) * 100);
  });

  it('builds ten-second ticks for a 62-second voice', () => {
    expect(buildVoiceTicks(62).map(({ seconds }) => seconds)).toEqual([
      0, 10, 20, 30, 40, 50, 60,
    ]);
  });

  it('returns no ticks for non-positive durations', () => {
    expect(buildVoiceTicks(0)).toEqual([]);
    expect(buildVoiceTicks(-1)).toEqual([]);
  });

  it.each([
    [Number.NaN, 0],
    [Number.POSITIVE_INFINITY, 0],
    [Number.NEGATIVE_INFINITY, 0],
    [-0.1, 0],
    [0, 0],
    [0.4, 0.4],
    [1, 1],
    [1.1, 1],
  ])('clamps %s to %s', (value, expected) => {
    expect(clampVoicePercent(value)).toBe(expected);
  });
});

describe('voice library data', () => {
  it('contains the exact seven voice records used by the timeline', () => {
    expect(VOICES).toEqual([
      {
        id: 'vs-003',
        name: '夏季新品 60秒 克隆',
        meta: '源自：亲切女声（参考） · 4.8MB',
        dur: '01:02',
        script:
          '夏季新品正式上线，限时三天全场八折。这次我们准备了超多惊喜，更有隐藏福利等你来发现。',
        secs: 62,
        sents: [
          '夏季新品正式上线，',
          '限时三天全场八折。',
          '这次我们准备了超多惊喜，',
          '更有隐藏福利等你来发现。',
        ],
        owner: 'custom',
        status: 'verified',
        type: 'clone',
      },
      {
        id: 'vs-004',
        name: '门店引流 30秒 克隆',
        meta: '源自：商务原声 A · 2.6MB',
        dur: '00:31',
        script: '本周末门店引流活动开启，到店即送精美礼品一份，数量有限先到先得。',
        secs: 31,
        sents: [
          '本周末门店引流活动开启，',
          '到店即送精美礼品一份，',
          '数量有限先到先得。',
        ],
        owner: 'custom',
        status: 'verified',
        type: 'clone',
      },
      {
        id: 'vs-001',
        name: '亲切女声（参考）',
        meta: '3.6MB',
        dur: '00:45',
        script:
          '大家好，欢迎来到我们的直播间。今天给大家带来一款非常好用的清洁产品，记得点赞收藏哦。',
        secs: 45,
        sents: [
          '大家好，欢迎来到我们的直播间。',
          '今天给大家带来一款非常好用的清洁产品，',
          '记得点赞收藏哦。',
        ],
        owner: 'custom',
        status: 'verified',
        type: 'origin',
      },
      {
        id: 'vs-002',
        name: '商务原声 A',
        meta: '2.4MB',
        dur: '00:32',
        script: '专注于企业级解决方案，我们致力于为客户提供最优质的服务体验。',
        secs: 32,
        sents: ['专注于企业级解决方案，', '我们致力于为客户提供最优质的服务体验。'],
        owner: 'custom',
        status: 'verified',
        type: 'origin',
      },
      {
        id: 'vs-005',
        name: '温柔讲书声',
        meta: '5.8MB',
        dur: '01:12',
        script:
          '夜深了，城市的喧嚣渐渐平息。在这个安静的时刻，让我为你读一段温暖的故事，陪你度过这个美好的夜晚。',
        secs: 72,
        sents: [
          '夜深了，城市的喧嚣渐渐平息。',
          '在这个安静的时刻，',
          '让我为你读一段温暖的故事，',
          '陪你度过这个美好的夜晚。',
        ],
        owner: 'custom',
        status: 'pending',
        type: 'origin',
      },
      {
        id: 'vs-201',
        name: '清亮女声',
        meta: '3.9MB',
        dur: '00:47',
        script: '探店打卡新地标，这家店真的太出片了。每一处角落都是绝佳拍照点，快约上闺蜜一起冲。',
        secs: 47,
        sents: [
          '探店打卡新地标，',
          '这家店真的太出片了。',
          '每一处角落都是绝佳拍照点，',
          '快约上闺蜜一起冲。',
        ],
        owner: 'public',
        status: 'verified',
        type: 'public',
      },
      {
        id: 'vs-202',
        name: '磁性男声',
        meta: '3.1MB',
        dur: '00:38',
        script: '失眠困扰着很多人，今天分享三个调理小方法，帮你找回安稳睡眠。',
        secs: 38,
        sents: ['失眠困扰着很多人，', '今天分享三个调理小方法，', '帮你找回安稳睡眠。'],
        owner: 'public',
        status: 'verified',
        type: 'public',
      },
    ]);
  });
});
