package ai.dsh.hub.catalog;

import ai.dsh.hub.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InstructionArtifactService {
    private static final int MAX_UPLOAD_BYTES = 512 * 1024;
    private static final Pattern RESPONSE_SUFFIX_RULE = Pattern.compile(
            "(?:最终回复|回复正文|面向用户[^\\r\\n]{0,24}回复)[^\\r\\n]{0,80}?以[“\"]([^”\"\\r\\n]{1,32})[”\"][^\\r\\n]{0,24}?结尾");
    private static final Pattern RESPONSE_LAST_CHARS_RULE = Pattern.compile(
            "[“\"]([^”\"\\r\\n]{1,32})[”\"][^\\r\\n]{0,40}?必须是回复正文最后");

    private final CapabilityService capabilityService;
    private final InstructionArtifactRepository artifactRepository;

    public InstructionArtifactService(CapabilityService capabilityService,
                                      InstructionArtifactRepository artifactRepository) {
        this.capabilityService = capabilityService;
        this.artifactRepository = artifactRepository;
    }

    @Transactional
    public Capability upload(String originalFileName, byte[] content, String instructionId, String displayName,
                             String description, String releaseVersion, String actor) {
        requireAgentsFile(originalFileName);
        if (content.length == 0 || content.length > MAX_UPLOAD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INSTRUCTION_SIZE",
                    "AGENTS.md 必须介于 1 字节和 512 KiB 之间");
        }
        String markdown = decodeUtf8(content);
        if (markdown.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_INSTRUCTION", "AGENTS.md 不能为空");
        }
        validateInstructionConsistency(markdown);
        String normalizedId = requireText(instructionId, 120, "企业指令标识不能为空");
        if (!normalizedId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INSTRUCTION_ID", "企业指令标识必须为 kebab-case");
        }
        String normalizedName = requireText(displayName, 120, "企业指令显示名称不能为空");
        String normalizedDescription = requireText(description, 1000, "企业指令说明不能为空");
        String normalizedVersion = requireText(releaseVersion, 80, "企业指令版本不能为空");
        SemanticVersion.parse(normalizedVersion);
        String hash = sha256(content);
        Capability capability = capabilityService.create(new CapabilityService.CreateCommand(
                Capability.Type.INSTRUCTION, null, Capability.SourceKind.UPLOAD, normalizedId, normalizedName,
                normalizedDescription, normalizedVersion,
                "hub://instructions/" + normalizedId + "/" + normalizedVersion, hash), actor);
        artifactRepository.save(new InstructionArtifact(capability.getId(), content, hash, actor));
        return capability;
    }

    @Transactional(readOnly = true)
    public Download download(UUID capabilityId) {
        Capability capability = capabilityService.requirePublishedInstruction(capabilityId);
        return toDownload(capability.getId(), artifact(capabilityId));
    }

    @Transactional(readOnly = true)
    public Download adminDownload(UUID capabilityId) {
        Capability capability = capabilityService.getCapability(capabilityId);
        if (capability.getType() != Capability.Type.INSTRUCTION) {
            throw new ApiException(HttpStatus.NOT_FOUND, "INSTRUCTION_ARTIFACT_NOT_FOUND", "企业指令文件不存在");
        }
        return toDownload(capabilityId, artifact(capabilityId));
    }

    private InstructionArtifact artifact(UUID capabilityId) {
        return artifactRepository.findByCapabilityId(capabilityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INSTRUCTION_ARTIFACT_NOT_FOUND",
                        "企业指令文件不存在"));
    }

    private static Download toDownload(UUID capabilityId, InstructionArtifact artifact) {
        return new Download(capabilityId, artifact.getFileName(), artifact.getMediaType(), artifact.getSha256(),
                artifact.getContent());
    }

    private static void requireAgentsFile(String originalFileName) {
        if (originalFileName == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSTRUCTION_FILENAME_REQUIRED", "文件名必须为 AGENTS.md");
        }
        String fileName = originalFileName.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1).trim();
        if (!fileName.equalsIgnoreCase("AGENTS.md")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INSTRUCTION_FILE", "企业指令只允许上传 AGENTS.md");
        }
    }

    private static String requireText(String value, int maxLength, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INSTRUCTION_METADATA", message);
        }
        return normalized;
    }

    private static void validateInstructionConsistency(String markdown) {
        Set<String> suffixes = new LinkedHashSet<>();
        collectSuffixes(RESPONSE_SUFFIX_RULE.matcher(markdown), suffixes);
        collectSuffixes(RESPONSE_LAST_CHARS_RULE.matcher(markdown), suffixes);
        if (suffixes.size() > 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONFLICTING_INSTRUCTION_RULES",
                    "AGENTS.md 包含互相冲突的最终回复后缀规则：" + String.join("、", suffixes));
        }
    }

    private static void collectSuffixes(Matcher matcher, Set<String> suffixes) {
        while (matcher.find()) {
            String suffix = matcher.group(1).trim();
            if (!suffix.isEmpty()) {
                suffixes.add(suffix);
            }
        }
    }

    private static String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INSTRUCTION_ENCODING", "AGENTS.md 必须使用 UTF-8 编码");
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public record Download(UUID capabilityId, String fileName, String mediaType, String sha256, byte[] content) {
        public Download { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
}
