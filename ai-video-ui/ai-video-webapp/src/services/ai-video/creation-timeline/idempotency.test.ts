import { describe, expect, it, vi } from 'vitest';
import { createIdempotencyKeyStore } from './idempotency';

describe('timeline idempotency keys', () => {
  it('reuses a key after an unknown network result and rotates it only for a new intent', () => {
    const createKey = vi.fn()
      .mockReturnValueOnce('intent-a')
      .mockReturnValueOnce('intent-b');
    const keys = createIdempotencyKeyStore(createKey);

    expect(keys.forIntent('save-draft')).toBe('intent-a');
    expect(keys.retryUnknown('save-draft')).toBe('intent-a');
    expect(createKey).toHaveBeenCalledTimes(1);
    expect(keys.beginNewIntent('save-draft')).toBe('intent-b');
    expect(createKey).toHaveBeenCalledTimes(2);
  });
});
