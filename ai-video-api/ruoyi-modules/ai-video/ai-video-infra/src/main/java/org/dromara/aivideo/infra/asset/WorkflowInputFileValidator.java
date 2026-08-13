package org.dromara.aivideo.infra.asset;

import org.dromara.common.core.exception.ServiceException;

import java.util.Locale;

/**
 * Identifies the small, fixed set of media formats accepted as workflow inputs.
 *
 * <p>The browser supplied name and MIME type are untrusted. A file is only eligible
 * for a workflow upload after its leading bytes agree with the declared media type.</p>
 */
public class WorkflowInputFileValidator {

    /** Detects a canonical MIME type from a media header. */
    public String detectContentType(byte[] header) {
        if (matches(header, 0, 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n')) {
            return "image/png";
        }
        if (matches(header, 0, 0xff, 0xd8, 0xff)) {
            return "image/jpeg";
        }
        if (matches(header, 0, 'G', 'I', 'F', '8')) {
            return "image/gif";
        }
        if (matches(header, 0, 'R', 'I', 'F', 'F') && matches(header, 8, 'W', 'A', 'V', 'E')) {
            return "audio/wav";
        }
        if (matches(header, 0, 'I', 'D', '3')
            || (header != null && header.length >= 2 && (header[0] & 0xff) == 0xff && (header[1] & 0xe0) == 0xe0)) {
            return "audio/mpeg";
        }
        if (matches(header, 4, 'f', 't', 'y', 'p')) {
            return "video/mp4";
        }
        throw new ServiceException("无法识别工作流输入文件类型");
    }

    /** Requires the client declaration to match the type identified from file bytes. */
    public void requireDeclaredTypeMatches(String declaredContentType, byte[] header) {
        String actual = detectContentType(header);
        if (declaredContentType == null || !actual.equals(declaredContentType.trim().toLowerCase(Locale.ROOT))) {
            throw new ServiceException("工作流输入文件类型与实际内容不一致");
        }
    }

    private boolean matches(byte[] bytes, int offset, int... expected) {
        if (bytes == null || offset < 0 || bytes.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((bytes[offset + index] & 0xff) != expected[index]) {
                return false;
            }
        }
        return true;
    }
}
