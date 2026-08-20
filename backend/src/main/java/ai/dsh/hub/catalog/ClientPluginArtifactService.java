package ai.dsh.hub.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.dsh.hub.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ClientPluginArtifactService {
    private static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 1024;
    private static final int MAX_UNCOMPRESSED_BYTES = 80 * 1024 * 1024;
    private static final int MAX_MANIFEST_BYTES = 128 * 1024;

    private final CapabilityService capabilityService;
    private final ClientPluginArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper;

    public ClientPluginArtifactService(CapabilityService capabilityService,
                                       ClientPluginArtifactRepository artifactRepository,
                                       ObjectMapper objectMapper) {
        this.capabilityService = capabilityService;
        this.artifactRepository = artifactRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Capability upload(String originalFileName, String mediaType, byte[] content, String actor) {
        if (content.length == 0 || content.length > MAX_UPLOAD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLUGIN_SIZE", "插件压缩包必须介于 1 字节和 20 MiB 之间");
        }
        String fileName = safeFileName(originalFileName);
        ValidatedManifest manifest = validateArchive(content);
        String hash = sha256(content);
        Capability capability = capabilityService.create(new CapabilityService.CreateCommand(
                Capability.Type.CLIENT_PLUGIN, null, Capability.SourceKind.UPLOAD, manifest.id(), manifest.name(),
                manifest.description(), manifest.version(), "hub://plugins/" + manifest.id() + "/" + manifest.version(),
                hash), actor);
        String resolvedMediaType = mediaType == null || mediaType.isBlank() || mediaType.length() > 120
                ? "application/zip" : mediaType;
        artifactRepository.save(new ClientPluginArtifact(capability.getId(), fileName, resolvedMediaType, content,
                hash, manifest.json(), actor));
        return capability;
    }

    @Transactional(readOnly = true)
    public Download download(UUID capabilityId, String workcode) {
        Capability capability = capabilityService.requireAuthorized(capabilityId, workcode, Capability.Type.CLIENT_PLUGIN);
        ClientPluginArtifact artifact = get(capabilityId);
        return toDownload(capability, artifact);
    }

    @Transactional(readOnly = true)
    public Download adminDownload(UUID capabilityId) {
        Capability capability = capabilityService.getCapability(capabilityId);
        if (capability.getType() != Capability.Type.CLIENT_PLUGIN) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PLUGIN_ARTIFACT_NOT_FOUND", "插件制品不存在");
        }
        return toDownload(capability, get(capabilityId));
    }

    private ClientPluginArtifact get(UUID capabilityId) {
        return artifactRepository.findByCapabilityId(capabilityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLUGIN_ARTIFACT_NOT_FOUND", "插件制品不存在"));
    }

    private static Download toDownload(Capability capability, ClientPluginArtifact artifact) {
        return new Download(capability.getId(), artifact.getFileName(), artifact.getMediaType(), artifact.getSha256(),
                artifact.getManifestJson(), artifact.getContent());
    }

    private ValidatedManifest validateArchive(byte[] content) {
        int entries = 0;
        int totalBytes = 0;
        byte[] manifestBytes = null;
        Set<String> paths = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ARCHIVE_ENTRIES) throw invalidArchive("插件压缩包文件数量超过限制");
                String path = normalizePath(entry.getName());
                if (!paths.add(path)) throw invalidArchive("插件压缩包包含重复路径");
                if (entry.isDirectory()) continue;
                ByteArrayOutputStream current = path.equals("plugin.json") ? new ByteArrayOutputStream() : null;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    totalBytes += read;
                    if (totalBytes > MAX_UNCOMPRESSED_BYTES) throw invalidArchive("插件压缩包解压后超过 80 MiB");
                    if (current != null) {
                        if (current.size() + read > MAX_MANIFEST_BYTES) throw invalidArchive("plugin.json 超过 128 KiB");
                        current.write(buffer, 0, read);
                    }
                }
                if (current != null) manifestBytes = current.toByteArray();
            }
        } catch (IOException error) {
            throw invalidArchive("无法读取插件压缩包");
        }
        if (manifestBytes == null || manifestBytes.length == 0) throw invalidArchive("插件压缩包缺少顶层 plugin.json");

        JsonNode root;
        try {
            root = objectMapper.readTree(manifestBytes);
        } catch (IOException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLUGIN_MANIFEST", "plugin.json 不是有效 JSON");
        }
        if (!root.isObject() || root.path("schemaVersion").asInt(-1) != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLUGIN_MANIFEST", "plugin.json schemaVersion 必须为 1");
        }
        String id = text(root, "id", 120);
        String name = text(root, "name", 120);
        String description = text(root, "description", 1000);
        String version = text(root, "version", 80);
        String entry = normalizePath(text(root, "entry", 240));
        String activation = text(root, "activation", 20);
        if (!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLUGIN_ID", "插件 id 必须为 kebab-case");
        }
        if (!entry.endsWith(".mjs") || !paths.contains(entry)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLUGIN_ENTRY", "插件入口必须是压缩包内存在的 .mjs 文件");
        }
        if (!activation.equals("hot")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PLUGIN_ACTIVATION",
                    "企业客户端插件必须声明 activation=hot，并通过 Cordis 生命周期释放全部资源");
        }
        try {
            return new ValidatedManifest(id, name, description, version, objectMapper.writeValueAsString(root));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unable to normalize plugin manifest", error);
        }
    }

    private static String text(JsonNode root, String field, int max) {
        JsonNode value = root.get(field);
        String normalized = value == null || !value.isTextual() ? "" : value.asText().trim();
        if (normalized.isBlank() || normalized.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLUGIN_MANIFEST", "plugin.json 字段无效: " + field);
        }
        return normalized;
    }

    private static String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLUGIN_FILENAME_REQUIRED", "插件文件名不能为空");
        }
        String fileName = original.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.length() > 180 || !fileName.toLowerCase().endsWith(".zip")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLUGIN_FILE", "只允许上传 .zip 插件制品");
        }
        return fileName;
    }

    private static String normalizePath(String raw) {
        String path = raw == null ? "" : raw.replace('\\', '/');
        if (path.isBlank() || path.startsWith("/") || path.contains("../") || path.equals("..")
                || path.indexOf('\0') >= 0 || path.startsWith("./")) {
            throw invalidArchive("插件压缩包包含不安全路径");
        }
        return path;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static ApiException invalidArchive(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLUGIN_ARCHIVE", message);
    }

    private record ValidatedManifest(String id, String name, String description, String version, String json) {
    }

    public record Download(UUID capabilityId, String fileName, String mediaType, String sha256, String manifestJson,
                           byte[] content) {
        public Download { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
}
