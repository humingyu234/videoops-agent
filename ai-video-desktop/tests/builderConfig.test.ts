import { readFileSync } from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

const configPath = path.join(process.cwd(), 'electron-builder.yml');
const packagePath = path.join(process.cwd(), 'package.json');

describe('electron-builder targets', () => {
  it('uses suzao-ai as the default Windows installation directory name', () => {
    const packageJson = JSON.parse(readFileSync(packagePath, 'utf8')) as {
      name?: string;
    };
    expect(packageJson.name).toBe('suzao-ai');
  });

  it('defines the product identity and unsigned test mode', () => {
    const config = readFileSync(configPath, 'utf8');
    expect(config).toContain('appId: com.suzao.aivideo');
    expect(config).toContain('productName: 素造智能体');
    expect(config).toContain('identity: null');
    expect(config).toContain('publish: null');
  });

  it('builds Windows x64 NSIS and macOS x64/arm64 DMG artifacts', () => {
    const config = readFileSync(configPath, 'utf8');
    expect(config).toMatch(/target:\s*nsis/);
    expect(config).toMatch(/target:\s*dmg/);
    expect(config).toContain('- x64');
    expect(config).toContain('- arm64');
    expect(config).toContain('windows-${arch}-setup.${ext}');
    expect(config).toContain('macos-${arch}.${ext}');
  });
});
