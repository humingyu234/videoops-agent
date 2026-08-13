package org.dromara.aivideo.asset;

import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * 人物照片类型一致性和安全解码校验器。
 */
@Component
public class PortraitImageValidator {

    public static final int FILE_TYPE_NOT_ALLOWED = 46201;
    public static final int FILE_SIZE_EXCEEDED = 46202;
    public static final int IMAGE_DIMENSIONS_EXCEEDED = 46203;
    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    public static final int MAX_IMAGE_DIMENSION = 12_000;
    public static final long MAX_PIXEL_COUNT = 25_000_000L;

    /**
     * 只接受扩展名、MIME、文件头和解码结果一致的 JPEG/PNG/WebP/GIF。
     */
    public PortraitImageMetadata validate(String fileName, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw typeError();
        }
        if (content.length > MAX_FILE_SIZE) {
            throw new ServiceException("人物照片不能超过 10MB", FILE_SIZE_EXCEEDED);
        }
        String extension = extension(fileName);
        String mime = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        String format = magicFormat(content);
        if (!matches(extension, mime, format)) {
            throw typeError();
        }
        int[] containerDimensions = "webp".equals(format) ? webpDimensions(content) : null;
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(content);
             ImageInputStream input = ImageIO.createImageInputStream(bytes)) {
            if (input == null) throw typeError();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw typeError();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!format.equals(normalizeReaderFormat(reader.getFormatName()))) throw typeError();
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (containerDimensions != null
                    && (containerDimensions[0] != width || containerDimensions[1] != height)) throw typeError();
                validateDimensions(width, height);
                BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() != width || image.getHeight() != height) throw typeError();
                return new PortraitImageMetadata(format, width, height, content.length);
            } finally {
                reader.dispose();
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ServiceException("人物照片无法安全解码", FILE_TYPE_NOT_ALLOWED);
        }
    }

    void validateDimensions(int width, int height) {
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
            || pixels > MAX_PIXEL_COUNT) {
            throw new ServiceException("人物照片像素尺寸过大", IMAGE_DIMENSIONS_EXCEEDED);
        }
    }

    private String normalizeReaderFormat(String readerFormat) {
        String normalized = readerFormat == null ? "" : readerFormat.trim().toLowerCase(Locale.ROOT);
        return "jpg".equals(normalized) ? "jpeg" : normalized;
    }

    private String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        String normalized = fileName.trim().toLowerCase(Locale.ROOT);
        int index = normalized.lastIndexOf('.');
        return index < 0 ? "" : normalized.substring(index + 1);
    }

    private boolean matches(String extension, String mime, String format) {
        return switch (format) {
            case "jpeg" -> ("jpg".equals(extension) || "jpeg".equals(extension)) && "image/jpeg".equals(mime);
            case "png" -> "png".equals(extension) && "image/png".equals(mime);
            case "webp" -> "webp".equals(extension) && "image/webp".equals(mime);
            case "gif" -> "gif".equals(extension) && "image/gif".equals(mime);
            default -> false;
        };
    }

    private String magicFormat(byte[] content) {
        if (content.length >= 8
            && (content[0] & 0xff) == 0x89 && content[1] == 0x50 && content[2] == 0x4e && content[3] == 0x47
            && content[4] == 0x0d && content[5] == 0x0a && content[6] == 0x1a && content[7] == 0x0a) {
            return "png";
        }
        if (content.length >= 3
            && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff) {
            return "jpeg";
        }
        if (content.length >= 6 && content[0] == 'G' && content[1] == 'I' && content[2] == 'F'
            && content[3] == '8' && (content[4] == '7' || content[4] == '9') && content[5] == 'a') {
            return "gif";
        }
        if (content.length >= 12 && content[0] == 'R' && content[1] == 'I' && content[2] == 'F'
            && content[3] == 'F' && content[8] == 'W' && content[9] == 'E' && content[10] == 'B'
            && content[11] == 'P') {
            return "webp";
        }
        return "";
    }

    private int[] webpDimensions(byte[] content) {
        if (unsignedInt(content, 4) != content.length - 8L) throw typeError();
        int offset = 12;
        int imageWidth = 0;
        int imageHeight = 0;
        int canvasWidth = 0;
        int canvasHeight = 0;
        boolean seenExtended = false;
        boolean seenImage = false;
        while (offset < content.length) {
            if (content.length - offset < 8) throw typeError();
            long chunkSize = unsignedInt(content, offset + 4);
            long payloadStart = offset + 8L;
            long payloadEnd = payloadStart + chunkSize;
            long paddedEnd = payloadEnd + (chunkSize & 1L);
            if (payloadEnd > content.length || paddedEnd > content.length) throw typeError();
            int start = (int) payloadStart;
            if (chunkType(content, offset, "VP8 ")) {
                if (seenImage) throw typeError();
                seenImage = true;
                if (chunkSize <= 10 || (content[start + 3] & 0xff) != 0x9d
                    || (content[start + 4] & 0xff) != 0x01 || (content[start + 5] & 0xff) != 0x2a) {
                    throw typeError();
                }
                long frameTag = (content[start] & 0xffL) | ((content[start + 1] & 0xffL) << 8)
                    | ((content[start + 2] & 0xffL) << 16);
                long firstPartitionSize = frameTag >>> 5;
                if ((frameTag & 1L) != 0 || ((frameTag >>> 1) & 0x7L) > 3
                    || ((frameTag >>> 4) & 1L) != 1 || chunkSize <= 10L + firstPartitionSize) {
                    throw typeError();
                }
                imageWidth = littleEndian16(content, start + 6) & 0x3fff;
                imageHeight = littleEndian16(content, start + 8) & 0x3fff;
            } else if (chunkType(content, offset, "VP8L")) {
                if (seenImage) throw typeError();
                seenImage = true;
                if (chunkSize <= 5 || (content[start] & 0xff) != 0x2f || (content[start + 4] & 0xe0) != 0) {
                    throw typeError();
                }
                imageWidth = 1 + (content[start + 1] & 0xff) + ((content[start + 2] & 0x3f) << 8);
                imageHeight = 1 + ((content[start + 2] & 0xc0) >> 6)
                    + ((content[start + 3] & 0xff) << 2) + ((content[start + 4] & 0x0f) << 10);
            } else if (chunkType(content, offset, "VP8X")) {
                if (seenExtended || offset != 12 || chunkSize != 10 || (content[start] & 0xc1) != 0
                    || content[start + 1] != 0 || content[start + 2] != 0 || content[start + 3] != 0) {
                    throw typeError();
                }
                seenExtended = true;
                canvasWidth = 1 + littleEndian24(content, start + 4);
                canvasHeight = 1 + littleEndian24(content, start + 7);
            }
            offset = (int) paddedEnd;
        }
        if (offset != content.length || imageWidth <= 0 || imageHeight <= 0
            || (seenExtended && (canvasWidth != imageWidth || canvasHeight != imageHeight))) {
            throw typeError();
        }
        return new int[]{imageWidth, imageHeight};
    }

    private boolean chunkType(byte[] content, int offset, String type) {
        return content[offset] == type.charAt(0) && content[offset + 1] == type.charAt(1)
            && content[offset + 2] == type.charAt(2) && content[offset + 3] == type.charAt(3);
    }

    private long unsignedInt(byte[] content, int offset) {
        return (content[offset] & 0xffL) | ((content[offset + 1] & 0xffL) << 8)
            | ((content[offset + 2] & 0xffL) << 16) | ((content[offset + 3] & 0xffL) << 24);
    }

    private int littleEndian16(byte[] content, int offset) {
        return (content[offset] & 0xff) | ((content[offset + 1] & 0xff) << 8);
    }

    private int littleEndian24(byte[] content, int offset) {
        return (content[offset] & 0xff) | ((content[offset + 1] & 0xff) << 8)
            | ((content[offset + 2] & 0xff) << 16);
    }

    private ServiceException typeError() {
        return new ServiceException("只支持类型一致且可安全解码的 JPG、JPEG、PNG、WebP、GIF 人物照片", FILE_TYPE_NOT_ALLOWED);
    }
}
