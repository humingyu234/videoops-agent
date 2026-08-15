/**
 * @name 代理的配置
 * @see 在生产环境 代理是无法生效的，所以这里没有生产环境的配置
 * -------------------------------
 * The agent cannot take effect in the production environment
 * so there is no configuration of the production environment
 * For details, please see
 * https://pro.ant.design/docs/deploy
 *
 * @doc https://umijs.org/docs/guides/proxy
 */
const defaultApiTarget =
  process.env.AI_VIDEO_API_ORIGIN ?? 'http://127.0.0.1:18081';
const testApiTarget = process.env.AI_VIDEO_API_TEST_ORIGIN ?? defaultApiTarget;
const preApiTarget = process.env.AI_VIDEO_API_PRE_ORIGIN ?? defaultApiTarget;

function createApiProxy(target: string) {
  return {
    target,
    changeOrigin: true,
    onProxyReq(proxyRequest: ClientRequest) {
      // The local operator UI and creator UI share localhost.  Do not forward
      // an operator's legacy auth cookie to the creator API: creator requests
      // are authenticated exclusively by the managed Bearer header.
      proxyRequest.removeHeader('cookie');
    },
  };
}

export default {
  dev: {
    '/api/': createApiProxy(defaultApiTarget),
  },
  test: {
    '/api/': createApiProxy(testApiTarget),
  },
  pre: {
    '/api/': createApiProxy(preApiTarget),
  },
};
import type { ClientRequest } from 'node:http';
