import { describe, expect, it } from 'vitest';

import {
  isAllowedDownloadChain,
  isAllowedMainNavigation,
  isSafeExternalUrl,
} from '../src/main/securityPolicy';
import { resolveWebTarget } from '../src/main/webUrlPolicy';

const productionTarget = resolveWebTarget(
  'https://app.example.com/workspace',
  'production',
);
const developmentTarget = resolveWebTarget(
  'http://localhost:8000',
  'development',
);

describe('main-frame navigation policy', () => {
  it('allows only the configured origin', () => {
    expect(
      isAllowedMainNavigation('https://app.example.com/projects/1', productionTarget),
    ).toBe(true);
    expect(
      isAllowedMainNavigation('https://app.example.com.evil.test', productionTarget),
    ).toBe(false);
    expect(
      isAllowedMainNavigation('https://app.example.com:444', productionTarget),
    ).toBe(false);
    expect(
      isAllowedMainNavigation(
        'https://user:password@app.example.com/projects/1',
        productionTarget,
      ),
    ).toBe(false);
    const loadingPageUrl = 'data:text/html;charset=utf-8,local-loading-page';
    expect(
      isAllowedMainNavigation(loadingPageUrl, productionTarget, loadingPageUrl),
    ).toBe(true);
    expect(
      isAllowedMainNavigation(
        'data:text/html;charset=utf-8,unexpected-page',
        productionTarget,
        loadingPageUrl,
      ),
    ).toBe(false);
    expect(isAllowedMainNavigation('not a url', productionTarget)).toBe(false);
  });
});

describe('external URL policy', () => {
  it('allows credential-free HTTPS only', () => {
    expect(isSafeExternalUrl('https://docs.example.com/help')).toBe(true);
    expect(isSafeExternalUrl('http://docs.example.com/help')).toBe(false);
    expect(isSafeExternalUrl('javascript:alert(1)')).toBe(false);
    expect(isSafeExternalUrl('https://user:password@example.com')).toBe(false);
    expect(isSafeExternalUrl('not a url')).toBe(false);
  });
});

describe('download chain policy', () => {
  it('allows HTTPS redirects in production', () => {
    expect(
      isAllowedDownloadChain(
        [
          'https://api.example.com/download/1',
          'https://oss.example.com/signed/video.mp4',
        ],
        productionTarget,
      ),
    ).toBe(true);
  });

  it('rejects insecure or empty production chains', () => {
    expect(
      isAllowedDownloadChain(['http://oss.example.com/video.mp4'], productionTarget),
    ).toBe(false);
    expect(isAllowedDownloadChain([], productionTarget)).toBe(false);
  });

  it('allows only same-origin blob URLs', () => {
    expect(
      isAllowedDownloadChain(
        ['blob:https://app.example.com/a35e62d0-72df-4d79-b291-5b02310495dd'],
        productionTarget,
      ),
    ).toBe(true);
    expect(
      isAllowedDownloadChain(
        ['blob:https://evil.example/a35e62d0-72df-4d79-b291-5b02310495dd'],
        productionTarget,
      ),
    ).toBe(false);
  });

  it('allows development HTTP only on loopback hosts', () => {
    expect(
      isAllowedDownloadChain(
        ['http://127.0.0.1:8080/api/download/1'],
        developmentTarget,
      ),
    ).toBe(true);
    expect(
      isAllowedDownloadChain(['http://example.com/video.mp4'], developmentTarget),
    ).toBe(false);
  });
});
