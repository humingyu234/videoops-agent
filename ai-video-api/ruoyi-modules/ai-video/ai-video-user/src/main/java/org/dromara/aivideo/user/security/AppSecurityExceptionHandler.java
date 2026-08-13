package org.dromara.aivideo.user.security;

import cn.dev33.satoken.exception.NotLoginException;
import org.dromara.common.core.domain.R;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 创作端认证错误的安全响应映射。
 *
 * <p>不把客户端策略、令牌或会话内部细节返回给调用方。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "org.dromara.aivideo.user")
public class AppSecurityExceptionHandler {

    /**
     * 统一返回创作端 app 登录态失败，不能泄露具体令牌状态。
     *
     * @param exception Sa-Token 未登录异常
     * @return HTTP 401 和稳定响应体
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<R<Void>> handleNotLoginException(NotLoginException exception) {
        return response(HttpStatus.UNAUTHORIZED, 401, "登录状态异常，请重新登录");
    }

    /**
     * 映射创作端安全业务码到明确 HTTP 状态和不泄露细节的响应体。
     *
     * @param exception 创作端业务异常
     * @return 安全响应
     */
    @ExceptionHandler(AppSecurityException.class)
    public ResponseEntity<R<Void>> handleAppSecurityException(AppSecurityException exception) {
        int code = exception.getCode();
        return switch (code) {
            case AppAuthErrorCodes.APP_AUTH_CREDENTIALS_INVALID ->
                response(HttpStatus.UNAUTHORIZED, code, "账号或登录凭据不正确");
            case AppAuthErrorCodes.APP_ACCOUNT_UNAVAILABLE ->
                response(HttpStatus.FORBIDDEN, code, "创作端账号不可用");
            case AppAuthErrorCodes.APP_AUTH_CLIENT_UNAVAILABLE ->
                response(HttpStatus.UNAUTHORIZED, code, "创作端认证客户端不可用");
            case AppAuthErrorCodes.APP_SESSION_REVISION_STALE ->
                response(HttpStatus.UNAUTHORIZED, code, "登录状态异常，请重新登录");
            case AppAuthErrorCodes.MULTIPLE_AUTH_CREDENTIALS_REJECTED ->
                response(HttpStatus.BAD_REQUEST, code, "认证凭据格式不合法");
            case AppAuthErrorCodes.APP_PASSWORD_RESET_REQUIRED ->
                response(HttpStatus.FORBIDDEN, code, "请先修改密码后再继续操作");
            case AppAuthErrorCodes.APP_ROLE_REVISION_CONFLICT ->
                response(HttpStatus.CONFLICT, code, "角色权限已变化，请刷新后重试");
            default -> response(HttpStatus.BAD_REQUEST, code, "请求处理失败");
        };
    }

    private ResponseEntity<R<Void>> response(HttpStatus status, int code, String message) {
        return ResponseEntity.status(status).body(R.fail(code, message));
    }
}
