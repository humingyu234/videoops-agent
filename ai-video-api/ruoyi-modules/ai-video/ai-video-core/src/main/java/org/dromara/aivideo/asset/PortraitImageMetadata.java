package org.dromara.aivideo.asset;

/**
 * 经服务端确认的人物照片文件事实。
 */
public record PortraitImageMetadata(String format, int width, int height, long size) {

    public String contentType() {
        return switch (format) {
            case "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> throw new IllegalStateException("Unsupported portrait image format: " + format);
        };
    }

    public String fileSuffix() {
        return switch (format) {
            case "jpeg" -> ".jpg";
            case "png" -> ".png";
            case "webp" -> ".webp";
            case "gif" -> ".gif";
            default -> throw new IllegalStateException("Unsupported portrait image format: " + format);
        };
    }
}
