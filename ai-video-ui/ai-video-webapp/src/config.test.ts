import { afterEach, describe, expect, it, vi } from 'vitest';

async function loadConfig() {
  vi.resetModules();
  return (await import('../config/config')).default;
}

describe('bundler configuration', () => {
  afterEach(() => {
    delete process.env.AI_VIDEO_DISABLE_UTOOPACK;
    delete process.env.AI_VIDEO_DISCOVERY_MOCK;
    delete process.env.AI_VIDEO_CREATION_TIMELINE_MOCK;
  });

  it('keeps Utoopack inside the current app directory', async () => {
    const config = await loadConfig();

    expect(config.utoopack).toMatchObject({ root: '.' });
  });

  it('allows isolated worktrees to use Webpack with shared dependencies', async () => {
    process.env.AI_VIDEO_DISABLE_UTOOPACK = 'true';

    const config = await loadConfig();

    expect(config).toMatchObject({
      mfsu: false,
      utoopack: false,
    });
  });

  it('can enable only the discovery mock while keeping authenticated APIs real', async () => {
    process.env.AI_VIDEO_DISCOVERY_MOCK = 'true';

    const config = await loadConfig();

    expect(config.mock).toMatchObject({
      exclude: ['mock/!(discovery).ts', 'mock/requestRecord.mock.js'],
    });
  });

  it('excludes creation timeline mocks unless explicitly enabled', async () => {
    const productionConfig = await loadConfig();
    expect(productionConfig.mock).toMatchObject({
      exclude: expect.arrayContaining(['mock/creationTimeline.ts', 'mock/creationAssets.ts']),
    });

    process.env.AI_VIDEO_CREATION_TIMELINE_MOCK = 'true';
    const mockConfig = await loadConfig();
    expect(mockConfig.mock).toMatchObject({
      exclude: ['mock/!(creationTimeline|creationAssets).ts', 'mock/requestRecord.mock.js'],
    });
  });
});
