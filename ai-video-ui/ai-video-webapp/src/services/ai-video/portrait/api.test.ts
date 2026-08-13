import { describe, expect, it, vi } from 'vitest';
import { createPortraitApi } from './api';

describe('portrait api', () => {
  it('keeps portrait paths and multipart body inside the service', async () => {
    const request = vi.fn().mockResolvedValue({ assetId: '9', availabilityStatus: 'ready' });
    const api = createPortraitApi({ request });
    const file = new File(['image'], 'portrait.png', { type: 'image/png' });

    await api.upload(file);

    expect(request).toHaveBeenCalledWith('/api/assets/uploads/portrait-images', expect.objectContaining({
      method: 'POST',
      data: expect.any(FormData),
    }));
  });
});
