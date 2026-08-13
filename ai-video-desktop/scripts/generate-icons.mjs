import { createRequire } from 'node:module';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

import sharp from 'sharp';

const require = createRequire(import.meta.url);
const png2icons = require('png2icons');

const root = process.cwd();
const source = path.resolve(
  root,
  '../ai-video-ui/ai-video-webapp/public/logo.svg',
);
const outputDirectory = path.join(root, 'resources/icons');

await mkdir(outputDirectory, { recursive: true });
const sourceSvg = await readFile(source);
const png = await sharp(sourceSvg)
  .resize(1024, 1024, { fit: 'contain' })
  .png()
  .toBuffer();
const ico = png2icons.createICO(png, png2icons.BICUBIC2, 0, false, true);
const icns = png2icons.createICNS(png, png2icons.BICUBIC2, 0);

if (!ico || !icns) throw new Error('无法生成桌面应用图标');

await Promise.all([
  writeFile(path.join(outputDirectory, 'icon.png'), png),
  writeFile(path.join(outputDirectory, 'icon.ico'), ico),
  writeFile(path.join(outputDirectory, 'icon.icns'), icns),
]);
