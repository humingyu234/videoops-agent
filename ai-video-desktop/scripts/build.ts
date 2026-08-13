import { rm } from 'node:fs/promises';
import path from 'node:path';

import { build } from 'esbuild';

import {
  resolveWebTarget,
  type BuildMode,
} from '../src/main/webUrlPolicy.ts';

function resolveBuildMode(raw: string | undefined): BuildMode {
  if (!raw || raw === 'development') return 'development';
  if (raw === 'production') return 'production';
  throw new Error(
    'ELECTRON_BUILD_MODE 只能是 development 或 production',
  );
}

async function main(): Promise<void> {
  const root = process.cwd();
  const mode = resolveBuildMode(process.env.ELECTRON_BUILD_MODE);
  const webTarget = resolveWebTarget(process.env.AI_VIDEO_WEB_URL, mode);
  const sharedOptions = {
    bundle: true,
    define: {
      __AI_VIDEO_BUILD_MODE__: JSON.stringify(mode),
      __AI_VIDEO_WEB_URL__: JSON.stringify(webTarget.href),
    },
    external: ['electron'],
    format: 'cjs' as const,
    logLevel: 'info' as const,
    minify: false,
    platform: 'node' as const,
    sourcemap: mode === 'development',
    target: 'node22',
  };

  await rm(path.join(root, 'dist'), { force: true, recursive: true });

  await Promise.all([
    build({
      ...sharedOptions,
      entryPoints: [path.join(root, 'src/main/index.ts')],
      outfile: path.join(root, 'dist/main/index.cjs'),
    }),
    build({
      ...sharedOptions,
      entryPoints: [path.join(root, 'src/preload/index.ts')],
      outfile: path.join(root, 'dist/preload/index.cjs'),
    }),
  ]);
}

void main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
