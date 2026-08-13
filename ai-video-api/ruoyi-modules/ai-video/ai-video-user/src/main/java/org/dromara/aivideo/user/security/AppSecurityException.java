package org.dromara.aivideo.user.security;

/**
 * 创作端认证边界专用异常。
 *
 * <p>只允许认证、会话、客户端策略等边界组件抛出该异常，避免把未来创作业务的
 * {@code ServiceException} 统一改写为认证错误响应。</p>
 */
public final class AppSecurityException extends RuntimeException {

    private final int code;

    /**
     * 创建带稳定业务码的创作端安全异常。
     *
     * @param message 内部诊断信息，不直接作为 HTTP 响应返回
     * @param code 创作端认证边界错误码
     */
    public AppSecurityException(String message, int code) {
        super(message);
        this.code = code;
    }

    /**
     * @return 创作端认证边界错误码
     */
    public int getCode() {
        return code;
    }
}
