package ai.dsh.hub;

import ai.dsh.hub.catalog.Capability;
import ai.dsh.hub.catalog.CapabilityRepository;
import ai.dsh.hub.catalog.CapabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CapabilityAuthorizationIntegrationTest {
    @Autowired
    CapabilityService service;
    @Autowired
    CapabilityRepository repository;

    @Test
    void capabilityDirectoryCanExcludeDisabledEntries() {
        String ref = "dashboard-count-" + System.nanoTime();
        Capability active = repository.save(new Capability(Capability.Type.SKILL, null,
                Capability.SourceKind.UPLOAD, ref + "-active", "Active dashboard skill", "test", "1", null,
                "sha-active"));
        Capability disabled = new Capability(Capability.Type.SKILL, null, Capability.SourceKind.UPLOAD,
                ref + "-disabled", "Disabled dashboard skill", "test", "1", null, "sha-disabled");
        disabled.disable();
        repository.save(disabled);

        List<Capability> visible = service.list(null, null, Capability.Status.DISABLED, false,
                Pageable.unpaged()).getContent();

        assertThat(visible).extracting(Capability::getId).contains(active.getId()).doesNotContain(disabled.getId());
        assertThat(visible).noneMatch(item -> item.getStatus() == Capability.Status.DISABLED);
    }

    @Test
    void toolsFollowMcpLifecycleAndGrantWithPerUserExclusions() {
        service.syncGateway(List.of(
                new CapabilityService.GatewayCapability(Capability.Type.MCP, null, "workbuddy", "WorkBuddy",
                        "WMS enterprise tools", "1", "gateway:mcp:workbuddy"),
                new CapabilityService.GatewayCapability(Capability.Type.TOOL, "workbuddy", "workbuddy/inventory",
                        "Inventory query", "Query inventory", "1", "gateway:tool:inventory"),
                new CapabilityService.GatewayCapability(Capability.Type.TOOL, "workbuddy", "workbuddy/purchase-order",
                        "Purchase order query", "Query purchase orders", "1", "gateway:tool:purchase-order")
        ));
        Capability mcp = repository.findByTypeAndExternalRefAndReleaseVersion(Capability.Type.MCP, "workbuddy", "1").orElseThrow();
        Capability tool = repository.findByTypeAndExternalRefAndReleaseVersion(Capability.Type.TOOL, "workbuddy/inventory", "1").orElseThrow();
        Capability secondTool = repository.findByTypeAndExternalRefAndReleaseVersion(
                Capability.Type.TOOL, "workbuddy/purchase-order", "1").orElseThrow();
        service.approve(mcp.getId(), "1000000");
        service.publish(mcp.getId(), "1000000");

        assertThat(repository.findById(tool.getId()).orElseThrow().getStatus()).isEqualTo(Capability.Status.PUBLISHED);
        assertThat(repository.findById(secondTool.getId()).orElseThrow().getStatus())
                .isEqualTo(Capability.Status.PUBLISHED);
        assertThatThrownBy(() -> service.grant("12345", tool.getId(), null, null, "1000000"))
                .hasMessageContaining("随所属 MCP 授权");

        service.grant("12345", mcp.getId(), null, null, "1000000");
        assertThat(service.check("12345", Capability.Type.TOOL, "workbuddy/inventory").allowed()).isTrue();
        assertThat(service.check("12345", Capability.Type.TOOL, "workbuddy/purchase-order").allowed()).isTrue();

        service.excludeTool("12345", mcp.getId(), secondTool.getId(), "1000000");
        assertThat(service.check("12345", Capability.Type.TOOL, "workbuddy/inventory").allowed()).isTrue();
        assertThat(service.check("12345", Capability.Type.TOOL, "workbuddy/purchase-order").allowed()).isFalse();
        assertThat(service.catalog("12345").mcps()).singleElement()
                .satisfies(item -> assertThat(item.tools()).singleElement()
                        .satisfies(view -> assertThat(view.externalRef()).isEqualTo("workbuddy/inventory")));
        assertThat(service.mcpToolGrantDetail("12345", mcp.getId()).tools())
                .filteredOn(CapabilityService.ToolGrantState::excluded)
                .singleElement()
                .satisfies(state -> assertThat(state.tool().externalRef()).isEqualTo("workbuddy/purchase-order"));

        service.syncGateway(List.of(new CapabilityService.GatewayCapability(Capability.Type.TOOL, "workbuddy",
                "workbuddy/material", "Material query", "Query materials", "1", "gateway:tool:material")));
        assertThat(service.check("12345", Capability.Type.TOOL, "workbuddy/material").allowed()).isTrue();
        assertThat(service.catalog("12345").mcps()).singleElement()
                .satisfies(item -> assertThat(item.tools()).hasSize(2));

        service.allowTool("12345", mcp.getId(), secondTool.getId(), "1000000");
        assertThat(service.check("12345", Capability.Type.TOOL, "workbuddy/purchase-order").allowed()).isTrue();
    }
}
