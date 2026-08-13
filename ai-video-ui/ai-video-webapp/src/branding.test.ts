import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const read = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8');

describe('user-facing product branding', () => {
  it.each([
    ['config/config.ts', "title: '素造智能体'"],
    ['config/defaultSettings.ts', "title: '素造智能体'"],
    ['src/components/Footer/index.tsx', '素造智能体 &copy; {year}'],
    ['src/pages/digital-human-studio/components/StudioSider.tsx', '素造智能体'],
    ['src/pages/Welcome.tsx', '欢迎使用素造智能体'],
    ['src/pages/Admin.tsx', '素造智能体'],
    ['src/pages/chatbot/index.tsx', '🚀 素造智能体如何接入后端权限系统？'],
  ])('%s contains the approved brand', (path, expected) => {
    expect(read(path)).toContain(expected);
  });

  it('uses the approved PWA names', () => {
    const manifest = JSON.parse(read('src/manifest.json')) as {
      name: string;
      short_name: string;
    };
    expect(manifest).toMatchObject({ name: '素造智能体', short_name: '素造智能体' });
  });

  it.each([
    ['src/locales/zh-CN/pages.ts', '欢迎使用素造智能体'],
    ['src/locales/zh-TW/pages.ts', '歡迎使用素造智能体'],
    ['src/locales/en-US/pages.ts', 'Welcome to 素造智能体'],
    ['src/locales/ja-JP/pages.ts', '素造智能体へようこそ'],
    ['src/locales/pt-BR/pages.ts', 'Bem-vindo ao 素造智能体'],
    ['src/locales/id-ID/pages.ts', 'Selamat datang di 素造智能体'],
    ['src/locales/fa-IR/pages.ts', 'به 素造智能体 خوش آمدید'],
    ['src/locales/bn-BD/pages.ts', '素造智能体-এ স্বাগতম'],
  ])('%s uses the localized welcome copy', (path, expected) => {
    expect(read(path)).toContain(expected);
    expect(read(path)).not.toContain('{v6}');
  });

  it('keeps OpenAPI metadata outside the product rename', () => {
    expect(read('config/oneapi.json')).toContain(
      ['Ant', 'Design', 'Pro'].join(' '),
    );
  });
});
