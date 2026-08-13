import { app, BrowserWindow, Menu, session, type Session } from 'electron';

import { createMainWindow } from './createWindow';
import { installDownloadHandler } from './downloads';
import {
  installCertificateErrorHandler,
  installSessionSecurity,
} from './security';
import { resolveWebTarget } from './webUrlPolicy';

const APP_ID = 'com.suzao.aivideo';
const SESSION_PARTITION = 'persist:ai-video-web';
const target = resolveWebTarget(
  __AI_VIDEO_WEB_URL__,
  __AI_VIDEO_BUILD_MODE__,
);

let desktopSession: Session | undefined;
let mainWindow: BrowserWindow | undefined;

function getDesktopSession(): Session {
  if (desktopSession) return desktopSession;

  desktopSession = session.fromPartition(SESSION_PARTITION);
  installSessionSecurity(desktopSession);
  installDownloadHandler(desktopSession, () => mainWindow, target);
  return desktopSession;
}

function openMainWindow(): BrowserWindow {
  if (mainWindow && !mainWindow.isDestroyed()) return mainWindow;

  const window = createMainWindow(target, getDesktopSession());
  mainWindow = window;
  window.once('closed', () => {
    if (mainWindow === window) mainWindow = undefined;
  });
  return window;
}

function focusMainWindow(): void {
  const window = openMainWindow();
  if (window.isMinimized()) window.restore();
  window.show();
  window.focus();
}

app.enableSandbox();
app.setAppUserModelId(APP_ID);

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  installCertificateErrorHandler();

  app.on('second-instance', focusMainWindow);
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) openMainWindow();
  });
  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
  });

  void app.whenReady().then(() => {
    Menu.setApplicationMenu(null);
    openMainWindow();
  });
}
