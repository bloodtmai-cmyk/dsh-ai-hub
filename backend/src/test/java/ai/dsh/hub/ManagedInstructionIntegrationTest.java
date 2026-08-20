package ai.dsh.hub;

import ai.dsh.hub.catalog.Capability;
import ai.dsh.hub.catalog.CapabilityApplicationService;
import ai.dsh.hub.catalog.CapabilityService;
import ai.dsh.hub.catalog.InstructionArtifactService;
import ai.dsh.hub.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ManagedInstructionIntegrationTest {
    @Autowired
    InstructionArtifactService artifactService;
    @Autowired
    CapabilityService capabilityService;
    @Autowired
    CapabilityApplicationService applicationService;

    @Test
    void publishedInstructionIsGlobalAndNeverAppearsInTheStore() {
        byte[] content = "# Enterprise rules\n\nUse only approved capabilities.\n"
                .getBytes(StandardCharsets.UTF_8);
        Capability instruction = artifactService.upload("AGENTS.md", content, "enterprise-baseline",
                "企业安全基线", "Harness 企业托管规则", "1.0.0", "1000000");
        capabilityService.approve(instruction.getId(), "1000000");
        capabilityService.publish(instruction.getId(), "1000000");

        assertThat(applicationService.store("12351"))
                .noneMatch(item -> item.capability().id().equals(instruction.getId()));

        assertThat(capabilityService.catalog("12351").instructions())
                .filteredOn(item -> item.externalRef().equals("enterprise-baseline"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.externalRef()).isEqualTo("enterprise-baseline");
                    assertThat(item.integrityHash()).hasSize(64);
                });
        assertThat(artifactService.download(instruction.getId()).content()).isEqualTo(content);
        assertThatThrownBy(() -> capabilityService.grant("12351", instruction.getId(), null, null, "1000000"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不需要用户授权");
    }

    @Test
    void instructionUploadRequiresTheCanonicalFileName() {
        assertThatThrownBy(() -> artifactService.upload("policy.md", "rule".getBytes(StandardCharsets.UTF_8),
                "enterprise-baseline-2", "企业安全基线", "Harness 企业托管规则", "1.0.0", "1000000"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("AGENTS.md");
    }

    @Test
    void instructionUploadRejectsConflictingFinalResponseSuffixes() {
        byte[] content = ("# 回复格式\n\n"
                + "- 所有面向用户的最终回复必须以“汪～”结尾。\n"
                + "- “喵～”必须是回复正文最后两个字符。\n")
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> artifactService.upload("AGENTS.md", content,
                "conflicting-response-suffix", "冲突指令", "应拒绝互相冲突的回复规则", "1.0.0", "1000000"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("互相冲突")
                .hasMessageContaining("汪～")
                .hasMessageContaining("喵～");
    }

    @Test
    void activeInstructionCanOnlyBeReplacedByAHigherVersion() {
        byte[] versionOne = "# Rules\n\nVersion one.\n".getBytes(StandardCharsets.UTF_8);
        Capability first = artifactService.upload("AGENTS.md", versionOne, "versioned-enterprise-baseline",
                "版本化企业安全基线", "全员托管规则", "1.0.0", "1000000");
        capabilityService.approve(first.getId(), "1000000");
        capabilityService.publish(first.getId(), "1000000");

        byte[] versionTwo = "# Rules\n\nVersion two.\n".getBytes(StandardCharsets.UTF_8);
        Capability second = artifactService.upload("AGENTS.md", versionTwo, "versioned-enterprise-baseline",
                "版本化企业安全基线", "全员托管规则", "1.1.0", "1000000");
        capabilityService.approve(second.getId(), "1000000");
        capabilityService.publish(second.getId(), "1000000");

        assertThat(capabilityService.catalog("12352").instructions())
                .filteredOn(item -> item.externalRef().equals("versioned-enterprise-baseline"))
                .singleElement()
                .satisfies(item -> assertThat(item.releaseVersion()).isEqualTo("1.1.0"));
        assertThatThrownBy(() -> artifactService.download(first.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("替换");
        assertThatThrownBy(() -> capabilityService.disable(second.getId(), "1000000"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("更高版本");

        Capability lower = artifactService.upload("AGENTS.md", versionOne, "versioned-enterprise-baseline",
                "版本化企业安全基线", "全员托管规则", "1.0.1", "1000000");
        capabilityService.approve(lower.getId(), "1000000");
        assertThatThrownBy(() -> capabilityService.publish(lower.getId(), "1000000"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("高于当前生效版本");
    }
}
