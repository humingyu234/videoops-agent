import { describe, expect, it } from 'vitest';

import { sanitizeSuggestedFileName } from '../src/main/downloadPolicy';

describe('sanitizeSuggestedFileName', () => {
  it('removes path segments and platform-invalid characters', () => {
    expect(sanitizeSuggestedFileName('../../CON?.mp4')).toBe('CON_.mp4');
    expect(sanitizeSuggestedFileName('folder\\video:name.mp4')).toBe(
      'video_name.mp4',
    );
  });

  it('protects Windows reserved names', () => {
    expect(sanitizeSuggestedFileName('CON.mp4')).toBe('_CON.mp4');
    expect(sanitizeSuggestedFileName('lpt1')).toBe('_lpt1');
  });

  it('uses a safe fallback for an empty result', () => {
    expect(sanitizeSuggestedFileName('...')).toBe('download');
    expect(sanitizeSuggestedFileName('\u0000\u0001')).toBe('download');
  });

  it('limits the final name to 120 characters', () => {
    const result = sanitizeSuggestedFileName(`${'a'.repeat(200)}.mp4`);
    expect(result.length).toBeLessThanOrEqual(120);
    expect(result.endsWith('.mp4')).toBe(true);
  });
});
