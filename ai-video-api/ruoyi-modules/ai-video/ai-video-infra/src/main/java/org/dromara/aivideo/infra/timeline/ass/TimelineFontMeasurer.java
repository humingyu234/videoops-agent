package org.dromara.aivideo.infra.timeline.ass;

import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureResultDTO;
import org.dromara.common.core.exception.ServiceException;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * Measures text exclusively through a deployment-owned, hash-verified registered font file.
 *
 * <p>This component never creates a font from a family name and never asks the platform for a
 * fallback font.  A missing glyph is reported in the result so the renderer can fail closed before
 * libass is allowed to substitute a system font.</p>
 */
public final class TimelineFontMeasurer {

    static final String FONT_REGISTRY_SHA256 = "2e0198557dc5a00c4cdde6eb970a3c2282c298f169c3f6bd7349c275156a9e33";
    private static final String REGISTRY_FILE_NAME = "font-registry.json";
    private static final int CANVAS_WIDTH = limit("canvasWidth");
    private static final int MIN_FONT_SIZE = limit("minFontSizePx");
    private static final int MAX_FONT_SIZE = limit("maxFontSizePx");
    private static final int MIN_OUTLINE_WIDTH = limit("minOutlineWidthPx");
    private static final int MAX_OUTLINE_WIDTH = limit("maxOutlineWidthPx");
    private static final BigDecimal SAFE_MARGIN_RATIO = TimelineContractLimits.NUMERIC_LIMITS.get("safeMarginRatio");

    private static final Map<String, FontDefinition> DEFAULT_REGISTRY = Map.of(
        "noto_sans_cjk_sc_regular", new FontDefinition("noto_sans_cjk_sc_regular", "Noto Sans CJK SC", "2.004",
            "NotoSansCJKsc-Regular.otf", "2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b"),
        "noto_serif_cjk_sc_regular", new FontDefinition("noto_serif_cjk_sc_regular", "Noto Serif CJK SC", "2.003",
            "NotoSerifCJKsc-Regular.otf", "2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca")
    );

    private final Path fontRoot;
    private final Map<String, FontDefinition> registry;
    private final String registryHash;
    private final FontBackend backend;
    private final boolean verifyRegistryFile;

    /**
     * Creates the production measurer for the C0 font registry.
     */
    public TimelineFontMeasurer(Path fontRoot) {
        this(fontRoot, DEFAULT_REGISTRY, FONT_REGISTRY_SHA256, new JdkFontBackend(), true);
    }

    /**
     * Test seam for deterministic font facts without using an operating-system font.
     */
    TimelineFontMeasurer(Path fontRoot,
                          Map<String, FontDefinition> registry,
                          String registryHash,
                          FontBackend backend) {
        this(fontRoot, registry, registryHash, backend, false);
    }

    private TimelineFontMeasurer(Path fontRoot,
                                  Map<String, FontDefinition> registry,
                                  String registryHash,
                                  FontBackend backend,
                                  boolean verifyRegistryFile) {
        if (fontRoot == null || !fontRoot.isAbsolute() || containsTraversal(fontRoot)) {
            throw fontUnavailable();
        }
        this.fontRoot = fontRoot.toAbsolutePath().normalize();
        this.registry = Map.copyOf(Objects.requireNonNull(registry, "registry"));
        this.registryHash = requireSha256(registryHash);
        this.backend = Objects.requireNonNull(backend, "backend");
        this.verifyRegistryFile = verifyRegistryFile;
        if (this.registry.isEmpty()) {
            throw fontUnavailable();
        }
    }

    /**
     * Returns deterministic pixel bounds and reports glyph coverage without any system fallback.
     */
    public TimelineTextMeasureResultDTO measure(TimelineTextMeasureCommandDTO command) {
        validateCommand(command);
        FontDefinition definition = registry.get(command.fontCode());
        if (definition == null) {
            throw fontUnavailable();
        }
        Path root = verifyRoot();
        if (verifyRegistryFile) {
            verifyRegistry(root);
        }
        Path fontFile = verifyRegisteredFont(root, definition);
        String text = AssTextEncoder.normalizeTextForMeasurement(command.text());
        try {
            FontFace font = backend.load(fontFile, command.fontSizePx());
            if (font == null) {
                throw fontUnavailable();
            }
            FontMetrics metrics = font.measure(text);
            int width = withOutline(metrics.widthPx(), command.outlineWidthPx());
            int height = withOutline(metrics.heightPx(), command.outlineWidthPx());
            boolean supported = text.codePoints().allMatch(font::canDisplay);
            return new TimelineTextMeasureResultDTO(command.requestId(), definition.fontCode(), definition.version(),
                definition.sha256(), registryHash, width, height, supported);
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw fontUnavailable();
        }
    }

    static FontDefinition requireDefaultDefinition(String fontCode, String fontVersion, String fontSha256) {
        if (fontCode == null || fontVersion == null || fontSha256 == null) {
            throw new IllegalArgumentException("timeline font reference is invalid");
        }
        FontDefinition definition = DEFAULT_REGISTRY.get(fontCode);
        if (definition == null || !definition.version().equals(fontVersion) || !definition.sha256().equals(fontSha256)) {
            throw new IllegalArgumentException("timeline font reference is invalid");
        }
        return definition;
    }

    private static void validateCommand(TimelineTextMeasureCommandDTO command) {
        if (command == null || !safeAsciiKey(command.requestId()) || command.fontCode() == null
            || command.fontCode().isBlank()) {
            throw invalidMeasurement();
        }
        AssTextEncoder.normalizeTextForMeasurement(command.text());
        if (command.fontSizePx() < MIN_FONT_SIZE || command.fontSizePx() > MAX_FONT_SIZE
            || command.outlineWidthPx() < MIN_OUTLINE_WIDTH || command.outlineWidthPx() > MAX_OUTLINE_WIDTH
            || command.canvasWidthPx() != CANVAS_WIDTH
            || command.safeMarginRatio() == null
            || command.safeMarginRatio().compareTo(SAFE_MARGIN_RATIO) != 0) {
            throw invalidMeasurement();
        }
    }

    private Path verifyRoot() {
        try {
            BasicFileAttributes attributes = Files.readAttributes(fontRoot, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()
                || Files.isSymbolicLink(fontRoot) || !Files.isReadable(fontRoot)) {
                throw fontUnavailable();
            }
            Path noFollow = fontRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path following = fontRoot.toRealPath();
            if (!noFollow.equals(following)) {
                throw fontUnavailable();
            }
            return noFollow;
        } catch (IOException | SecurityException exception) {
            throw fontUnavailable();
        }
    }

    private void verifyRegistry(Path root) {
        Path registryFile = requireRegularFile(root, REGISTRY_FILE_NAME);
        if (!FONT_REGISTRY_SHA256.equals(sha256(registryFile))) {
            throw fontUnavailable();
        }
    }

    private Path verifyRegisteredFont(Path root, FontDefinition definition) {
        Path font = requireRegularFile(root, definition.fileName());
        if (!definition.sha256().equals(sha256(font))) {
            throw fontUnavailable();
        }
        return font;
    }

    private Path requireRegularFile(Path root, String fileName) {
        if (fileName == null || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0
            || ".".equals(fileName) || "..".equals(fileName)) {
            throw fontUnavailable();
        }
        Path candidate = root.resolve(fileName);
        try {
            BasicFileAttributes attributes = Files.readAttributes(candidate, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther()
                || Files.isSymbolicLink(candidate) || !Files.isReadable(candidate)) {
                throw fontUnavailable();
            }
            Path noFollow = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path following = candidate.toRealPath();
            if (!noFollow.equals(following) || !noFollow.startsWith(root)) {
                throw fontUnavailable();
            }
            return noFollow;
        } catch (IOException | SecurityException exception) {
            throw fontUnavailable();
        }
    }

    private static int withOutline(int measurement, int outlineWidth) {
        if (measurement <= 0) {
            throw fontUnavailable();
        }
        try {
            return Math.addExact(measurement, Math.multiplyExact(outlineWidth, 2));
        } catch (ArithmeticException exception) {
            throw fontUnavailable();
        }
    }

    private static boolean safeAsciiKey(String value) {
        if (value == null || value.isBlank() || value.length() > limit("maxKeyAsciiLength")) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                && !(character >= 'A' && character <= 'Z')
                && !(character >= '0' && character <= '9')
                && character != '_' && character != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean containsTraversal(Path path) {
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String requireSha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw fontUnavailable();
        }
        return value;
    }

    private static String sha256(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw fontUnavailable();
        }
    }

    private static int limit(String name) {
        return TimelineContractLimits.NUMERIC_LIMITS.get(name).intValueExact();
    }

    private static IllegalArgumentException invalidMeasurement() {
        return new IllegalArgumentException("timeline text measurement is invalid");
    }

    private static ServiceException fontUnavailable() {
        return new ServiceException("timeline font is unavailable", TimelineErrorCodes.TIMELINE_FONT_UNAVAILABLE);
    }

    record FontDefinition(String fontCode, String familyName, String version, String fileName, String sha256) {
        FontDefinition {
            if (!safeAsciiKey(fontCode) || familyName == null || familyName.isBlank() || version == null
                || version.isBlank() || fileName == null || fileName.isBlank()) {
                throw fontUnavailable();
            }
            sha256 = requireSha256(sha256);
        }
    }

    interface FontBackend {
        FontFace load(Path verifiedFont, int fontSizePx);
    }

    interface FontFace {
        FontMetrics measure(String text);

        boolean canDisplay(int codePoint);
    }

    record FontMetrics(int widthPx, int heightPx) {
    }

    private static final class JdkFontBackend implements FontBackend {
        private static final FontRenderContext FONT_RENDER_CONTEXT = new FontRenderContext(new AffineTransform(), true,
            true);

        @Override
        public FontFace load(Path verifiedFont, int fontSizePx) {
            try {
                Font font = Font.createFont(Font.TRUETYPE_FONT, verifiedFont.toFile())
                    .deriveFont(Font.PLAIN, (float) fontSizePx);
                return new FontFace() {
                    @Override
                    public FontMetrics measure(String text) {
                        TextLayout layout = new TextLayout(text, font, FONT_RENDER_CONTEXT);
                        int width = (int) Math.ceil(layout.getAdvance());
                        int height = (int) Math.ceil(layout.getAscent() + layout.getDescent() + layout.getLeading());
                        return new FontMetrics(width, height);
                    }

                    @Override
                    public boolean canDisplay(int codePoint) {
                        return font.canDisplay(codePoint);
                    }
                };
            } catch (IOException | java.awt.FontFormatException exception) {
                throw fontUnavailable();
            }
        }
    }
}
