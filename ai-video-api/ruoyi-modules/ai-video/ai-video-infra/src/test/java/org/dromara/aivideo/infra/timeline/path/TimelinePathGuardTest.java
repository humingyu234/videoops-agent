package org.dromara.aivideo.infra.timeline.path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("dev")
class TimelinePathGuardTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsAnExistingRegularInputAndAControlledNewOutput() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("approved-root"));
        Path input = Files.writeString(root.resolve("input.mp4"), "fixture");
        TimelinePathGuard guard = new TimelinePathGuard(root);

        assertThat(guard.requireExistingFile(input)).isEqualTo(input.toRealPath());

        Path prepared = guard.prepareOutputFile(root.resolve("output.mp4"));
        assertThat(prepared.getParent()).isEqualTo(root.toRealPath());
        assertThat(prepared.getFileName()).hasToString("output.mp4");
        try (SeekableByteChannel output = Files.newByteChannel(prepared, StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            output.write(ByteBuffer.wrap("rendered".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        assertThat(guard.verifyCreatedOutputFile(prepared)).isEqualTo(prepared.toRealPath());
    }

    @Test
    void rejectsTraversalRelativePathsAndParentsOutsideTheApprovedRoot() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("approved-root"));
        Path input = Files.writeString(root.resolve("input.mp4"), "fixture");
        TimelinePathGuard guard = new TimelinePathGuard(root);

        assertPathRejected(() -> guard.requireExistingFile(Path.of("input.mp4")));
        assertPathRejected(() -> guard.requireExistingFile(root.resolve("nested").resolve("..").resolve("input.mp4")));
        assertPathRejected(() -> guard.prepareOutputFile(temporaryDirectory.resolve("outside.mp4")));
        assertPathRejected(() -> guard.prepareOutputFile(root.resolve("missing-parent").resolve("output.mp4")));
        assertThat(guard.requireExistingFile(input)).isEqualTo(input.toRealPath());
    }

    @Test
    void rejectsSymbolicLinksAndRealPathEscapesForInputsAndOutputs() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("approved-root"));
        Path outside = Files.createDirectories(temporaryDirectory.resolve("outside"));
        Path outsideFile = Files.writeString(outside.resolve("outside.mp4"), "fixture");
        Path insideFile = Files.writeString(root.resolve("inside.mp4"), "fixture");
        Path inputLink = root.resolve("linked-input.mp4");
        Path inRootInputLink = root.resolve("linked-inside-input.mp4");
        Path outputParentLink = root.resolve("linked-output");
        createSymbolicLinkOrSkip(inputLink, outsideFile);
        createSymbolicLinkOrSkip(inRootInputLink, insideFile);
        createSymbolicLinkOrSkip(outputParentLink, outside);
        TimelinePathGuard guard = new TimelinePathGuard(root);

        assertPathRejected(() -> guard.requireExistingFile(inputLink));
        assertPathRejected(() -> guard.requireExistingFile(inRootInputLink));
        assertPathRejected(() -> guard.prepareOutputFile(outputParentLink.resolve("output.mp4")));
    }

    @Test
    void rejectsAnOutputWhoseAncestorIsReplacedAfterPreparation() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("approved-root"));
        Path nested = Files.createDirectories(root.resolve("nested"));
        TimelinePathGuard guard = new TimelinePathGuard(root);
        Path prepared = guard.prepareOutputFile(nested.resolve("output.mp4"));

        Files.delete(nested);
        Files.writeString(nested, "not-a-directory");

        assertPathRejected(() -> guard.verifyCreatedOutputFile(prepared));
    }

    @Test
    void rejectsAnOutputWhoseAncestorBecomesAnInRootSymbolicLinkAfterPreparation() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("approved-root"));
        Path nested = Files.createDirectories(root.resolve("nested"));
        Path replacement = Files.createDirectories(root.resolve("replacement"));
        TimelinePathGuard guard = new TimelinePathGuard(root);
        Path prepared = guard.prepareOutputFile(nested.resolve("output.mp4"));

        Files.delete(nested);
        createSymbolicLinkOrSkip(nested, replacement);
        Files.writeString(replacement.resolve("output.mp4"), "replacement");

        assertPathRejected(() -> guard.verifyCreatedOutputFile(prepared));
    }

    @Test
    void rejectsAnExistingOutputAndARootThatIsItselfAReparseLikeLink() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("approved-root"));
        Path existingOutput = Files.writeString(root.resolve("already-exists.mp4"), "fixture");
        TimelinePathGuard guard = new TimelinePathGuard(root);

        assertPathRejected(() -> guard.prepareOutputFile(existingOutput));

        Path rootLink = temporaryDirectory.resolve("approved-root-link");
        createSymbolicLinkOrSkip(rootLink, root);
        assertPathRejected(() -> new TimelinePathGuard(rootLink));
    }

    @Test
    void resolvesWindowsSeparatorAndCaseVariantsOnlyThroughTheRealApprovedRoot() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"));
        Path root = Files.createDirectories(temporaryDirectory.resolve("approved-root"));
        Path input = Files.writeString(root.resolve("input.mp4"), "fixture");
        TimelinePathGuard guard = new TimelinePathGuard(root);
        Path separatorVariant = Path.of(root.toString().replace('\\', '/')).resolve("INPUT.MP4");

        assertThat(guard.requireExistingFile(separatorVariant)).isEqualTo(input.toRealPath());
    }

    private static void createSymbolicLinkOrSkip(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | SecurityException exception) {
            assumeTrue(false, "symbolic-link tests require local link support");
        } catch (IOException exception) {
            assumeTrue(false, "symbolic-link tests require local link permission");
        }
    }

    private static void assertPathRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(IllegalArgumentException.class);
    }
}
