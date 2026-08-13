package org.dromara.aivideo.creation.service.impl;

import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.exception.S3StorageException;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.oss.model.Options;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stores deterministic render keys without ever overwriting an existing object. */
final class ImmutableRenderObjectStore {

    private static final String CREATE_ONLY = "*";

    private ImmutableRenderObjectStore() {
    }

    static String uploadOrReuse(String storageKey, InputStream input, long expectedSize,
                                String expectedSha256) throws IOException {
        OssClient client = OssFactory.instance();
        try (DigestInputStream digestInput = new DigestInputStream(input, sha256())) {
            try {
                client.upload(storageKey, digestInput, expectedSize,
                    Options.builder().setIfNoneMatch(CREATE_ONLY));
                return HexFormat.of().formatHex(digestInput.getMessageDigest().digest());
            } catch (RuntimeException exception) {
                if (!isExistingObjectConflict(exception)) {
                    throw exception;
                }
            }
        }
        if (!matchesExisting(client, storageKey, expectedSize, expectedSha256)) {
            throw S3StorageException.form("immutable render object conflicts with retry output");
        }
        return expectedSha256;
    }

    private static boolean matchesExisting(OssClient client, String storageKey, long expectedSize,
                                           String expectedSha256) throws IOException {
        try {
            return client.download(storageKey, (object, existing) -> {
                if (object.size() != expectedSize) {
                    return false;
                }
                try (DigestInputStream digestInput = new DigestInputStream(existing, sha256())) {
                    digestInput.transferTo(OutputStream.nullOutputStream());
                    String actualSha256 = HexFormat.of().formatHex(digestInput.getMessageDigest().digest());
                    return expectedSha256.equals(actualSha256);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private static boolean isExistingObjectConflict(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof S3Exception s3Exception
                && (s3Exception.statusCode() == 409 || s3Exception.statusCode() == 412)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
