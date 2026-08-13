import { describe, expect, it } from 'vitest';

import {
  isSupportedPortraitImage,
  PORTRAIT_IMAGE_ACCEPT,
} from './portraitImageFile';

describe('portrait image file validation', () => {
  it('exposes the supported extensions and MIME types for file inputs', () => {
    expect(PORTRAIT_IMAGE_ACCEPT).toBe(
      '.jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif',
    );
  });

  it.each([
    ['A.JPG', 'image/jpeg'],
    ['A.JpEg', 'IMAGE/JPEG'],
    ['A.PnG', 'image/png'],
    ['A.WeBp', 'image/webp'],
    ['A.GIF', 'image/gif'],
  ])('accepts matching %s and %s values case-insensitively', (name, type) => {
    expect(isSupportedPortraitImage(new File(['image'], name, { type }))).toBe(
      true,
    );
  });

  it('accepts a matching extension after earlier dots', () => {
    expect(
      isSupportedPortraitImage(
        new File(['image'], 'portrait.final.JpG', { type: 'image/jpeg' }),
      ),
    ).toBe(true);
  });

  it.each([
    ['A.webp', 'image/png'],
    ['A.svg', 'image/svg+xml'],
    ['A.bmp', 'image/bmp'],
    ['A.heic', 'image/heic'],
    ['A.avif', 'image/avif'],
    ['A.jpg', ''],
    ['jpg', 'image/jpeg'],
    ['.jpg', 'image/jpeg'],
    ['portrait.', 'image/jpeg'],
  ])('rejects unsupported or mismatched %s and %s values', (name, type) => {
    expect(isSupportedPortraitImage(new File(['image'], name, { type }))).toBe(
      false,
    );
  });
});
