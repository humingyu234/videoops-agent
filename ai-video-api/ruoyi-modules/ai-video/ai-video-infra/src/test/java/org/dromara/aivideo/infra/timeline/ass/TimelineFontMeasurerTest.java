package org.dromara.aivideo.infra.timeline.ass;

import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureCommandDTO;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class TimelineFontMeasurerTest {

    @TempDir
    Path temporaryDirectory;

    private Path fontRoot;
    private Path fontFile;
    private TimelineFontMeasurer measurer;

    @BeforeEach
    void setUp() throws Exception {
        fontRoot = Files.createDirectories(temporaryDirectory.resolve("approved-fonts"));
        fontFile = Files.writeString(fontRoot.resolve("fixture-font.otf"), "fixture-font", StandardCharsets.UTF_8);
        TimelineFontMeasurer.FontDefinition definition = new TimelineFontMeasurer.FontDefinition("fixture_font",
            "Fixture Font", "1.0", "fixture-font.otf", sha256(fontFile));
        measurer = new TimelineFontMeasurer(fontRoot, Map.of(definition.fontCode(), definition), "a".repeat(64),
            new FakeFontBackend());
    }

    @Test
    void measuresCjkAsciiCombiningCharactersAndEmojiFromTheRegisteredFontOnly() {
        var supported = measurer.measure(command("中文 Café"));
        var emoji = measurer.measure(command("中文😀"));

        assertThat(supported).extracting(result -> result.requestId(), result -> result.fontCode(),
                result -> result.fontVersion(), result -> result.fontSha256(), result -> result.fontRegistrySha256(),
                result -> result.allCodePointsSupported())
            .containsExactly("measure-1", "fixture_font", "1.0", sha256Unchecked(fontFile), "a".repeat(64), true);
        assertThat(supported.widthPx()).isPositive();
        assertThat(supported.heightPx()).isPositive();
        assertThat(emoji.allCodePointsSupported()).isFalse();
    }

    @Test
    void makesMeasurementDeterministicAcrossFontSizeAndRejectsMissingOrOutOfRangeFonts() throws Exception {
        var small = measurer.measure(command("文字", 24, 1));
        var large = measurer.measure(command("文字", 48, 4));

        assertThat(large.widthPx()).isGreaterThan(small.widthPx());
        assertThat(large.heightPx()).isGreaterThan(small.heightPx());
        assertThatThrownBy(() -> measurer.measure(new TimelineTextMeasureCommandDTO("measure-1", "unknown", "文字",
            24, 1080, 1, new BigDecimal("0.05"))))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(TimelineErrorCodes.TIMELINE_FONT_UNAVAILABLE));
        assertThatThrownBy(() -> measurer.measure(command("文字", 121, 1)))
            .isInstanceOf(IllegalArgumentException.class);
        Files.writeString(fontFile, "tampered-font", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> measurer.measure(command("text")))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(TimelineErrorCodes.TIMELINE_FONT_UNAVAILABLE));
        Files.delete(fontFile);
        assertThatThrownBy(() -> measurer.measure(command("文字"))).isInstanceOf(ServiceException.class);
    }

    private static TimelineTextMeasureCommandDTO command(String text) {
        return command(text, 48, 2);
    }

    private static TimelineTextMeasureCommandDTO command(String text, int fontSize, int outline) {
        return new TimelineTextMeasureCommandDTO("measure-1", "fixture_font", text, fontSize, 1080, outline,
            new BigDecimal("0.05"));
    }

    private static String sha256(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static String sha256Unchecked(Path path) {
        try {
            return sha256(path);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class FakeFontBackend implements TimelineFontMeasurer.FontBackend {
        @Override
        public TimelineFontMeasurer.FontFace load(Path verifiedFont, int fontSizePx) {
            assertThat(verifiedFont.getFileName().toString()).isEqualTo("fixture-font.otf");
            return new TimelineFontMeasurer.FontFace() {
                @Override
                public TimelineFontMeasurer.FontMetrics measure(String text) {
                    int width = text.codePoints().map(codePoint -> Character.getType(codePoint) == Character.NON_SPACING_MARK
                        ? 0 : fontSizePx).sum();
                    return new TimelineFontMeasurer.FontMetrics(width, fontSizePx);
                }

                @Override
                public boolean canDisplay(int codePoint) {
                    return codePoint != 0x1F600;
                }
            };
        }
    }
}
