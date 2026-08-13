package org.dromara.aivideo.identity.security;

import org.dromara.common.core.exception.ServiceException;

/**
 * 在一次服务调用范围内保存由受信任入口绑定的审计请求信息。
 */
public final class AppAuditRequestContextHolder {

    private static final ThreadLocal<AppAuditRequestContext> CONTEXT = new ThreadLocal<>();

    private AppAuditRequestContextHolder() {
    }

    /**
     * 取得当前审计请求信息；未绑定时拒绝追加审计。
     * 非 HTTP 调用必须先显式绑定 {@link AppAuditRequestContext#nonHttp()}。
     *
     * @return 当前审计请求信息
     */
    public static AppAuditRequestContext current() {
        AppAuditRequestContext context = CONTEXT.get();
        if (context == null) {
            throw new ServiceException("审计请求上下文未绑定");
        }
        return context;
    }

    /**
     * 绑定受信任入口已解析的审计请求信息，并在关闭作用域时恢复上一上下文。
     *
     * @param context 受信任入口已解析的审计请求信息
     * @return 可关闭的上下文作用域
     */
    public static Scope bindTrusted(AppAuditRequestContext context) {
        if (context == null) {
            throw new ServiceException("审计请求上下文不能为空");
        }
        AppAuditRequestContext previous = CONTEXT.get();
        CONTEXT.set(context);
        return () -> {
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        };
    }

    /**
     * 可关闭的审计上下文绑定作用域。
     */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        /**
         * 恢复调用前的审计请求上下文。
         */
        @Override
        void close();
    }
}
