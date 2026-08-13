package org.dromara.aivideo.asset;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class VoiceSampleValidatorTest {

    private final VoiceSampleValidator validator = new VoiceSampleValidator();

    @Test
    void acceptsMatchingWavSample() {
        byte[] wav = "RIFF1234WAVEfmt ".getBytes();
        VoiceSampleMetadata result = validator.validate(
            "sample.wav", "audio/wav", wav.length, new ByteArrayInputStream(wav));
        assertThat(result.format()).isEqualTo("wav");
    }

    @Test
    void acceptsMp3MimeAliasesAndGenericMimeWhenHeaderMatches() {
        byte[] mp3 = mp3Frames(0);
        for (String mime : new String[] {
            "audio/mpeg", "audio/mp3", "audio/x-mpeg", "audio/mpeg3",
            "audio/x-mpeg-3", "", "application/octet-stream"
        }) {
            VoiceSampleMetadata result = validator.validate(
                "sample.MP3", mime, mp3.length, new ByteArrayInputStream(mp3));
            assertThat(result.format()).isEqualTo("mp3");
        }
    }

    @Test
    void acceptsMp3MimeParameters() {
        byte[] mp3 = mp3Frames(0);
        VoiceSampleMetadata result = validator.validate(
            "sample.mp3", "Audio/MPEG; codecs=mp3", mp3.length, new ByteArrayInputStream(mp3));
        assertThat(result.format()).isEqualTo("mp3");
    }

    @Test
    void acceptsValidId3v2Header() {
        byte[] id3 = {'I', 'D', '3', 4, 0, 0, 0, 0, 0, 0};
        VoiceSampleMetadata result = validator.validate(
            "sample.mp3", "audio/mpeg", id3.length, new ByteArrayInputStream(id3));
        assertThat(result.format()).isEqualTo("mp3");
    }

    @Test
    void acceptsMp3FramesAfterLimitedLeadingBytes() {
        byte[] mp3 = mp3Frames(7);
        VoiceSampleMetadata result = validator.validate(
            "sample.mp3", "audio/mpeg", mp3.length, new ByteArrayInputStream(mp3));
        assertThat(result.format()).isEqualTo("mp3");
    }

    @Test
    void rejectsMp3ExtensionWithWavHeader() {
        byte[] wav = "RIFF1234WAVEfmt ".getBytes();
        assertThatThrownBy(() -> validator.validate(
            "sample.mp3", "audio/mpeg", wav.length, new ByteArrayInputStream(wav)))
            .isInstanceOf(ServiceException.class)
            .extracting("code").isEqualTo(46201);
    }

    @Test
    void rejectsExplicitConflictingMimeForMp3() {
        byte[] mp3 = mp3Frames(0);
        assertThatThrownBy(() -> validator.validate(
            "sample.mp3", "audio/wav", mp3.length, new ByteArrayInputStream(mp3)))
            .isInstanceOf(ServiceException.class)
            .extracting("code").isEqualTo(46201);
    }

    @Test
    void rejectsNonEmptyMalformedMimeForMp3() {
        byte[] mp3 = mp3Frames(0);
        assertThatThrownBy(() -> validator.validate(
            "sample.mp3", "; charset=binary", mp3.length, new ByteArrayInputStream(mp3)))
            .isInstanceOf(ServiceException.class)
            .extracting("code").isEqualTo(46201);
    }

    @Test
    void rejectsParameterizedWavMimeToPreserveExistingRule() {
        byte[] wav = "RIFF1234WAVEfmt ".getBytes();
        assertThatThrownBy(() -> validator.validate(
            "sample.wav", "audio/wav; codecs=1", wav.length, new ByteArrayInputStream(wav)))
            .isInstanceOf(ServiceException.class)
            .extracting("code").isEqualTo(46201);
    }

    @Test
    void rejectsIsolatedMpegSyncBytes() {
        byte[] fake = {(byte) 0xff, (byte) 0xfb, (byte) 0x90, 0x64, 0, 0, 0, 0};
        assertThatThrownBy(() -> validator.validate(
            "sample.mp3", "audio/mpeg", fake.length, new ByteArrayInputStream(fake)))
            .isInstanceOf(ServiceException.class)
            .extracting("code").isEqualTo(46201);
    }

    @Test
    void rejectsTruncatedId3Header() {
        byte[] fake = {'I', 'D', '3'};
        assertThatThrownBy(() -> validator.validate(
            "sample.mp3", "audio/mpeg", fake.length, new ByteArrayInputStream(fake)))
            .isInstanceOf(ServiceException.class)
            .extracting("code").isEqualTo(46201);
    }

    @Test
    void rejectsVoiceLargerThanOneHundredMegabytesBeforeReading() {
        assertThatThrownBy(() -> validator.validate(
            "sample.wav", "audio/wav", VoiceSampleValidator.MAX_FILE_SIZE + 1,
            new ByteArrayInputStream(new byte[0])))
            .isInstanceOf(ServiceException.class)
            .extracting("code").isEqualTo(46202);
    }

    private byte[] mp3Frames(int leadingBytes) {
        int frameLength = 417;
        byte[] bytes = new byte[leadingBytes + frameLength * 2];
        writeMp3FrameHeader(bytes, leadingBytes);
        writeMp3FrameHeader(bytes, leadingBytes + frameLength);
        return bytes;
    }

    private void writeMp3FrameHeader(byte[] bytes, int offset) {
        bytes[offset] = (byte) 0xff;
        bytes[offset + 1] = (byte) 0xfb;
        bytes[offset + 2] = (byte) 0x90;
        bytes[offset + 3] = 0x64;
    }
}
