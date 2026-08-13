export type BuildMode = 'development' | 'production';

export interface WebTarget {
  href: string;
  mode: BuildMode;
  origin: string;
}

const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]']);

export function isLoopbackHost(hostname: string): boolean {
  return LOOPBACK_HOSTS.has(hostname.toLowerCase());
}

export function resolveWebTarget(
  raw: string | undefined,
  mode: BuildMode,
): WebTarget {
  const value =
    raw?.trim() || (mode === 'development' ? 'http://localhost:8000' : '');

  if (!value) {
    throw new Error('生产构建必须提供 AI_VIDEO_WEB_URL');
  }

  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new Error('AI_VIDEO_WEB_URL 不是合法 URL');
  }

  if (url.username || url.password) {
    throw new Error('AI_VIDEO_WEB_URL 不得包含用户名或密码凭据');
  }

  if (url.protocol === 'https:') {
    return { href: url.href, mode, origin: url.origin };
  }

  if (
    mode === 'development' &&
    url.protocol === 'http:' &&
    isLoopbackHost(url.hostname)
  ) {
    return { href: url.href, mode, origin: url.origin };
  }

  if (mode === 'development' && url.protocol === 'http:') {
    throw new Error('开发环境 HTTP 地址只能使用本机回环主机');
  }

  throw new Error('生产 AI_VIDEO_WEB_URL 必须使用 HTTPS');
}
