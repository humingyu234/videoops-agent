package org.dromara.aivideo.asset;

import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** MP3/WAV/M4A 扩展名、MIME 和文件头一致性校验。 */
@Component
public class VoiceSampleValidator {
    public static final int FILE_TYPE_NOT_ALLOWED = 46201;
    public static final int FILE_SIZE_EXCEEDED = 46202;
    public static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
    private static final int MP3_SCAN_LIMIT = 64 * 1024;
    private static final int[] MPEG1_LAYER3_BITRATES =
        {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
    private static final int[] MPEG2_LAYER3_BITRATES =
        {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};
    private static final int[] MPEG1_SAMPLE_RATES = {44100, 48000, 32000};
    private static final int[] MPEG2_SAMPLE_RATES = {22050, 24000, 16000};
    private static final int[] MPEG25_SAMPLE_RATES = {11025, 12000, 8000};

    public VoiceSampleMetadata validate(String fileName, String contentType, long size, InputStream input) {
        if (size <= 0 || input == null) {
            throw typeError();
        }
        if (size > MAX_FILE_SIZE) {
            throw new ServiceException("声音文件不能超过 100MB", FILE_SIZE_EXCEEDED);
        }
        String extension = extension(fileName);
        String mime = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        String mp3Mime = normalizeMp3Mime(mime);
        byte[] header;
        try {
            input.mark(MP3_SCAN_LIMIT + 1);
            header = input.readNBytes(MP3_SCAN_LIMIT);
            input.reset();
        } catch (IOException exception) {
            throw typeError();
        }
        String format = magicFormat(header, size);
        boolean mp3 = "mp3".equals(extension) && isMp3Mime(mime, mp3Mime) && "mp3".equals(format);
        boolean wav = "wav".equals(extension)
            && ("audio/wav".equals(mime) || "audio/x-wav".equals(mime)) && "wav".equals(format);
        boolean m4a = "m4a".equals(extension)
            && ("audio/mp4".equals(mime) || "audio/x-m4a".equals(mime)) && "m4a".equals(format);
        if (!mp3 && !wav && !m4a) {
            throw typeError();
        }
        return new VoiceSampleMetadata(format, size);
    }

    private String normalizeMp3Mime(String mime) {
        int parameterIndex = mime.indexOf(';');
        return (parameterIndex < 0 ? mime : mime.substring(0, parameterIndex)).trim();
    }

    private boolean isMp3Mime(String suppliedMime, String normalizedMime) {
        if (suppliedMime.isEmpty()) {
            return true;
        }
        return switch (normalizedMime) {
            case "application/octet-stream", "audio/mpeg", "audio/mp3",
                 "audio/x-mpeg", "audio/mpeg3", "audio/x-mpeg-3" -> true;
            default -> false;
        };
    }

    private String extension(String fileName) {
        if (fileName == null) return "";
        int index = fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String magicFormat(byte[] header, long size) {
        if (header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F'
            && header[3] == 'F' && header[8] == 'W' && header[9] == 'A' && header[10] == 'V'
            && header[11] == 'E') {
            return "wav";
        }
        if (header.length >= 12 && header[4] == 'f' && header[5] == 't'
            && header[6] == 'y' && header[7] == 'p') {
            return "m4a";
        }
        if (hasValidId3Header(header, size) || hasConsecutiveMp3Frames(header)) {
            return "mp3";
        }
        return "";
    }

    private boolean hasValidId3Header(byte[] header, long size) {
        if (header.length < 10 || header[0] != 'I' || header[1] != 'D' || header[2] != '3') {
            return false;
        }
        if ((header[3] & 0xff) == 0xff || (header[4] & 0xff) == 0xff) {
            return false;
        }
        for (int index = 6; index <= 9; index++) {
            if ((header[index] & 0x80) != 0) {
                return false;
            }
        }
        long tagSize = ((long) header[6] << 21)
            | ((long) header[7] << 14)
            | ((long) header[8] << 7)
            | header[9];
        return 10L + tagSize <= size;
    }

    private boolean hasConsecutiveMp3Frames(byte[] header) {
        for (int offset = 0; offset <= header.length - 4; offset++) {
            Mp3FrameHeader first = parseMp3FrameHeader(header, offset);
            if (first == null) {
                continue;
            }
            int nextOffset = offset + first.frameLength();
            Mp3FrameHeader second = parseMp3FrameHeader(header, nextOffset);
            if (second != null && first.versionBits() == second.versionBits()
                && first.sampleRateIndex() == second.sampleRateIndex()) {
                return true;
            }
        }
        return false;
    }

    private Mp3FrameHeader parseMp3FrameHeader(byte[] bytes, int offset) {
        if (offset < 0 || offset > bytes.length - 4) {
            return null;
        }
        int value = ((bytes[offset] & 0xff) << 24)
            | ((bytes[offset + 1] & 0xff) << 16)
            | ((bytes[offset + 2] & 0xff) << 8)
            | (bytes[offset + 3] & 0xff);
        if ((value & 0xffe00000) != 0xffe00000) {
            return null;
        }
        int versionBits = (value >>> 19) & 0x03;
        int layerBits = (value >>> 17) & 0x03;
        int bitrateIndex = (value >>> 12) & 0x0f;
        int sampleRateIndex = (value >>> 10) & 0x03;
        if (versionBits == 1 || layerBits != 1 || bitrateIndex == 0 || bitrateIndex == 15
            || sampleRateIndex == 3) {
            return null;
        }
        int bitrate = versionBits == 3
            ? MPEG1_LAYER3_BITRATES[bitrateIndex]
            : MPEG2_LAYER3_BITRATES[bitrateIndex];
        int sampleRate = sampleRate(versionBits, sampleRateIndex);
        int padding = (value >>> 9) & 0x01;
        int coefficient = versionBits == 3 ? 144 : 72;
        int frameLength = coefficient * bitrate * 1000 / sampleRate + padding;
        return new Mp3FrameHeader(frameLength, versionBits, sampleRateIndex);
    }

    private int sampleRate(int versionBits, int sampleRateIndex) {
        return switch (versionBits) {
            case 3 -> MPEG1_SAMPLE_RATES[sampleRateIndex];
            case 2 -> MPEG2_SAMPLE_RATES[sampleRateIndex];
            case 0 -> MPEG25_SAMPLE_RATES[sampleRateIndex];
            default -> throw new IllegalArgumentException("Unsupported MPEG version");
        };
    }

    private ServiceException typeError() {
        return new ServiceException("只支持扩展名、MIME 和文件头一致的 MP3、WAV、M4A", FILE_TYPE_NOT_ALLOWED);
    }

    private record Mp3FrameHeader(int frameLength, int versionBits, int sampleRateIndex) {
    }
}
