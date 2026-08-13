package org.dromara.aivideo.identity.security;

import org.dromara.common.core.exception.ServiceException;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * 已由受信任请求入口解析的创作端审计请求信息。
 *
 * @param requestId 固定格式的链路追踪编号，非 HTTP 调用使用 {@code non-http}
 * @param ipAddress 已解析的客户端地址，非 HTTP 调用使用 {@code non-http}
 */
public record AppAuditRequestContext(String requestId, String ipAddress) {

    private static final String NON_HTTP_MARKER = "non-http";
    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern IPV6_TEXT = Pattern.compile("[0-9A-Fa-f:.]{2,64}");
    private static final AppAuditRequestContext NON_HTTP = new AppAuditRequestContext(
        NON_HTTP_MARKER, NON_HTTP_MARKER);

    /**
     * 创建受信任入口已规范化的审计请求信息。
     *
     * @param requestId 固定格式的链路追踪编号
     * @param ipAddress 已解析的客户端地址
     */
    public AppAuditRequestContext {
        boolean nonHttpRequest = NON_HTTP_MARKER.equals(requestId);
        boolean nonHttpIp = NON_HTTP_MARKER.equals(ipAddress);
        if (nonHttpRequest || nonHttpIp) {
            if (!nonHttpRequest || !nonHttpIp) {
                throw new ServiceException("非 HTTP 审计上下文必须同时使用 non-http 标记");
            }
        } else {
            if (requestId == null || !TRACE_ID.matcher(requestId).matches()) {
                throw new ServiceException("审计请求追踪编号格式不安全");
            }
            if (!isSpecifiedNumericIpAddress(ipAddress)) {
                throw new ServiceException("审计请求 IP 地址格式不安全");
            }
        }
    }

    /**
     * 返回明确标记的非 HTTP 审计上下文。
     *
     * @return 非 HTTP 审计上下文
     */
    public static AppAuditRequestContext nonHttp() {
        return NON_HTTP;
    }

    private static boolean isSpecifiedNumericIpAddress(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.indexOf(':') >= 0) {
            return isSpecifiedIpv6(value);
        }
        return isSpecifiedIpv4(value);
    }

    private static boolean isSpecifiedIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        boolean allZero = true;
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3
                || (octet.length() > 1 && octet.charAt(0) == '0')) {
                return false;
            }
            int numericValue = 0;
            for (int index = 0; index < octet.length(); index++) {
                char character = octet.charAt(index);
                if (character < '0' || character > '9') {
                    return false;
                }
                numericValue = numericValue * 10 + (character - '0');
            }
            if (numericValue > 255) {
                return false;
            }
            allZero &= numericValue == 0;
        }
        return !allZero;
    }

    private static boolean isSpecifiedIpv6(String value) {
        if (!IPV6_TEXT.matcher(value).matches()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return address instanceof Inet6Address && !address.isAnyLocalAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
