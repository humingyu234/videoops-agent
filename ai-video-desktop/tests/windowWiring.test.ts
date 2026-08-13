import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { BrowserWindowConstructorOptions, Session } from 'electron';

const electronMocks = vi.hoisted(() => ({
  browserWindowOptions: undefined as BrowserWindowConstructorOptions | undefined,
  loadURL: vi.fn(() => Promise.resolve()),
  webContentsOn: vi.fn(),
  quit: vi.fn(),
  showMessageBox: vi.fn(() => Promise.resolve({ response: 1 })),
}));

vi.mock('electron', () => ({
  app: { quit: electronMocks.quit },
  BrowserWindow: class {
    webContents = {
      on: electronMocks.webContentsOn,
      setWindowOpenHandler: vi.fn(),
    };

    constructor(options: BrowserWindowConstructorOptions) {
      electronMocks.browserWindowOptions = options;
    }

    isDestroyed(): boolean {
      return false;
    }

    loadURL = electronMocks.loadURL;
  },
  dialog: { showMessageBox: electronMocks.showMessageBox },
  shell: { openExternal: vi.fn(() => Promise.resolve()) },
}));

import { createMainWindow } from '../src/main/createWindow';
import { resolveWebTarget } from '../src/main/webUrlPolicy';

describe('BrowserWindow security wiring', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    electronMocks.browserWindowOptions = undefined;
  });

  it('creates the production window with the fixed isolation settings', async () => {
    const target = resolveWebTarget('https://app.example.com', 'production');
    createMainWindow(target, {} as Session);

    expect(electronMocks.browserWindowOptions?.autoHideMenuBar).toBe(true);
    expect(electronMocks.browserWindowOptions?.webPreferences).toMatchObject({
      allowRunningInsecureContent: false,
      contextIsolation: true,
      devTools: false,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      webviewTag: false,
    });
    expect(electronMocks.browserWindowOptions?.webPreferences?.session).toEqual({});
    await vi.waitFor(() =>
      expect(electronMocks.loadURL).toHaveBeenCalledWith('https://app.example.com/'),
    );
    expect(electronMocks.loadURL).toHaveBeenNthCalledWith(
      1,
      expect.stringMatching(/^data:text\/html;charset=utf-8,/),
    );
  });

  it('shows the loading page again before retrying a failed web load', async () => {
    const target = resolveWebTarget('https://app.example.com', 'production');
    electronMocks.showMessageBox.mockResolvedValue({ response: 0 });
    createMainWindow(target, {} as Session);

    await vi.waitFor(() =>
      expect(electronMocks.loadURL).toHaveBeenCalledWith('https://app.example.com/'),
    );
    electronMocks.loadURL.mockClear();

    const failHandler = electronMocks.webContentsOn.mock.calls.find(
      ([eventName]) => eventName === 'did-fail-load',
    )?.[1] as (...args: unknown[]) => void;
    failHandler({}, -106, '网络不可用', 'https://app.example.com/', true);

    await vi.waitFor(() =>
      expect(electronMocks.showMessageBox).toHaveBeenCalled(),
    );
    await vi.waitFor(() => expect(electronMocks.loadURL).toHaveBeenCalledTimes(2));
    expect(electronMocks.loadURL).toHaveBeenNthCalledWith(
      1,
      expect.stringMatching(/^data:text\/html;charset=utf-8,/),
    );
    expect(electronMocks.loadURL).toHaveBeenNthCalledWith(
      2,
      'https://app.example.com/',
    );
  });
});
