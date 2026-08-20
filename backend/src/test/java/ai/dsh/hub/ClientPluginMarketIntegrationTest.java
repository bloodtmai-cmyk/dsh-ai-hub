package ai.dsh.hub;

import ai.dsh.hub.catalog.Capability;
import ai.dsh.hub.catalog.CapabilityApplicationService;
import ai.dsh.hub.catalog.CapabilityService;
import ai.dsh.hub.catalog.ClientPluginArtifactService;
import ai.dsh.hub.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ClientPluginMarketIntegrationTest {
    @Autowired
    ClientPluginArtifactService artifactService;
    @Autowired
    CapabilityService capabilityService;
    @Autowired
    CapabilityApplicationService applicationService;

    @Test
    void pluginMovesFromUploadThroughApprovalAndUserDelivery() throws Exception {
        byte[] artifact = pluginZip("1.5.0", "hot");
        Capability plugin = artifactService.upload("agent-reach.zip", "application/zip", artifact, "1000000");
        capabilityService.approve(plugin.getId(), "1000000");
        capabilityService.publish(plugin.getId(), "1000000");

        assertThat(applicationService.store("12350")).filteredOn(item -> item.capability().id().equals(plugin.getId()))
                .singleElement().satisfies(item -> {
            assertThat(item.capability().externalRef()).isEqualTo("agent-reach");
            assertThat(item.state()).isEqualTo(CapabilityApplicationService.StoreState.AVAILABLE);
        });

        var application = applicationService.apply("12350", plugin.getId(), "调研公开资料");
        assertThat(applicationService.store("12350")).filteredOn(item -> item.capability().id().equals(plugin.getId()))
                .singleElement()
                .satisfies(item -> assertThat(item.state()).isEqualTo(CapabilityApplicationService.StoreState.PENDING));
        applicationService.approve(application.id(), "1000000", null, "同意试用");

        assertThat(applicationService.store("12350")).filteredOn(item -> item.capability().id().equals(plugin.getId()))
                .singleElement()
                .satisfies(item -> assertThat(item.state()).isEqualTo(CapabilityApplicationService.StoreState.AUTHORIZED));
        assertThat(capabilityService.catalog("12350").plugins()).singleElement()
                .satisfies(item -> assertThat(item.externalRef()).isEqualTo("agent-reach"));
        assertThat(artifactService.download(plugin.getId(), "12350").content()).isEqualTo(artifact);

        var bundle = capabilityService.createBundle(new CapabilityService.CreateBundleCommand(
                "research-bundle", "调研套件", "包含 Agent Reach 的受管套件", "1.0.0",
                List.of(plugin.getId())), "1000000");
        capabilityService.approve(bundle.bundle().id(), "1000000");
        capabilityService.publish(bundle.bundle().id(), "1000000");
        capabilityService.grant("12351", bundle.bundle().id(), null, null, "1000000");

        byte[] upgradedArtifact = pluginZip("1.6.0", "hot");
        Capability upgraded = artifactService.upload("agent-reach-1.6.0.zip", "application/zip",
                upgradedArtifact, "1000000");
        capabilityService.approve(upgraded.getId(), "1000000");
        capabilityService.publish(upgraded.getId(), "1000000");

        assertThat(capabilityService.catalog("12350").plugins()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(upgraded.getId());
            assertThat(item.releaseVersion()).isEqualTo("1.6.0");
        });
        assertThat(capabilityService.bundle(bundle.bundle().id()).members()).singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo(upgraded.getId()));
        assertThat(capabilityService.catalog("12351").plugins()).singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo(upgraded.getId()));
        assertThat(artifactService.download(upgraded.getId(), "12350").content()).isEqualTo(upgradedArtifact);

        Capability downgrade = artifactService.upload("agent-reach-1.5.5.zip", "application/zip",
                pluginZip("1.5.5", "hot"), "1000000");
        capabilityService.approve(downgrade.getId(), "1000000");
        assertThatThrownBy(() -> capabilityService.publish(downgrade.getId(), "1000000"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo("CLIENT_PLUGIN_VERSION_NOT_HIGHER"));
    }

    @Test
    void pluginUploadRejectsRestartBoundActivation() throws Exception {
        byte[] artifact = pluginZip("1.5.0", "restart");

        assertThatThrownBy(() -> artifactService.upload("legacy.zip", "application/zip", artifact, "1000000"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo("UNSUPPORTED_PLUGIN_ACTIVATION"));
    }

    private static byte[] pluginZip(String version, String activation) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("plugin.json"));
            zip.write(("""
                    {"schemaVersion":1,"id":"agent-reach","name":"Agent Reach","description":"Managed research plugin","version":"%s","entry":"index.mjs","activation":"%s"}
                    """.formatted(version, activation)).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("index.mjs"));
            zip.write("export function apply() {}\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
