import { readFileSync } from 'node:fs';

import { describe, expect, it } from 'vitest';

const packageJson = JSON.parse(
  readFileSync('package.json', 'utf8'),
) as { scripts: Record<string, string> };

describe('production build scripts', () => {
  it.each(['deploy', 'preview:build'])(
    'routes %s through the guarded build script',
    (scriptName) => {
      expect(packageJson.scripts[scriptName]).toMatch(/\bnpm run build\b/);
      expect(packageJson.scripts[scriptName]).not.toMatch(/\bmax build\b/);
    },
  );
});
