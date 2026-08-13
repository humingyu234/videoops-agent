package org.dromara.aivideo.asset;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class PortraitImageValidatorTest {

    private final PortraitImageValidator validator = new PortraitImageValidator();

    @Test
    void acceptsAConsistentDecodablePng() throws Exception {
        byte[] content = image("png");

        PortraitImageMetadata metadata = validator.validate("portrait.png", "image/png", content);

        assertThat(metadata.format()).isEqualTo("png");
        assertThat(metadata.width()).isEqualTo(2);
        assertThat(metadata.height()).isEqualTo(3);
        assertThat(metadata.size()).isEqualTo(content.length);
    }

    @Test
    void acceptsJpgAliasWithUppercaseMetadata() throws Exception {
        byte[] content = image("jpeg");

        PortraitImageMetadata metadata = validator.validate("PORTRAIT.JPG", "IMAGE/JPEG", content);

        assertMetadata(metadata, "jpeg", "image/jpeg", ".jpg", 2, 3, content.length);
    }

    @Test
    void acceptsJpegAliasWithMixedCaseExtension() throws Exception {
        byte[] content = image("jpeg");

        PortraitImageMetadata metadata = validator.validate("portrait.JpEg", "image/jpeg", content);

        assertMetadata(metadata, "jpeg", "image/jpeg", ".jpg", 2, 3, content.length);
    }

    @Test
    void acceptsGifWithUppercaseMetadata() throws Exception {
        byte[] content = image("gif");

        PortraitImageMetadata metadata = validator.validate("PORTRAIT.GIF", "IMAGE/GIF", content);

        assertMetadata(metadata, "gif", "image/gif", ".gif", 2, 3, content.length);
    }

    @Test
    void acceptsWebpWithMixedCaseMetadata() {
        byte[] content = validWebp();

        PortraitImageMetadata metadata = validator.validate("PORTRAIT.WeBp", "IMAGE/WEBP", content);

        assertMetadata(metadata, "webp", "image/webp", ".webp", 2, 3, content.length);
    }

    @Test
    void acceptsDecodableVp8Webp() {
        byte[] content = Base64.getDecoder().decode(
            "UklGRi4AAABXRUJQVlA4ICIAAABwAQCdASoCAAMAAUAmJZQCdAFAAAD+/DeBV/fU6D4r4AAA"
        );

        PortraitImageMetadata metadata = validator.validate("portrait.webp", "image/webp", content);

        assertMetadata(metadata, "webp", "image/webp", ".webp", 2, 3, content.length);
    }

    @Test
    void rejectsWebpWithCorruptedCompressedPayload() {
        byte[] content = Base64.getDecoder().decode(
            "UklGRi4AAABXRUJQVlA4ICIAAABwAQCdASoCAAMAAUAmJZQCdAFAAAD+/DeBV/fU6D4r4AAA"
        );
        Arrays.fill(content, 35, content.length, (byte) 0xff);

        assertTypeError("portrait.webp", "image/webp", content);
    }

    @Test
    void acceptsDecodableVp8lWebp() {
        byte[] content = Base64.getDecoder().decode(
            "UklGRh4AAABXRUJQVlA4TBEAAAAvAYAAAAdQiirUo/+BiOh/AAA="
        );

        PortraitImageMetadata metadata = validator.validate("portrait.webp", "image/webp", content);

        assertMetadata(metadata, "webp", "image/webp", ".webp", 2, 3, content.length);
    }

    @Test
    void rejectsVp8xContainerWithoutImageDataChunk() {
        byte[] content = new byte[]{
            'R', 'I', 'F', 'F', 22, 0, 0, 0, 'W', 'E', 'B', 'P',
            'V', 'P', '8', 'X', 10, 0, 0, 0,
            0, 0, 0, 0, 1, 0, 0, 2, 0, 0
        };

        assertTypeError("portrait.webp", "image/webp", content);
    }

    @Test
    void rejectsWebpChunksContainingOnlyFrameHeaders() {
        byte[] vp8HeaderOnly = new byte[]{
            'R', 'I', 'F', 'F', 22, 0, 0, 0, 'W', 'E', 'B', 'P',
            'V', 'P', '8', ' ', 10, 0, 0, 0,
            0, 0, 0, (byte) 0x9d, 0x01, 0x2a, 2, 0, 3, 0
        };
        byte[] vp8lHeaderOnly = new byte[]{
            'R', 'I', 'F', 'F', 18, 0, 0, 0, 'W', 'E', 'B', 'P',
            'V', 'P', '8', 'L', 5, 0, 0, 0,
            0x2f, 0x01, (byte) 0x80, 0, 0, 0
        };

        assertTypeError("portrait.webp", "image/webp", vp8HeaderOnly);
        assertTypeError("portrait.webp", "image/webp", vp8lHeaderOnly);
    }

    @Test
    void rejectsVp8xChunkWithNonCanonicalLength() {
        byte[] content = new byte[]{
            'R', 'I', 'F', 'F', 44, 0, 0, 0, 'W', 'E', 'B', 'P',
            'V', 'P', '8', 'X', 11, 0, 0, 0,
            0, 0, 0, 0, 1, 0, 0, 2, 0, 0, 0, 0,
            'V', 'P', '8', ' ', 11, 0, 0, 0,
            0, 0, 0, (byte) 0x9d, 0x01, 0x2a, 2, 0, 3, 0, 0, 0
        };

        assertTypeError("portrait.webp", "image/webp", content);
    }

    @Test
    void rejectsDuplicateAndMixedWebpImageChunks() {
        byte[] vp8 = Base64.getDecoder().decode(
            "UklGRi4AAABXRUJQVlA4ICIAAABwAQCdASoCAAMAAUAmJZQCdAFAAAD+/DeBV/fU6D4r4AAA"
        );
        byte[] vp8l = Base64.getDecoder().decode(
            "UklGRh4AAABXRUJQVlA4TBEAAAAvAYAAAAdQiirUo/+BiOh/AAA="
        );

        assertTypeError("portrait.webp", "image/webp", appendChunk(vp8, vp8, 12, vp8.length - 12));
        assertTypeError("portrait.webp", "image/webp", appendChunk(vp8l, vp8l, 12, vp8l.length - 12));
        assertTypeError("portrait.webp", "image/webp", appendChunk(vp8, vp8l, 12, vp8l.length - 12));
    }

    @Test
    void rejectsDuplicateAndLateVp8xChunks() {
        byte[] extended = validWebp();
        byte[] vp8 = Base64.getDecoder().decode(
            "UklGRi4AAABXRUJQVlA4ICIAAABwAQCdASoCAAMAAUAmJZQCdAFAAAD+/DeBV/fU6D4r4AAA"
        );

        assertTypeError("portrait.webp", "image/webp", appendChunk(extended, extended, 12, 18));
        assertTypeError("portrait.webp", "image/webp", appendChunk(vp8, extended, 12, 18));
    }

    @Test
    void rejectsInvalidVp8FrameTagsAndPartitionLengths() {
        byte[] source = Base64.getDecoder().decode(
            "UklGRi4AAABXRUJQVlA4ICIAAABwAQCdASoCAAMAAUAmJZQCdAFAAAD+/DeBV/fU6D4r4AAA"
        );
        byte[] interFrame = source.clone();
        interFrame[20] |= 0x01;
        byte[] hiddenFrame = source.clone();
        hiddenFrame[20] &= (byte) ~0x10;
        byte[] invalidVersion = source.clone();
        invalidVersion[20] = (byte) ((invalidVersion[20] & ~0x0e) | 0x08);
        byte[] missingCompressedPayload = source.clone();
        missingCompressedPayload[20] = 0x10;
        missingCompressedPayload[21] = 0x03;
        missingCompressedPayload[22] = 0;

        assertTypeError("portrait.webp", "image/webp", interFrame);
        assertTypeError("portrait.webp", "image/webp", hiddenFrame);
        assertTypeError("portrait.webp", "image/webp", invalidVersion);
        assertTypeError("portrait.webp", "image/webp", missingCompressedPayload);
    }

    @Test
    void rejectsVp8lWithNonZeroVersionBits() {
        byte[] content = Base64.getDecoder().decode(
            "UklGRh4AAABXRUJQVlA4TBEAAAAvAYAAAAdQiirUo/+BiOh/AAA="
        );
        content[24] |= 0x20;

        assertTypeError("portrait.webp", "image/webp", content);
    }

    @Test
    void rejectsExtensionMimeAndMagicMismatch() throws Exception {
        byte[] gif = image("gif");

        assertTypeError("portrait.png", "image/gif", gif);
        assertTypeError("portrait.gif", "image/png", gif);
        assertTypeError("portrait.gif", "image/gif", image("png"));
    }

    @Test
    void rejectsTruncatedGifAndWebp() {
        assertTypeError("portrait.gif", "image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII));
        assertTypeError("portrait.webp", "image/webp", Arrays.copyOf(validWebp(), 20));
    }

    @Test
    void rejectsUnsupportedAndEmptyMimeTypes() throws Exception {
        assertTypeError("portrait.svg", "image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8));
        assertTypeError("portrait.bmp", "image/bmp", new byte[]{'B', 'M', 0, 0});
        assertTypeError("portrait.heic", "image/heic", "\0\0\0\u0018ftypheic".getBytes(StandardCharsets.ISO_8859_1));
        assertTypeError("portrait.heif", "image/heif", "\0\0\0\u0018ftypheif".getBytes(StandardCharsets.ISO_8859_1));
        assertTypeError("portrait.avif", "image/avif", "\0\0\0\u0018ftypavif".getBytes(StandardCharsets.ISO_8859_1));
        assertTypeError("portrait.gif", "", image("gif"));
    }

    @Test
    void rejectsNonExactBinaryMagic() {
        byte[] content = validWebp();
        content[0] = 'r';

        assertTypeError("portrait.webp", "image/webp", content);
    }

    @Test
    void rejectsUndecodableAndOversizedFiles() {
        assertTypeError("portrait.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});
        assertThatThrownBy(() -> validator.validate("portrait.png", "image/png", new byte[10 * 1024 * 1024 + 1]))
            .isInstanceOf(ServiceException.class)
            .hasFieldOrPropertyWithValue("code", 46202);
    }

    @Test
    void rejectsOversizedDimensionsBeforeAllocatingDecodedPixels() {
        assertThatThrownBy(() -> validator.validateDimensions(12001, 1))
            .isInstanceOf(ServiceException.class)
            .hasFieldOrPropertyWithValue("code", 46203);
        assertThatThrownBy(() -> validator.validateDimensions(5001, 5000))
            .isInstanceOf(ServiceException.class)
            .hasFieldOrPropertyWithValue("code", 46203);
    }

    private byte[] image(String format) throws Exception {
        BufferedImage image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private byte[] validWebp() {
        return Base64.getDecoder().decode(
            "UklGRlAAAABXRUJQVlA4WAoAAAAQAAAAAQAAAgAAQUxQSAcAAAAAZGRkZGRkAFZQOCAiAAAAcAEAnQEqAgADAAFAJiWUAnQBQAAA/vw3gVf31Og+K+AAAA=="
        );
    }

    private byte[] appendChunk(byte[] container, byte[] chunkSource, int chunkOffset, int chunkLength) {
        byte[] result = Arrays.copyOf(container, container.length + chunkLength);
        System.arraycopy(chunkSource, chunkOffset, result, container.length, chunkLength);
        int riffSize = result.length - 8;
        result[4] = (byte) riffSize;
        result[5] = (byte) (riffSize >>> 8);
        result[6] = (byte) (riffSize >>> 16);
        result[7] = (byte) (riffSize >>> 24);
        return result;
    }

    private void assertMetadata(PortraitImageMetadata metadata, String format, String contentType,
                                String fileSuffix, int width, int height, long size) {
        assertThat(metadata.format()).isEqualTo(format);
        assertThat(metadata.contentType()).isEqualTo(contentType);
        assertThat(metadata.fileSuffix()).isEqualTo(fileSuffix);
        assertThat(metadata.width()).isEqualTo(width);
        assertThat(metadata.height()).isEqualTo(height);
        assertThat(metadata.size()).isEqualTo(size);
    }

    private void assertTypeError(String fileName, String contentType, byte[] content) {
        assertThatThrownBy(() -> validator.validate(fileName, contentType, content))
            .isInstanceOf(ServiceException.class)
            .hasFieldOrPropertyWithValue("code", 46201);
    }
}
