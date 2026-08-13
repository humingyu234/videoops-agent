import { beforeEach, describe, expect, it, vi } from 'vitest';

import type {
  BrowserWindow,
  DownloadItem,
  Session,
  WebContents,
} from 'electron';

const electronMocks = vi.hoisted(() => ({
  showErrorBox: vi.fn(),
  showMessageBox: vi.fn(() => Promise.resolve({ response: 0 })),
  showSaveDialog: vi.fn(),
}));

vi.mock('electron', () => ({
  app: { getPath: vi.fn(() => 'C:\\Downloads') },
  dialog: electronMocks,
}));

import { installDownloadHandler } from '../src/main/downloads';
import { resolveWebTarget } from '../src/main/webUrlPolicy';

const target = resolveWebTarget('https://app.example.com', 'production');

interface ItemFixture {
  done: (state: string) => void;
  item: DownloadItem;
  spies: {
    cancel: ReturnType<typeof vi.fn>;
    pause: ReturnType<typeof vi.fn>;
    resume: ReturnType<typeof vi.fn>;
    setSavePath: ReturnType<typeof vi.fn>;
  };
}

function createItem(url = 'https://cdn.example.com/video.mp4'): ItemFixture {
  let doneHandler: ((_event: unknown, state: string) => void) | undefined;
  const spies = {
    cancel: vi.fn(),
    pause: vi.fn(),
    resume: vi.fn(),
    setSavePath: vi.fn(),
  };
  const item = {
    ...spies,
    getFilename: vi.fn(() => '../../video?.mp4'),
    getURLChain: vi.fn(() => [url]),
    isPaused: vi.fn(() => true),
    once: vi.fn((name: string, handler: typeof doneHandler) => {
      if (name === 'done') doneHandler = handler;
      return item;
    }),
  } as unknown as DownloadItem;

  return {
    done: (state: string) => doneHandler?.(undefined, state),
    item,
    spies,
  };
}

function createHarness(): {
  emitDownload: (item: DownloadItem, sender?: WebContents) => void;
  mainContents: WebContents;
} {
  let downloadHandler:
    | ((_event: unknown, item: DownloadItem, sender: WebContents) => void)
    | undefined;
  const mainContents = {
    getURL: vi.fn(() => 'https://app.example.com/projects'),
  } as unknown as WebContents;
  const window = {
    isDestroyed: vi.fn(() => false),
    webContents: mainContents,
  } as unknown as BrowserWindow;
  const desktopSession = {
    on: vi.fn(
      (
        name: string,
        handler: (_event: unknown, item: DownloadItem, sender: WebContents) => void,
      ) => {
        if (name === 'will-download') downloadHandler = handler;
      },
    ),
  } as unknown as Session;

  installDownloadHandler(desktopSession, () => window, target);
  return {
    emitDownload: (item, sender = mainContents) =>
      downloadHandler?.(undefined, item, sender),
    mainContents,
  };
}

describe('Electron download wiring', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    electronMocks.showMessageBox.mockResolvedValue({ response: 0 });
  });

  it('cancels downloads from an unexpected sender before showing a dialog', () => {
    const harness = createHarness();
    const fixture = createItem();
    const otherContents = {
      getURL: vi.fn(() => 'https://app.example.com'),
    } as unknown as WebContents;

    harness.emitDownload(fixture.item, otherContents);

    expect(fixture.spies.cancel).toHaveBeenCalledOnce();
    expect(electronMocks.showSaveDialog).not.toHaveBeenCalled();
  });

  it('cancels when the user closes the save dialog', async () => {
    electronMocks.showSaveDialog.mockResolvedValue({ canceled: true });
    const harness = createHarness();
    const fixture = createItem();

    harness.emitDownload(fixture.item);

    await vi.waitFor(() => expect(fixture.spies.cancel).toHaveBeenCalledOnce());
    expect(fixture.spies.pause).toHaveBeenCalledOnce();
  });

  it('uses only the selected path and reports an interrupted transfer', async () => {
    electronMocks.showSaveDialog.mockResolvedValue({
      canceled: false,
      filePath: 'D:\\Exports\\video.mp4',
    });
    const harness = createHarness();
    const fixture = createItem();

    harness.emitDownload(fixture.item);

    await vi.waitFor(() =>
      expect(fixture.spies.setSavePath).toHaveBeenCalledWith(
        'D:\\Exports\\video.mp4',
      ),
    );
    expect(fixture.spies.resume).toHaveBeenCalledOnce();
    fixture.done('interrupted');
    expect(electronMocks.showErrorBox).toHaveBeenCalledWith(
      '下载失败',
      '文件下载被中断，请重试。',
    );
  });

  it('cancels concurrent downloads and shows one deduplicated prompt', async () => {
    let resolveSaveDialog: ((value: { canceled: true }) => void) | undefined;
    electronMocks.showSaveDialog.mockReturnValue(
      new Promise((resolve) => {
        resolveSaveDialog = resolve;
      }),
    );
    const harness = createHarness();
    const first = createItem();
    const second = createItem();
    const third = createItem();

    harness.emitDownload(first.item);
    await vi.waitFor(() =>
      expect(electronMocks.showSaveDialog).toHaveBeenCalledOnce(),
    );
    harness.emitDownload(second.item);
    harness.emitDownload(third.item);

    expect(second.spies.cancel).toHaveBeenCalledOnce();
    expect(third.spies.cancel).toHaveBeenCalledOnce();
    expect(electronMocks.showMessageBox).toHaveBeenCalledOnce();

    resolveSaveDialog?.({ canceled: true });
    await vi.waitFor(() => expect(first.spies.cancel).toHaveBeenCalledOnce());
  });
});
