package org.dromara.aivideo.asset.service;

import java.util.regex.Pattern;

/** Object-key namespace owned by the VideoOps Agent golden path. */
public final class VideoOpsObjectKey {

    public static final String PREFIX = "videoops-agent/dev";

    private static final String LEGACY_PREFIX = "ai-video";
    private static final int MAX_KEY_LENGTH = 1024;
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9._/-]+");

    private VideoOpsObjectKey() {
    }

    /** Qualifies one canonical logical key into the project namespace. */
    public static String qualify(String logicalKey) {
        String key = requireCanonical(logicalKey, "logical object key");
        if (key.equals(PREFIX) || key.startsWith(PREFIX + "/")) {
            throw new IllegalArgumentException("logical object key must not contain the project namespace");
        }
        if (key.equals(LEGACY_PREFIX) || key.startsWith(LEGACY_PREFIX + "/")) {
            throw new IllegalArgumentException("legacy OSS namespace is not allowed");
        }
        return requireQualified(PREFIX + "/" + key);
    }

    /** Requires a key already owned by the project namespace. */
    public static String requireQualified(String objectKey) {
        String key = requireCanonical(objectKey, "object key");
        if (!key.startsWith(PREFIX + "/") || key.length() == PREFIX.length() + 1) {
            throw new IllegalArgumentException("object key is outside the VideoOps Agent namespace");
        }
        return key;
    }

    /** Requires the active OSS configuration to use the project prefix exactly. */
    public static String requireProjectPrefix(String prefix) {
        if (!PREFIX.equals(prefix)) {
            throw new IllegalArgumentException("OSS prefix must be exactly " + PREFIX);
        }
        return prefix;
    }

    private static String requireCanonical(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > MAX_KEY_LENGTH
            || !SAFE_KEY.matcher(value).matches() || value.startsWith("/") || value.endsWith("/")) {
            throw new IllegalArgumentException(name + " is not a canonical relative key");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(name + " contains an unsafe path segment");
            }
        }
        return value;
    }
}
