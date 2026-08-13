package org.dromara.aivideo.infra.timeline.path;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Anchors timeline work paths to one deployment-owned real directory.
 *
 * <p>The configured root must not be writable by untrusted local users. This guard rejects links,
 * reparse-like entries and traversal, and callers must revalidate immediately after creating an output.</p>
 */
public final class TimelinePathGuard {

    private final Path approvedRoot;

    public TimelinePathGuard(Path approvedRoot) {
        this.approvedRoot = requireApprovedRoot(approvedRoot);
    }

    /**
     * Returns the canonical deployment-owned work root.
     */
    public Path approvedRoot() {
        return approvedRoot;
    }

    /**
     * Resolves an existing, readable regular input file beneath the approved root.
     */
    public Path requireExistingFile(Path candidate) {
        Path resolved = requireExisting(candidate, false);
        if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(resolved)) {
            throw rejected();
        }
        return resolved;
    }

    /**
     * Resolves an existing real directory beneath the approved root.
     */
    public Path requireExistingDirectory(Path candidate) {
        return requireExisting(candidate, true);
    }

    /**
     * Validates a not-yet-created output file and its real parent directory.
     * The caller must create the returned file with {@code CREATE_NEW} and {@code NOFOLLOW_LINKS},
     * then call {@link #verifyCreatedOutputFile(Path)} before consuming it.
     */
    public Path prepareOutputFile(Path candidate) {
        Path absolute = requireAbsolutePath(candidate);
        if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw rejected();
        }
        Path fileName = absolute.getFileName();
        Path parent = absolute.getParent();
        if (fileName == null || parent == null || ".".equals(fileName.toString()) || "..".equals(fileName.toString())) {
            throw rejected();
        }
        Path realParent = requireExistingDirectory(parent);
        Path prepared = realParent.resolve(fileName);
        if (Files.exists(prepared, LinkOption.NOFOLLOW_LINKS)) {
            throw rejected();
        }
        return prepared;
    }

    /**
     * Revalidates a file after it was created by the current timeline operation.
     */
    public Path verifyCreatedOutputFile(Path candidate) {
        return requireExistingFile(candidate);
    }

    private Path requireExisting(Path candidate, boolean directory) {
        Path absolute = requireAbsolutePath(candidate);
        Path relative = relativeToRoot(requireRealExistingEntry(absolute));
        Path current = approvedRoot;
        for (Path segment : relative) {
            current = current.resolve(segment);
            current = requireRealExistingEntry(current);
        }
        if (directory && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
            throw rejected();
        }
        return current;
    }

    private Path requireAbsolutePath(Path candidate) {
        if (candidate == null || !candidate.isAbsolute() || containsTraversal(candidate)) {
            throw rejected();
        }
        return candidate.toAbsolutePath().normalize();
    }

    private Path relativeToRoot(Path absolute) {
        if (!absolute.startsWith(approvedRoot)) {
            throw rejected();
        }
        return approvedRoot.relativize(absolute);
    }

    private Path requireRealExistingEntry(Path candidate) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(candidate, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther() || Files.isSymbolicLink(candidate)) {
                throw rejected();
            }
            Path noFollow = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path following = candidate.toRealPath();
            if (!noFollow.equals(following) || !noFollow.startsWith(approvedRoot)) {
                throw rejected();
            }
            return noFollow;
        } catch (IOException exception) {
            throw rejected();
        }
    }

    private static Path requireApprovedRoot(Path candidate) {
        if (candidate == null || !candidate.isAbsolute() || containsTraversal(candidate)) {
            throw rejected();
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(candidate, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther() || Files.isSymbolicLink(candidate)
                || !attributes.isDirectory() || !Files.isReadable(candidate) || !Files.isWritable(candidate)) {
                throw rejected();
            }
            Path noFollow = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path following = candidate.toRealPath();
            if (!noFollow.equals(following)) {
                throw rejected();
            }
            return noFollow;
        } catch (IOException exception) {
            throw rejected();
        }
    }

    private static boolean containsTraversal(Path path) {
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private static IllegalArgumentException rejected() {
        return new IllegalArgumentException("timeline path rejected");
    }
}
