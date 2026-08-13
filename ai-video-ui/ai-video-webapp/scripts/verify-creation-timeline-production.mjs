import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const mockMarker = 'AI_VIDEO_CREATION_TIMELINE_MOCK_MARKER';

function fail(message) {
  console.error(`creation timeline production gate: ${message}`);
  process.exit(1);
}

function scan(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? scan(path) : [path];
  });
}

if (process.env.AI_VIDEO_CREATION_TIMELINE_MOCK === 'true') {
  fail('AI_VIDEO_CREATION_TIMELINE_MOCK must not be enabled for production builds');
}

if (process.argv.includes('--after')) {
  if (!existsSync('dist')) fail('dist is missing after build');
  const contaminated = scan('dist').find((file) => readFileSync(file, 'utf8').includes(mockMarker));
  if (contaminated) fail(`mock marker found in ${contaminated}`);
}
