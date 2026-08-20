package ai.dsh.hub.release;

import ai.dsh.hub.admin.AdminAuditService;
import ai.dsh.hub.common.ApiException;
import ai.dsh.hub.config.AiHubProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DesktopReleaseService {
    private static final int BUFFER_BYTES = 64 * 1024;
    private static final int MAX_NOTES_LENGTH = 4_000;
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$"
    );

    private final DesktopReleaseRepository repository;
    private final AdminAuditService auditService;
    private final Path artifactRoot;
    private final long maxArtifactBytes;

    public DesktopReleaseService(DesktopReleaseRepository repository, AdminAuditService auditService,
                                 AiHubProperties properties) {
        this.repository = repository;
        this.auditService = auditService;
        this.artifactRoot = Path.of(properties.release().artifactRoot()).toAbsolutePath().normalize();
        this.maxArtifactBytes = properties.release().maxArtifactBytes();
    }

    @Transactional(readOnly = true)
    public Page<ReleaseView> list(Pageable pageable) {
        return repository.findAll(pageable).map(ReleaseView::from);
    }

    @Transactional
    public ReleaseView upload(DesktopRelease.Platform platform, String version, String releaseNotes,
                              MultipartFile file, String actor) {
        String normalizedVersion = normalizeVersion(version);
        String normalizedNotes = normalizeNotes(releaseNotes);
        String fileName = safeFileName(file.getOriginalFilename(), platform);
        if (file.isEmpty() || file.getSize() <= 0 || file.getSize() > maxArtifactBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DESKTOP_RELEASE_SIZE",
                    "客户端安装制品必须介于 1 字节和 " + maxArtifactBytes + " 字节之间");
        }
        if (repository.existsByPlatformAndVersion(platform, normalizedVersion)) {
            throw new ApiException(HttpStatus.CONFLICT, "DESKTOP_RELEASE_EXISTS", "该平台版本已经存在");
        }

        String extension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
        String artifactKey = UUID.randomUUID() + extension;
        StoredArtifact artifact = store(file, artifactKey);
        String mediaType = file.getContentType() == null || file.getContentType().isBlank()
                || file.getContentType().length() > 120 ? "application/octet-stream" : file.getContentType();
        DesktopRelease release = new DesktopRelease(normalizedVersion, platform, fileName, mediaType,
                artifact.sizeBytes(), artifact.sha256(), artifact.sha512(), artifactKey, normalizedNotes, actor);
        try {
            repository.saveAndFlush(release);
        } catch (RuntimeException error) {
            deleteQuietly(artifact.path());
            throw error;
        }
        auditService.record(actor, "UPLOAD_DESKTOP_RELEASE", "DESKTOP_RELEASE", release.getId().toString(),
                "SUCCESS", "platform=" + platform + ",version=" + normalizedVersion + ",sha256=" + artifact.sha256());
        return ReleaseView.from(release);
    }

    @Transactional
    public ReleaseView publish(UUID id, String actor) {
        DesktopRelease release = repository.findLockedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DESKTOP_RELEASE_NOT_FOUND",
                        "客户端版本不存在"));
        ReleaseVersion candidate = ReleaseVersion.parse(release.getVersion());
        List<DesktopRelease> current = repository.findByPlatformAndStatus(
                release.getPlatform(), DesktopRelease.Status.PUBLISHED);
        Optional<DesktopRelease> notOlder = current.stream()
                .filter(item -> candidate.compareTo(ReleaseVersion.parse(item.getVersion())) <= 0)
                .findFirst();
        if (notOlder.isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "DESKTOP_RELEASE_VERSION_NOT_HIGHER",
                    "发布版本必须高于当前生效版本 " + notOlder.get().getVersion());
        }
        requireArtifact(release);
        current.forEach(DesktopRelease::supersede);
        repository.flush();
        release.publish(actor);
        auditService.record(actor, "PUBLISH_DESKTOP_RELEASE", "DESKTOP_RELEASE", release.getId().toString(),
                "SUCCESS", "platform=" + release.getPlatform() + ",version=" + release.getVersion());
        return ReleaseView.from(release);
    }

    @Transactional(readOnly = true)
    public Optional<ReleaseView> latest(DesktopRelease.Platform platform, String currentVersion) {
        ReleaseVersion installed = ReleaseVersion.parse(normalizeVersion(currentVersion));
        return repository.findFirstByPlatformAndStatusOrderByPublishedAtDesc(platform, DesktopRelease.Status.PUBLISHED)
                .filter(release -> ReleaseVersion.parse(release.getVersion()).compareTo(installed) > 0)
                .map(ReleaseView::from);
    }

    @Transactional(readOnly = true)
    public ReleaseDownload clientDownload(UUID id) {
        DesktopRelease release = get(id);
        if (release.getStatus() == DesktopRelease.Status.DRAFT) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DESKTOP_RELEASE_NOT_FOUND", "客户端版本不存在");
        }
        return toDownload(release);
    }

    @Transactional(readOnly = true)
    public ReleaseDownload adminDownload(UUID id) {
        return toDownload(get(id));
    }

    @Transactional(readOnly = true)
    public UpdateManifest windowsUpdateManifest() {
        DesktopRelease release = repository.findFirstByPlatformAndStatusOrderByPublishedAtDesc(
                        DesktopRelease.Platform.WINDOWS_X64, DesktopRelease.Status.PUBLISHED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DESKTOP_RELEASE_NOT_FOUND",
                        "尚未发布 Windows 客户端版本"));
        Path artifact = requireArtifact(release);
        String sha512 = release.getSha512() == null || release.getSha512().isBlank()
                ? digestBase64(artifact, "SHA-512") : release.getSha512();
        String relativeUrl = "artifacts/" + release.getId() + ".exe";
        LinkedHashMap<String, Object> file = new LinkedHashMap<>();
        file.put("url", relativeUrl);
        file.put("sha512", sha512);
        file.put("size", release.getSizeBytes());
        LinkedHashMap<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", release.getVersion());
        manifest.put("files", List.of(file));
        manifest.put("path", relativeUrl);
        manifest.put("sha512", sha512);
        manifest.put("releaseDate", release.getPublishedAt().toString());
        manifest.put("releaseNotes", release.getReleaseNotes());
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setSplitLines(false);
        return new UpdateManifest(new Yaml(options).dump(manifest));
    }

    @Transactional(readOnly = true)
    public ReleaseDownload windowsUpdateDownload(UUID id) {
        DesktopRelease release = get(id);
        if (release.getPlatform() != DesktopRelease.Platform.WINDOWS_X64
                || release.getStatus() == DesktopRelease.Status.DRAFT) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DESKTOP_RELEASE_NOT_FOUND", "客户端版本不存在");
        }
        return toDownload(release);
    }

    private DesktopRelease get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DESKTOP_RELEASE_NOT_FOUND",
                        "客户端版本不存在"));
    }

    private ReleaseDownload toDownload(DesktopRelease release) {
        Path artifact = requireArtifact(release);
        return new ReleaseDownload(release.getFileName(), release.getMediaType(), release.getSizeBytes(),
                release.getSha256(), new FileSystemResource(artifact));
    }

    private StoredArtifact store(MultipartFile file, String artifactKey) {
        Path target = artifactPath(artifactKey);
        Path temporary = artifactPath('.' + artifactKey + ".part");
        try {
            Files.createDirectories(artifactRoot);
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
            long size = 0;
            try (InputStream input = file.getInputStream();
                 OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW,
                         StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[BUFFER_BYTES];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    size += read;
                    if (size > maxArtifactBytes) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DESKTOP_RELEASE_SIZE",
                                "客户端安装制品超过大小限制");
                    }
                    sha256.update(buffer, 0, read);
                    sha512.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            if (size == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DESKTOP_RELEASE_SIZE",
                        "客户端安装制品不能为空");
            }
            setReadOnly(temporary);
            moveAtomically(temporary, target);
            return new StoredArtifact(target, size, HexFormat.of().formatHex(sha256.digest()),
                    Base64.getEncoder().encodeToString(sha512.digest()));
        } catch (ApiException error) {
            deleteQuietly(temporary);
            throw error;
        } catch (IOException | NoSuchAlgorithmException error) {
            deleteQuietly(temporary);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DESKTOP_RELEASE_STORAGE_FAILED",
                    "客户端安装制品存储失败");
        }
    }

    private Path requireArtifact(DesktopRelease release) {
        Path artifact = artifactPath(release.getArtifactKey());
        try {
            if (!Files.isRegularFile(artifact) || Files.size(artifact) != release.getSizeBytes()) {
                throw new ApiException(HttpStatus.CONFLICT, "DESKTOP_RELEASE_ARTIFACT_MISSING",
                        "客户端安装制品缺失或大小不一致");
            }
            return artifact;
        } catch (IOException error) {
            throw new ApiException(HttpStatus.CONFLICT, "DESKTOP_RELEASE_ARTIFACT_MISSING",
                    "客户端安装制品不可用");
        }
    }

    private Path artifactPath(String key) {
        Path path = artifactRoot.resolve(key).normalize();
        if (!path.startsWith(artifactRoot)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_DESKTOP_RELEASE_PATH",
                    "客户端安装制品路径无效");
        }
        return path;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    private static void setReadOnly(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ));
        } catch (UnsupportedOperationException ignored) {
            path.toFile().setReadOnly();
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A failed cleanup is intentionally not allowed to hide the original upload error.
        }
    }

    private static String digestBase64(Path path, String algorithm) {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new ApiException(HttpStatus.CONFLICT, "DESKTOP_RELEASE_ARTIFACT_MISSING",
                    "客户端安装制品不可用");
        }
    }

    private static String safeFileName(String original, DesktopRelease.Platform platform) {
        String normalized = original == null ? "" : original.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.length() > 180 || fileName.indexOf('\0') >= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DESKTOP_RELEASE_FILE", "客户端制品文件名无效");
        }
        String lower = fileName.toLowerCase();
        boolean accepted = platform == DesktopRelease.Platform.WINDOWS_X64
                ? lower.endsWith(".exe") : lower.endsWith(".dmg") || lower.endsWith(".pkg");
        if (!accepted) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DESKTOP_RELEASE_FILE",
                    platform == DesktopRelease.Platform.WINDOWS_X64
                            ? "Windows 客户端制品必须为 .exe" : "macOS 客户端制品必须为 .dmg 或 .pkg");
        }
        return fileName;
    }

    private static String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        ReleaseVersion.parse(normalized);
        return normalized;
    }

    private static String normalizeNotes(String notes) {
        String normalized = notes == null ? "" : notes.trim();
        if (normalized.isBlank() || normalized.length() > MAX_NOTES_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DESKTOP_RELEASE_NOTES",
                    "升级说明不能为空且不超过 4000 字符");
        }
        return normalized;
    }

    public record ReleaseView(UUID id, String version, DesktopRelease.Platform platform,
                              DesktopRelease.Status status, String fileName, String mediaType, long sizeBytes,
                              String sha256, String releaseNotes, String createdBy, java.time.Instant createdAt,
                              String publishedBy, java.time.Instant publishedAt, java.time.Instant supersededAt) {
        static ReleaseView from(DesktopRelease release) {
            return new ReleaseView(release.getId(), release.getVersion(), release.getPlatform(), release.getStatus(),
                    release.getFileName(), release.getMediaType(), release.getSizeBytes(), release.getSha256(),
                    release.getReleaseNotes(), release.getCreatedBy(), release.getCreatedAt(), release.getPublishedBy(),
                    release.getPublishedAt(), release.getSupersededAt());
        }
    }

    public record ReleaseDownload(String fileName, String mediaType, long sizeBytes, String sha256,
                                  FileSystemResource resource) {
    }

    public record UpdateManifest(String yaml) {
    }

    private record StoredArtifact(Path path, long sizeBytes, String sha256, String sha512) {
    }

    private record ReleaseVersion(BigInteger major, BigInteger minor, BigInteger patch,
                                  List<String> prerelease) implements Comparable<ReleaseVersion> {
        static ReleaseVersion parse(String value) {
            Matcher matcher = VERSION_PATTERN.matcher(value == null ? "" : value.trim());
            if (!matcher.matches()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DESKTOP_RELEASE_VERSION",
                        "客户端版本必须使用 SemVer，例如 1.2.0");
            }
            List<String> prerelease = matcher.group(4) == null
                    ? List.of() : List.of(matcher.group(4).split("\\."));
            return new ReleaseVersion(new BigInteger(matcher.group(1)), new BigInteger(matcher.group(2)),
                    new BigInteger(matcher.group(3)), prerelease);
        }

        @Override
        public int compareTo(ReleaseVersion other) {
            int core = major.compareTo(other.major);
            if (core == 0) core = minor.compareTo(other.minor);
            if (core == 0) core = patch.compareTo(other.patch);
            if (core != 0) return core;
            if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0;
            if (prerelease.isEmpty()) return 1;
            if (other.prerelease.isEmpty()) return -1;
            int count = Math.max(prerelease.size(), other.prerelease.size());
            for (int index = 0; index < count; index++) {
                if (index >= prerelease.size()) return -1;
                if (index >= other.prerelease.size()) return 1;
                int compared = compareIdentifier(prerelease.get(index), other.prerelease.get(index));
                if (compared != 0) return compared;
            }
            return 0;
        }

        private static int compareIdentifier(String left, String right) {
            boolean leftNumeric = left.matches("[0-9]+");
            boolean rightNumeric = right.matches("[0-9]+");
            if (leftNumeric && rightNumeric) return new BigInteger(left).compareTo(new BigInteger(right));
            if (leftNumeric != rightNumeric) return leftNumeric ? -1 : 1;
            return left.compareTo(right);
        }
    }
}
