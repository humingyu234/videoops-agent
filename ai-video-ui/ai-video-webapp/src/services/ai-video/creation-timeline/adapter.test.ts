import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  parseCreationOutputWire,
  parseCreationProjectWire,
  parseTimelineDraftWire,
} from './adapter';

const fixturePath = resolve(
  process.cwd(),
  '../../docs/contracts/creation-timeline/timeline-draft.example.json',
);

async function draftFixture(): Promise<Record<string, unknown>> {
  return JSON.parse(await readFile(fixturePath, 'utf8')) as Record<string, unknown>;
}

async function outputFixture(): Promise<Record<string, unknown>> {
  return JSON.parse(await readFile(
    resolve(
      process.cwd(),
      '../../docs/contracts/creation-timeline/creation-output.example.json',
    ),
    'utf8',
  )) as Record<string, unknown>;
}

describe('creation timeline adapter', () => {
  it('rejects an audio element whose usage does not match its track', async () => {
    const fixture = await draftFixture();
    const timeline = fixture.timeline as { tracks: Array<{ trackType: string; elements: Array<Record<string, unknown>> }> };
    const primaryAudioTrack = timeline.tracks.find(
      (track) => track.trackType === 'primary_audio',
    );
    if (!primaryAudioTrack) {
      throw new Error('C0 fixture must contain a primary audio track');
    }
    primaryAudioTrack.elements[0].usageType = 'background_music';
    primaryAudioTrack.elements[0].volumeRatio = 0.3;
    primaryAudioTrack.elements[0].loopWhenOverflow = true;
    primaryAudioTrack.elements[0].duckingEnabled = true;
    primaryAudioTrack.elements[0].targetGainRatio = 0.35;
    primaryAudioTrack.elements[0].attackMs = 120;
    primaryAudioTrack.elements[0].releaseMs = 400;

    expect(() => parseTimelineDraftWire(fixture)).toThrowError(
      'Invalid wire response: trackType does not match element usage',
    );
  });

  it('rejects unknown fields, unsafe milliseconds, and absent element discriminators', async () => {
    const fixture = await draftFixture() as {
      timeline: { canvas: { durationMs: number }; tracks: Array<{ elements: Array<Record<string, unknown>> }> };
    };

    const unknownField = structuredClone(fixture);
    unknownField.timeline.tracks[0].elements[0].internalPath = '/never/expose';
    expect(() => parseTimelineDraftWire(unknownField)).toThrow('contains an unknown field');

    const unsafeMilliseconds = structuredClone(fixture);
    unsafeMilliseconds.timeline.canvas.durationMs = 120001;
    expect(() => parseTimelineDraftWire(unsafeMilliseconds)).toThrow('durationMs must be an in-range integer');

    const missingDiscriminator = structuredClone(fixture);
    delete missingDiscriminator.timeline.tracks[0].elements[0].elementType;
    expect(() => parseTimelineDraftWire(missingDiscriminator)).toThrow('elementType contains an unknown enum value');
  });

  it('rejects unknown project statuses and numeric identifiers', async () => {
    const projectPath = resolve(
      process.cwd(),
      '../../docs/contracts/creation-timeline/project.example.json',
    );
    const fixture = JSON.parse(await readFile(projectPath, 'utf8')) as Record<string, unknown>;

    expect(() => parseCreationProjectWire({ ...fixture, status: 'new' })).toThrow('status contains an unknown enum value');
    expect(() => parseCreationProjectWire({ ...fixture, projectId: 90071992547409931 })).toThrow('projectId must be a canonical decimal string');
    expect(() => parseCreationProjectWire({ ...fixture, projectId: '-1' })).toThrow('projectId must be a positive decimal string');
  });

  it('enforces element-specific required fields and track placement invariants', async () => {
    const fixture = await draftFixture() as {
      timeline: { tracks: Array<{ trackType: string; area: string; elements: Array<Record<string, unknown>> }> };
    };

    const missingMainVideoAsset = structuredClone(fixture);
    const mainTrack = missingMainVideoAsset.timeline.tracks.find((track) => track.trackType === 'main_video');
    if (!mainTrack) throw new Error('C0 fixture must contain a main video track');
    delete mainTrack.elements[0].assetId;
    expect(() => parseTimelineDraftWire(missingMainVideoAsset)).toThrow('elements[0].assetId is required');

    const invalidTrackPlacement = structuredClone(fixture);
    const subtitleTrack = invalidTrackPlacement.timeline.tracks.find((track) => track.trackType === 'subtitle');
    if (!subtitleTrack) throw new Error('C0 fixture must contain a subtitle track');
    subtitleTrack.area = 'bottom';
    expect(() => parseTimelineDraftWire(invalidTrackPlacement)).toThrow('track area does not match trackType');
  });

  it('accepts only the four C2 CreationOutput fields', async () => {
    const fixture = await outputFixture();

    expect(parseCreationOutputWire(fixture)).toEqual(fixture);

    const missingTaskId = structuredClone(fixture);
    delete missingTaskId.taskId;
    expect(() => parseCreationOutputWire(missingTaskId)).toThrow('creationOutput.taskId is required');

    expect(() => parseCreationOutputWire({
      ...fixture,
      assetId: '9000000000000002',
    })).toThrow('creationOutput contains an unknown field');
    expect(() => parseCreationOutputWire({
      ...fixture,
      outputAssetId: 9000000000000002,
    })).toThrow('outputAssetId must be a canonical decimal string');
    expect(() => parseCreationOutputWire({
      projectId: fixture.projectId,
      assetId: '9000000000000002',
      mimeType: 'video/mp4',
      sizeBytes: '1024',
      previewUrl: '/preview',
      downloadUrl: '/download',
    })).toThrow('creationOutput contains an unknown field');
  });
});
