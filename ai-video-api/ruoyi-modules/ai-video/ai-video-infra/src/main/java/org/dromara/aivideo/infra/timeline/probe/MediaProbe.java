package org.dromara.aivideo.infra.timeline.probe;

/**
 * Fixed, locally verified media facts retained from one bounded ffprobe response.
 */
public record MediaProbe(
    String formatName,
    long durationMs,
    long fileSize,
    Integer width,
    Integer height,
    Integer frameRate,
    Integer sampleRate,
    Integer channels,
    boolean videoStream,
    boolean audioStream,
    String videoCodec,
    String audioCodec,
    String securitySummary
) {

    public MediaProbe {
        formatName = requireFormat(formatName);
        if (durationMs < 0 || fileSize <= 0) {
            throw invalid();
        }
        if (videoStream) {
            width = requirePositive(width);
            height = requirePositive(height);
            frameRate = requirePositive(frameRate);
            videoCodec = requireCodec(videoCodec);
        } else if (width != null || height != null || frameRate != null || videoCodec != null) {
            throw invalid();
        }
        if (audioStream) {
            sampleRate = requirePositive(sampleRate);
            channels = requirePositive(channels);
            audioCodec = requireCodec(audioCodec);
        } else if (sampleRate != null || channels != null || audioCodec != null) {
            throw invalid();
        }
        securitySummary = requireSummary(securitySummary);
    }

    private static String requireFormat(String value) {
        if (!safeToken(value, 128, true)) {
            throw invalid();
        }
        return value;
    }

    private static String requireCodec(String value) {
        if (!safeToken(value, 64, false)) {
            throw invalid();
        }
        return value;
    }

    private static Integer requirePositive(Integer value) {
        if (value == null || value <= 0) {
            throw invalid();
        }
        return value;
    }

    private static String requireSummary(String value) {
        if (value == null || value.isBlank() || value.length() > 512) {
            throw invalid();
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                && !(character >= 'A' && character <= 'Z')
                && !(character >= '0' && character <= '9')
                && "=;,_+-.".indexOf(character) < 0) {
                throw invalid();
            }
        }
        return value;
    }

    private static boolean safeToken(String value, int maximumLength, boolean allowComma) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                && !(character >= 'A' && character <= 'Z')
                && !(character >= '0' && character <= '9')
                && "._+-".indexOf(character) < 0
                && !(allowComma && character == ',')) {
                return false;
            }
        }
        return true;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid media probe");
    }
}
