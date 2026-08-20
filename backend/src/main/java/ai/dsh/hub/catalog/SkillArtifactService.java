package ai.dsh.hub.catalog;

import ai.dsh.hub.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class SkillArtifactService {
    private static final int MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 256;
    private static final int MAX_UNCOMPRESSED_BYTES = 8 * 1024 * 1024;
    private static final int MAX_SKILL_DEFINITION_BYTES = 1024 * 1024;

    private final CapabilityService capabilityService;
    private final SkillArtifactRepository artifactRepository;

    public SkillArtifactService(CapabilityService capabilityService, SkillArtifactRepository artifactRepository) {
        this.capabilityService = capabilityService;
        this.artifactRepository = artifactRepository;
    }

    @Transactional
    public Capability upload(String originalFileName, String mediaType, byte[] content, String displayName,
                             String releaseVersion, String actor) {
        if (content.length == 0 || content.length > MAX_UPLOAD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKILL_SIZE", "Skill 文件必须介于 1 字节和 2 MiB 之间");
        }
        String fileName = safeFileName(originalFileName);
        SkillManifest manifest = fileName.toLowerCase().endsWith(".zip")
                ? validateZip(content)
                : validateMarkdown(fileName, content);
        String normalizedDisplayName = requireText(displayName, 120, "Skill 显示名称不能为空");
        String normalizedVersion = requireText(releaseVersion, 80, "Skill 版本不能为空");
        String hash = sha256(content);
        Capability capability = capabilityService.create(new CapabilityService.CreateCommand(
                Capability.Type.SKILL, null, Capability.SourceKind.UPLOAD, manifest.name(), normalizedDisplayName,
                manifest.description(), normalizedVersion, "hub://skills/" + manifest.name() + "/" + normalizedVersion,
                hash), actor);
        String resolvedMediaType = fileName.toLowerCase().endsWith(".zip")
                ? "application/zip" : "text/markdown; charset=utf-8";
        if (mediaType != null && !mediaType.isBlank() && mediaType.length() <= 120) {
            resolvedMediaType = mediaType;
        }
        artifactRepository.save(new SkillArtifact(capability.getId(), fileName, resolvedMediaType, content, hash, actor));
        return capability;
    }

    @Transactional(readOnly = true)
    public Download download(UUID capabilityId, String workcode) {
        Capability capability = capabilityService.requireAuthorized(capabilityId, workcode, Capability.Type.SKILL);
        SkillArtifact artifact = artifactRepository.findByCapabilityId(capabilityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SKILL_ARTIFACT_NOT_FOUND", "Skill 文件不存在"));
        return new Download(capability.getId(), artifact.getFileName(), artifact.getMediaType(), artifact.getSha256(),
                artifact.getContent());
    }

    @Transactional(readOnly = true)
    public Download adminDownload(UUID capabilityId) {
        SkillArtifact artifact = artifactRepository.findByCapabilityId(capabilityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SKILL_ARTIFACT_NOT_FOUND", "Skill 文件不存在"));
        return new Download(capabilityId, artifact.getFileName(), artifact.getMediaType(), artifact.getSha256(),
                artifact.getContent());
    }

    private static String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SKILL_FILENAME_REQUIRED", "Skill 文件名不能为空");
        }
        String fileName = original.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1).trim();
        String lower = fileName.toLowerCase();
        if (fileName.isBlank() || fileName.length() > 180 || (!lower.endsWith(".zip") && !lower.endsWith(".md"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKILL_FILE", "只允许上传 .zip 或 .md Skill 文件");
        }
        return fileName;
    }

    private static SkillManifest validateMarkdown(String fileName, byte[] content) {
        if (!fileName.toLowerCase().endsWith(".md")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKILL_FILE", "Skill 文件格式不受支持");
        }
        return parseManifest(decodeUtf8(content));
    }

    private static SkillManifest validateZip(byte[] content) {
        int entries = 0;
        int totalBytes = 0;
        byte[] definition = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ARCHIVE_ENTRIES) {
                    throw invalidArchive("Skill 压缩包文件数量超过限制");
                }
                String path = entry.getName().replace('\\', '/');
                if (path.startsWith("/") || path.contains("../") || path.equals("..") || path.indexOf('\0') >= 0) {
                    throw invalidArchive("Skill 压缩包包含不安全路径");
                }
                if (entry.isDirectory()) continue;
                ByteArrayOutputStream current = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    totalBytes += read;
                    if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                        throw invalidArchive("Skill 压缩包解压后超过 8 MiB");
                    }
                    if (isSkillDefinition(path) && current.size() + read <= MAX_SKILL_DEFINITION_BYTES) {
                        current.write(buffer, 0, read);
                    }
                }
                if (isSkillDefinition(path)) {
                    if (definition != null) throw invalidArchive("Skill 压缩包只能包含一个顶层 SKILL.md");
                    if (current.size() == 0 || current.size() > MAX_SKILL_DEFINITION_BYTES) {
                        throw invalidArchive("SKILL.md 为空或超过 1 MiB");
                    }
                    definition = current.toByteArray();
                }
            }
        } catch (IOException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKILL_ARCHIVE", "无法读取 Skill 压缩包");
        }
        if (definition == null) throw invalidArchive("Skill 压缩包缺少顶层 SKILL.md");
        return parseManifest(decodeUtf8(definition));
    }

    private static boolean isSkillDefinition(String path) {
        if (path.equals("SKILL.md")) return true;
        int slash = path.indexOf('/');
        return slash > 0 && slash == path.lastIndexOf('/') && path.endsWith("/SKILL.md");
    }

    private static SkillManifest parseManifest(String markdown) {
        String normalized = markdown.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKILL_MANIFEST", "SKILL.md 必须以 YAML frontmatter 开头");
        }
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKILL_MANIFEST", "SKILL.md 的 YAML frontmatter 未闭合");
        }
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object parsed;
        try {
            parsed = new Yaml(new SafeConstructor(options)).load(normalized.substring(4, end));
        } catch (RuntimeException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKILL_MANIFEST", "SKILL.md 的 YAML frontmatter 无效");
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKILL_MANIFEST", "SKILL.md 缺少有效元数据");
        }
        String name = map.get("name") instanceof String value ? value.trim() : "";
        String description = map.get("description") instanceof String value ? value.trim() : "";
        if (!name.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || name.length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKILL_NAME", "Skill name 必须为 kebab-case，且不超过 120 字符");
        }
        if (description.isBlank() || description.length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKILL_DESCRIPTION", "Skill description 不能为空且不超过 1000 字符");
        }
        return new SkillManifest(name, description);
    }

    private static String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKILL_ENCODING", "SKILL.md 必须使用 UTF-8 编码");
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String requireText(String value, int max, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKILL_METADATA", message);
        }
        return normalized;
    }

    private static ApiException invalidArchive(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKILL_ARCHIVE", message);
    }

    private record SkillManifest(String name, String description) {
    }

    public record Download(UUID capabilityId, String fileName, String mediaType, String sha256, byte[] content) {
        public Download {
            content = content.clone();
        }
        @Override public byte[] content() { return content.clone(); }
    }
}
