import path from 'node:path';

import { app, BrowserWindow, dialog, type Session } from 'electron';

import { installWindowSecurity } from './security';
import type { WebTarget } from './webUrlPolicy';

const LOADING_PAGE_HTML = `<!doctype html>
<html lang="zh-CN">
  <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
  <body>
    <main class="loading" aria-live="polite" aria-label="正在加载素造智能体">
      <span class="spinner" aria-hidden="true"></span>
      <span>正在加载素造智能体</span>
    </main>
    <style>
      :root { color-scheme: light; font-family: system-ui, -apple-system, "Segoe UI", sans-serif; }
      html, body { width: 100%; height: 100%; margin: 0; background: #f5f5f5; }
      .loading { display: flex; width: 100%; height: 100%; align-items: center; justify-content: center; gap: 12px; color: #595959; font-size: 15px; }
      .spinner { width: 20px; height: 20px; border: 3px solid #d9d9d9; border-top-color: #1677ff; border-radius: 50%; animation: spin .8s linear infinite; }
      @keyframes spin { to { transform: rotate(360deg); } }
    </style>
  </body>
</html>`;

function createLoadingPageUrl(): string {
  return `data:text/html;charset=utf-8,${encodeURIComponent(LOADING_PAGE_HTML)}`;
}

function loadTarget(window: BrowserWindow, target: WebTarget): void {
  void window.loadURL(target.href).catch(() => undefined);
}

function loadTargetWithLoadingPage(
  window: BrowserWindow,
  target: WebTarget,
  loadingPageUrl: string,
): void {
  void window
    .loadURL(loadingPageUrl)
    .then(() => {
      if (!window.isDestroyed()) loadTarget(window, target);
    })
    .catch(() => {
      if (!window.isDestroyed()) loadTarget(window, target);
    });
}

export function createMainWindow(
  target: WebTarget,
  desktopSession: Session,
): BrowserWindow {
  const loadingPageUrl = createLoadingPageUrl();
  const window = new BrowserWindow({
    autoHideMenuBar: true,
    backgroundColor: '#f5f5f5',
    height: 900,
    minHeight: 720,
    minWidth: 1024,
    show: true,
    title: '素造智能体',
    webPreferences: {
      allowRunningInsecureContent: false,
      contextIsolation: true,
      devTools: target.mode === 'development',
      nodeIntegration: false,
      preload: path.join(__dirname, '../preload/index.cjs'),
      sandbox: true,
      session: desktopSession,
      webSecurity: true,
      webviewTag: false,
    },
    width: 1440,
  });

  installWindowSecurity(window, target, loadingPageUrl);

  let loadDialogOpen = false;
  window.webContents.on(
    'did-fail-load',
    (_event, errorCode, errorDescription, _validatedUrl, isMainFrame) => {
      if (!isMainFrame || errorCode === -3 || loadDialogOpen || window.isDestroyed()) {
        return;
      }

      loadDialogOpen = true;
      void dialog
        .showMessageBox(window, {
          buttons: ['重试', '退出'],
          cancelId: 1,
          defaultId: 0,
          detail: errorDescription,
          message: '无法连接用户端网页，请检查网络后重试。',
          noLink: true,
          title: '加载失败',
          type: 'error',
        })
        .then(({ response }) => {
          if (response === 0 && !window.isDestroyed()) {
            loadTargetWithLoadingPage(window, target, loadingPageUrl);
          }
          else app.quit();
        })
        .finally(() => {
          loadDialogOpen = false;
        });
    },
  );

  let crashDialogOpen = false;
  window.webContents.on('render-process-gone', (_event, details) => {
    if (details.reason === 'clean-exit' || crashDialogOpen || window.isDestroyed()) {
      return;
    }

    crashDialogOpen = true;
    void dialog
      .showMessageBox(window, {
        buttons: ['重新加载', '退出'],
        cancelId: 1,
        defaultId: 0,
        message: '桌面页面进程异常退出。',
        noLink: true,
        title: '页面异常',
        type: 'error',
      })
      .then(({ response }) => {
        if (response === 0 && !window.isDestroyed()) {
          loadTargetWithLoadingPage(window, target, loadingPageUrl);
        }
        else app.quit();
      })
      .finally(() => {
        crashDialogOpen = false;
      });
  });

  loadTargetWithLoadingPage(window, target, loadingPageUrl);
  return window;
}
