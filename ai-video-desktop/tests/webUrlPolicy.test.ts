import { describe, expect, it } from 'vitest';

import { resolveWebTarget } from '../src/main/webUrlPolicy';

describe('resolveWebTarget', () => {
  it('uses the loopback web app by default in development', () => {
    expect(resolveWebTarget(undefined, 'development')).toEqual({
      href: 'http://localhost:8000/',
      mode: 'development',
      origin: 'http://localhost:8000',
    });
  });

  it('normalizes an HTTPS production URL while preserving its path', () => {
    expect(
      resolveWebTarget('https://app.example.com/workspace', 'production'),
    ).toEqual({
      href: 'https://app.example.com/workspace',
      mode: 'production',
      origin: 'https://app.example.com',
    });
  });

  it('requires an explicit production URL', () => {
    expect(() => resolveWebTarget(undefined, 'production')).toThrow(
      'AI_VIDEO_WEB_URL',
    );
  });

  it('requires HTTPS in production', () => {
    expect(() =>
      resolveWebTarget('http://example.com', 'production'),
    ).toThrow('HTTPS');
  });

  it('allows HTTP only for loopback hosts in development', () => {
    expect(
      resolveWebTarget('http://127.0.0.1:9000/app', 'development').origin,
    ).toBe('http://127.0.0.1:9000');
    expect(() =>
      resolveWebTarget('http://example.com', 'development'),
    ).toThrow('本机');
  });

  it('rejects embedded credentials', () => {
    expect(() =>
      resolveWebTarget('https://user:password@example.com', 'production'),
    ).toThrow('凭据');
  });
});
