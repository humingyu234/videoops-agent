package org.dromara.aivideo.infra.digitalhuman;

import org.dromara.aivideo.digitalhuman.dto.DigitalHumanMediaContentDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanStoredMediaDTO;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanMediaStorageService;
import org.dromara.common.core.exception.ServiceException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 服务端私有目录媒体存储。
 */
public final class FileSystemDigitalHumanMediaStorageService implements IDigitalHumanMediaStorageService {

    private static final Pattern KEY = Pattern.compile("[1-9][0-9]*/(input|output)/[a-f0-9-]+\\.[a-z0-9]{1,10}");
    private static final int MAX_INPUT_BYTES = 10 * 1024 * 1024;
    private static final int MAX_WAV_BYTES = 32 * 1024 * 1024;
    private static final int MAX_MP4_BYTES = 128 * 1024 * 1024;
    private static final MediaFormat WAV = new MediaFormat("wav", "audio/wav", MAX_WAV_BYTES);
    private static final MediaFormat MP3 = new MediaFormat("mp3", "audio/mpeg", MAX_INPUT_BYTES);
    private static final MediaFormat M4A = new MediaFormat("m4a", "audio/mp4", MAX_INPUT_BYTES);
    private static final MediaFormat FLAC = new MediaFormat("flac", "audio/flac", MAX_INPUT_BYTES);
    private static final MediaFormat JPG = new MediaFormat("jpg", "image/jpeg", MAX_INPUT_BYTES);
    private static final MediaFormat PNG = new MediaFormat("png", "image/png", MAX_INPUT_BYTES);
    private static final MediaFormat WEBP = new MediaFormat("webp", "image/webp", MAX_INPUT_BYTES);
    private static final MediaFormat MP4 = new MediaFormat("mp4", "video/mp4", MAX_MP4_BYTES);
    private static final Map<String, MediaFormat> MEDIA_FORMATS = Map.ofEntries(
        Map.entry("wav", WAV), Map.entry("mp3", MP3), Map.entry("m4a", M4A), Map.entry("flac", FLAC),
        Map.entry("jpg", JPG), Map.entry("jpeg", JPG), Map.entry("png", PNG), Map.entry("webp", WEBP),
        Map.entry("mp4", MP4));

    private final Path root;

    public FileSystemDigitalHumanMediaStorageService(String mediaRoot) {
        try {
            Path configuredRoot = Path.of(mediaRoot).toAbsolutePath().normalize();
            Files.createDirectories(configuredRoot);
            if (Files.isSymbolicLink(configuredRoot)) {
                throw new IOException();
            }
            root = configuredRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException();
            }
        } catch (Exception exception) {
            throw new ServiceException("数字人私有媒体目录不可用");
        }
    }

    @Override
    public DigitalHumanStoredMediaDTO storeInput(Long jobId, String fileName, String mediaType, byte[] content) {
        return store(jobId, "input", fileName, mediaType, content);
    }

    @Override
    public DigitalHumanStoredMediaDTO storeOutput(Long jobId, String fileName, String mediaType, byte[] content) {
        return store(jobId, "output", fileName, mediaType, content);
    }

    @Override
    public DigitalHumanMediaContentDTO read(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new ServiceException("数字人媒体文件不可用");
        }
        try {
            Path path = resolve(key);
            path = requireRegularFile(path);
            String fileName = path.getFileName().toString();
            MediaFormat format = MEDIA_FORMATS.get(extension(fileName));
            if (format == null) {
                throw new ServiceException("数字人媒体文件不可用");
            }
            byte[] content = readLimited(path, format.maxBytes());
            if (!hasMagic(format, content)) {
                throw new ServiceException("数字人媒体文件不可用");
            }
            return new DigitalHumanMediaContentDTO(fileName, format.mediaType(), content);
        } catch (IOException exception) {
            throw new ServiceException("数字人媒体文件不可用");
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new ServiceException("数字人媒体文件不可用");
        }
        try {
            Path path = resolve(key);
            Path parent = requireDirectory(path.getParent(), false);
            if (parent == null) {
                return;
            }
            path = parent.resolve(path.getFileName());
            if (Files.isSymbolicLink(path)) {
                throw new ServiceException("数字人媒体文件不可用");
            }
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new ServiceException("删除数字人媒体文件失败");
        }
    }

    private DigitalHumanStoredMediaDTO store(Long jobId, String scope, String fileName,
                                               String mediaType, byte[] content) {
        if (jobId == null || jobId <= 0 || content == null || content.length == 0) {
            throw new ServiceException("数字人媒体文件无效");
        }
        MediaFormat format = requireMediaFormat(fileName, mediaType, content);
        String key = jobId + "/" + scope + "/" + UUID.randomUUID() + "." + format.extension();
        Path path = null;
        boolean created = false;
        try {
            path = resolve(key);
            Path parent = requireDirectory(path.getParent(), true);
            path = parent.resolve(path.getFileName());
            SeekableByteChannel opened = Files.newByteChannel(path, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            created = true;
            try (SeekableByteChannel channel = opened) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }
            return new DigitalHumanStoredMediaDTO(key, format.mediaType(), content.length, sha256(content));
        } catch (IOException exception) {
            if (created) {
                deletePartialFile(path);
            }
            throw new ServiceException("保存数字人媒体文件失败");
        }
    }

    private static void deletePartialFile(Path path) {
        try {
            if (path != null && !Files.isSymbolicLink(path)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // 保留稳定的存储失败提示；无法删除的残片由后续维护扫描清理。
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new ServiceException("数字人媒体文件不可用");
        }
        return path;
    }

    private Path requireRegularFile(Path path) throws IOException {
        Path parent = requireDirectory(path.getParent(), false);
        if (parent == null) {
            throw new ServiceException("数字人媒体文件不可用");
        }
        Path candidate = parent.resolve(path.getFileName());
        if (Files.isSymbolicLink(candidate) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new ServiceException("数字人媒体文件不可用");
        }
        Path realPath = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realPath.equals(candidate) || !realPath.startsWith(root)) {
            throw new ServiceException("数字人媒体文件不可用");
        }
        return realPath;
    }

    private Path requireDirectory(Path directory, boolean create) throws IOException {
        Path relative = root.relativize(directory);
        Path current = root;
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (!create) {
                    return null;
                }
                Files.createDirectory(current);
            }
            if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new ServiceException("数字人媒体文件不可用");
            }
        }
        Path realDirectory = current.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realDirectory.equals(current) || !realDirectory.startsWith(root)) {
            throw new ServiceException("数字人媒体文件不可用");
        }
        return realDirectory;
    }

    private static byte[] readLimited(Path path, int maxBytes) throws IOException {
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] content = input.readNBytes(maxBytes + 1);
            if (content.length > maxBytes) {
                throw new ServiceException("数字人媒体文件不可用");
            }
            return content;
        }
    }

    private static String extension(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private static MediaFormat requireMediaFormat(String fileName, String mediaType, byte[] content) {
        MediaFormat format = MEDIA_FORMATS.get(extension(fileName));
        String normalizedType = mediaType == null ? "" : mediaType.trim().toLowerCase(Locale.ROOT);
        if (format == null || !format.mediaType().equals(normalizedType) || content.length > format.maxBytes()
            || !hasMagic(format, content)) {
            throw new ServiceException("数字人媒体文件无效");
        }
        return format;
    }

    private static boolean hasMagic(MediaFormat format, byte[] content) {
        return switch (format.extension()) {
            case "wav" -> asciiAt(content, 0, "RIFF") && asciiAt(content, 8, "WAVE");
            case "mp3" -> asciiAt(content, 0, "ID3")
                || content.length >= 2 && unsigned(content[0]) == 0xff && (unsigned(content[1]) & 0xe0) == 0xe0;
            case "m4a", "mp4" -> asciiAt(content, 4, "ftyp");
            case "flac" -> asciiAt(content, 0, "fLaC");
            case "jpg" -> content.length >= 3 && unsigned(content[0]) == 0xff
                && unsigned(content[1]) == 0xd8 && unsigned(content[2]) == 0xff;
            case "png" -> content.length >= 8 && unsigned(content[0]) == 0x89 && asciiAt(content, 1, "PNG")
                && unsigned(content[4]) == 0x0d && unsigned(content[5]) == 0x0a
                && unsigned(content[6]) == 0x1a && unsigned(content[7]) == 0x0a;
            case "webp" -> asciiAt(content, 0, "RIFF") && asciiAt(content, 8, "WEBP");
            default -> false;
        };
    }

    private static boolean asciiAt(byte[] content, int offset, String value) {
        if (content.length < offset + value.length()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (unsigned(content[offset + index]) != value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private record MediaFormat(String extension, String mediaType, int maxBytes) {
    }
}
