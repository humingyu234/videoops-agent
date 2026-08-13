import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const read = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8');

describe('platform product branding', () => {
  it.each(['.env.development', '.env.production'])(
    '%s owns the approved titles',
    (path) => {
      const content = read(path);
      expect(content).toContain('VITE_APP_TITLE=素造智能体后台管理系统');
      expect(content).toContain('VITE_APP_LOGO_TITLE=素造智能体');
      expect(content).toContain(
        'VITE_APP_CLIENT_ID=e5cd7e4891bf95d1d19206ce24a7b32e',
      );
    },
  );

  it('uses matching runtime and build fallbacks', () => {
    expect(read('src/utils/env.ts')).toContain("'素造智能体后台管理系统'");
    expect(read('src/utils/env.ts')).toContain("'素造智能体'");
    expect(read('vite.config.ts')).toContain("'素造智能体后台管理系统'");
  });

  it('shows the approved console title', () => {
    expect(read('src/pages/index.tsx')).toContain('素造智能体控制台');
  });

  it('keeps the approved external repository URL', () => {
    const repositoryName = ['AI', 'Video'].join('-');
    expect(read('src/components/layout/ExternalLinkButton.tsx')).toContain(
      `https://gitee.com/dromara/${repositoryName}`,
    );
  });
});
