import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { BrowserWindow, Session } from 'electron';

const electronMocks = vi.hoisted(() => ({
  appOn: vi.fn(),
  openExternal: vi.fn(() => Promise.resolve()),
}));

vi.mock('electron', () => ({
  app: { on: electronMocks.appOn },
  shell: { openExternal: electronMocks.openExternal },
}));

import {
  installCertificateErrorHandler,
  installSessionSecurity,
  installWindowSecurity,
} from '../src/main/security';
import { resolveWebTarget } from '../src/main/webUrlPolicy';

const target = resolveWebTarget('https://app.example.com', 'production');

describe('Electron security wiring', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('denies permission checks and requests by default', () => {
    const setPermissionCheckHandler = vi.fn();
    const setPermissionRequestHandler = vi.fn();
    const desktopSession = {
      setPermissionCheckHandler,
      setPermissionRequestHandler,
    } as unknown as Session;

    installSessionSecurity(desktopSession);

    const checkHandler = setPermissionCheckHandler.mock.calls[0]?.[0];
    const requestHandler = setPermissionRequestHandler.mock.calls[0]?.[0];
    const callback = vi.fn();
    expect(checkHandler()).toBe(false);
    requestHandler(undefined, 'media', callback);
    expect(callback).toHaveBeenCalledWith(false);
  });

  it('blocks cross-origin, credentialed redirects and Electron windows', () => {
    const handlers = new Map<string, (...args: never[]) => unknown>();
    const setWindowOpenHandler = vi.fn();
    const window = {
      webContents: {
        on: vi.fn((name: string, handler: (...args: never[]) => unknown) => {
          handlers.set(name, handler);
        }),
        setWindowOpenHandler,
      },
    } as unknown as BrowserWindow;

    installWindowSecurity(window, target);

    const allowedEvent = { preventDefault: vi.fn() };
    handlers.get('will-navigate')?.(
      allowedEvent as never,
      'https://app.example.com/projects' as never,
    );
    expect(allowedEvent.preventDefault).not.toHaveBeenCalled();

    for (const url of [
      'https://evil.example/projects',
      'https://user:password@app.example.com/projects',
    ]) {
      const blockedEvent = { preventDefault: vi.fn() };
      handlers.get('will-redirect')?.(blockedEvent as never, url as never);
      expect(blockedEvent.preventDefault).toHaveBeenCalledOnce();
    }

    const openHandler = setWindowOpenHandler.mock.calls[0]?.[0];
    expect(openHandler({ url: 'javascript:alert(1)' })).toEqual({ action: 'deny' });
    expect(electronMocks.openExternal).not.toHaveBeenCalled();
    expect(openHandler({ url: 'https://docs.example.com/help' })).toEqual({
      action: 'deny',
    });
    expect(electronMocks.openExternal).toHaveBeenCalledWith(
      'https://docs.example.com/help',
    );
  });

  it('rejects certificate errors without an override path', () => {
    installCertificateErrorHandler();
    const certificateHandler = electronMocks.appOn.mock.calls.find(
      ([name]) => name === 'certificate-error',
    )?.[1];
    const event = { preventDefault: vi.fn() };
    const callback = vi.fn();

    certificateHandler(
      event,
      undefined,
      'https://app.example.com',
      'ERR_CERT_INVALID',
      undefined,
      callback,
    );

    expect(event.preventDefault).toHaveBeenCalledOnce();
    expect(callback).toHaveBeenCalledWith(false);
  });
});
