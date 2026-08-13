import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { parseCreationProjectWire, parseTimelineDraftWire } from './adapter';

const contractDirectory = resolve(
  process.cwd(),
  '../../docs/contracts/creation-timeline',
);

async function readContractFixture(fileName: string): Promise<unknown> {
  const content = await readFile(resolve(contractDirectory, fileName), 'utf8');
  return JSON.parse(content) as unknown;
}

describe('creation timeline C0 contract fixtures', () => {
  it('accepts the frozen project and timeline draft examples without copying them', async () => {
    const [projectWire, draftWire] = await Promise.all([
      readContractFixture('project.example.json'),
      readContractFixture('timeline-draft.example.json'),
    ]);

    const project = parseCreationProjectWire(projectWire);
    const draft = parseTimelineDraftWire(draftWire);

    expect(project.projectId).toBe('90071992547409931');
    expect(project.canvas).toEqual({
      width: 1080,
      height: 1920,
      frameRate: 30,
      durationMs: 30000,
    });
    expect(draft.revision).toBe('3');
    expect(draft.timeline.schemaVersion).toBe('timeline-1');
    expect(draft.timeline.tracks).toHaveLength(9);
  });
});
