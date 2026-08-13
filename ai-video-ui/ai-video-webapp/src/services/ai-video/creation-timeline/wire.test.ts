import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { parseTimelineTaskDetailWire, parseTimelineTaskListItemWire } from './adapter';

const taskFixturePath = resolve(
  process.cwd(),
  '../../docs/contracts/creation-timeline/timeline-task.example.json',
);

async function taskFixture(): Promise<Record<string, unknown>> {
  return JSON.parse(await readFile(taskFixturePath, 'utf8')) as Record<string, unknown>;
}

describe('creation timeline task wire', () => {
  it('accepts the C0 HTTP task field names without exposing internal DTO names', async () => {
    const task = parseTimelineTaskDetailWire(await taskFixture());

    expect(task).toMatchObject({
      taskId: '90071992547409937',
      taskType: 'timeline_render',
      canCancel: true,
      canRetry: false,
    });
    expect('cancellable' in task).toBe(false);
    expect('retryable' in task).toBe(false);
    expect('safeMessage' in task).toBe(false);
  });

  it('rejects internal DTO fields and strips detail results from list items', async () => {
    const fixture = await taskFixture();

    expect(() => parseTimelineTaskDetailWire({ ...fixture, cancellable: true })).toThrow('contains an unknown field');
    expect(() => parseTimelineTaskDetailWire({ ...fixture, retryable: false })).toThrow('contains an unknown field');
    expect(() => parseTimelineTaskDetailWire({ ...fixture, safeMessage: 'internal' })).toThrow('contains an unknown field');

    const listItem = parseTimelineTaskListItemWire({
      ...fixture,
      result: { shouldNotReach: 'the page' },
    });
    expect('result' in listItem).toBe(false);
  });

  it('rejects unknown task types outside the four timeline task contracts', async () => {
    const fixture = await taskFixture();

    expect(() => parseTimelineTaskDetailWire({
      ...fixture,
      taskType: 'timeline_future_task',
      result: { providerInternals: 'never exposed' },
    })).toThrow('taskType must be a supported timeline task');
  });
});
