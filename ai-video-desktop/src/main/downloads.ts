import path from 'node:path';

import {
  app,
  dialog,
  type BrowserWindow,
  type DownloadItem,
  type Session,
  type WebContents,
} from 'electron';

import { sanitizeSuggestedFileName } from './downloadPolicy';
import {
  isAllowedDownloadChain,
  isAllowedMainNavigation,
} from './securityPolicy';
import type { WebTarget } from './webUrlPolicy';

type WindowProvider = () => BrowserWindow | undefined;

async function chooseDownloadPath(
  item: DownloadItem,
  webContents: WebContents,
  window: BrowserWindow,
  target: WebTarget,
): Promise<void> {
  if (
    webContents !== window.webContents ||
    !isAllowedMainNavigation(webContents.getURL(), target) ||
    !isAllowedDownloadChain(item.getURLChain(), target)
  ) {
    item.cancel();
    return;
  }

  item.pause();
  const suggestedName = sanitizeSuggestedFileName(item.getFilename());
  const result = await dialog.showSaveDialog(window, {
    defaultPath: path.join(app.getPath('downloads'), suggestedName),
    title: '另存为',
  });

  if (result.canceled || !result.filePath) {
    item.cancel();
    return;
  }

  item.setSavePath(result.filePath);
  if (item.isPaused()) item.resume();

  item.once('done', (_event, state) => {
    if (state === 'interrupted') {
      dialog.showErrorBox('下载失败', '文件下载被中断，请重试。');
    }
  });
}

export function installDownloadHandler(
  session: Session,
  getWindow: WindowProvider,
  target: WebTarget,
): void {
  let dialogOpen = false;
  let concurrentWarningOpen = false;

  session.on('will-download', (_event, item, webContents) => {
    const window = getWindow();
    if (!window || window.isDestroyed()) {
      item.cancel();
      return;
    }

    if (dialogOpen) {
      item.cancel();
      if (!concurrentWarningOpen) {
        concurrentWarningOpen = true;
        void dialog
          .showMessageBox(window, {
            message: '已有下载正在等待保存，请完成当前“另存为”后再逐个下载。',
            noLink: true,
            title: '请逐个下载',
            type: 'info',
          })
          .catch(() => undefined)
          .finally(() => {
            concurrentWarningOpen = false;
          });
      }
      return;
    }

    dialogOpen = true;
    void chooseDownloadPath(item, webContents, window, target)
      .catch(() => {
        item.cancel();
      })
      .finally(() => {
        dialogOpen = false;
      });
  });
}
