package ai.dsh.hub;

import ai.dsh.hub.catalog.Capability;
import ai.dsh.hub.catalog.CapabilityRepository;
import ai.dsh.hub.catalog.CapabilityService;
import ai.dsh.hub.catalog.SkillArtifactService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SkillBundleIntegrationTest {
    @Autowired
    CapabilityService capabilityService;
    @Autowired
    SkillArtifactService skillArtifactService;
    @Autowired
    CapabilityRepository capabilityRepository;

    @Test
    void uploadedSkillAndMcpToolsCanBeGrantedAsOneBundle() {
        byte[] skillContent = """
                ---
                name: inventory-assistant
                description: Query authorized inventory through WorkBuddy.
                ---

                Use the authorized inventory tool.
                """.getBytes(StandardCharsets.UTF_8);
        Capability skill = skillArtifactService.upload("SKILL.md", "text/markdown", skillContent,
                "库存查询 Skill", "1.0.0", "1000000");
        capabilityService.approve(skill.getId(), "1000000");
        capabilityService.publish(skill.getId(), "1000000");

        capabilityService.syncGateway(List.of(
                new CapabilityService.GatewayCapability(Capability.Type.MCP, null, "bundle-workbuddy",
                        "WorkBuddy Bundle", "Bundle test MCP", "1", "gateway:mcp:bundle-workbuddy"),
                new CapabilityService.GatewayCapability(Capability.Type.TOOL, "bundle-workbuddy",
                        "bundle-workbuddy/inventory", "Inventory", "Query inventory", "1",
                        "gateway:tool:bundle-inventory")
        ));
        Capability mcp = capabilityRepository.findByTypeAndExternalRefAndReleaseVersion(
                Capability.Type.MCP, "bundle-workbuddy", "1").orElseThrow();
        capabilityService.approve(mcp.getId(), "1000000");
        capabilityService.publish(mcp.getId(), "1000000");

        var bundle = capabilityService.createBundle(new CapabilityService.CreateBundleCommand(
                "inventory-bundle", "库存助手", "Skill 与 MCP Tool 的受控组合", "1.0.0",
                List.of(mcp.getId(), skill.getId())), "1000000");
        capabilityService.approve(bundle.bundle().id(), "1000000");
        capabilityService.publish(bundle.bundle().id(), "1000000");
        capabilityService.grant("12349", bundle.bundle().id(), null, null, "1000000");

        assertThat(capabilityService.check("12349", Capability.Type.TOOL, "bundle-workbuddy/inventory").allowed())
                .isTrue();
        CapabilityService.Catalog catalog = capabilityService.catalog("12349");
        assertThat(catalog.bundles()).singleElement()
                .satisfies(item -> assertThat(item.members()).hasSize(2));
        assertThat(catalog.skills()).singleElement()
                .satisfies(item -> assertThat(item.externalRef()).isEqualTo("inventory-assistant"));
        assertThat(skillArtifactService.download(skill.getId(), "12349").content()).isEqualTo(skillContent);
    }
}
