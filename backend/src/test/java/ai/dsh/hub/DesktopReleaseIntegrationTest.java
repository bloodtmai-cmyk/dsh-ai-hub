package ai.dsh.hub;

import ai.dsh.hub.common.ApiException;
import ai.dsh.hub.release.DesktopRelease;
import ai.dsh.hub.release.DesktopReleaseRepository;
import ai.dsh.hub.release.DesktopReleaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DesktopReleaseIntegrationTest {
    @Autowired
    DesktopReleaseService service;
    @Autowired
    DesktopReleaseRepository repository;
    @Autowired
    MockMvc mockMvc;

    @Test
    void publishesOneMonotonicReleasePerPlatformAndServesOnlyNewerVersions() throws Exception {
        byte[] firstContent = "first desktop artifact".getBytes(StandardCharsets.UTF_8);
        DesktopReleaseService.ReleaseView first = service.upload(DesktopRelease.Platform.MAC_ARM64, "9.0.0",
                "首个测试版本", file("DSH-Harness-9.0.0-arm64.dmg", firstContent), "1000000");
        assertThat(first.status()).isEqualTo(DesktopRelease.Status.DRAFT);
        assertThat(first.sha256()).isEqualTo(sha256(firstContent));

        first = service.publish(first.id(), "1000000");
        assertThat(first.status()).isEqualTo(DesktopRelease.Status.PUBLISHED);
        assertThat(service.latest(DesktopRelease.Platform.MAC_ARM64, "8.9.0"))
                .hasValueSatisfying(item -> assertThat(item.version()).isEqualTo("9.0.0"));
        assertThat(service.latest(DesktopRelease.Platform.MAC_ARM64, "9.0.0")).isEmpty();

        byte[] secondContent = "second desktop artifact".getBytes(StandardCharsets.UTF_8);
        DesktopReleaseService.ReleaseView second = service.upload(DesktopRelease.Platform.MAC_ARM64, "9.1.0",
                "第二个测试版本", file("DSH-Harness-9.1.0-arm64.pkg", secondContent), "1000000");
        second = service.publish(second.id(), "1000000");

        assertThat(repository.findById(first.id()).orElseThrow().getStatus())
                .isEqualTo(DesktopRelease.Status.SUPERSEDED);
        assertThat(second.status()).isEqualTo(DesktopRelease.Status.PUBLISHED);
        assertThat(service.clientDownload(second.id()).resource().getContentAsByteArray()).isEqualTo(secondContent);

        mockMvc.perform(get("/api/client/desktop-releases/latest")
                        .param("platform", "MAC_ARM64")
                        .param("currentVersion", "9.0.0")
                        .with(jwt().jwt(token -> token.subject("12501").claim("workcode", "12501"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("9.1.0"))
                .andExpect(jsonPath("$.sha256").value(sha256(secondContent)));
        mockMvc.perform(get("/api/client/desktop-releases/{id}/artifact", second.id())
                        .with(jwt().jwt(token -> token.subject("12501").claim("workcode", "12501"))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", '"' + sha256(secondContent) + '"'));
    }

    @Test
    void rejectsTheWrongInstallerFormatForTheTargetPlatform() {
        assertThatThrownBy(() -> service.upload(DesktopRelease.Platform.WINDOWS_X64, "9.2.0", "测试版本",
                file("DSH-Harness.dmg", new byte[]{1}), "1000000"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(".exe");
    }

    @Test
    void servesAnAuthenticatedElectronUpdaterFeedAndInstaller() throws Exception {
        byte[] installer = "windows electron updater artifact".getBytes(StandardCharsets.UTF_8);
        DesktopReleaseService.ReleaseView release = service.upload(DesktopRelease.Platform.WINDOWS_X64, "9.2.0",
                "Windows 覆盖升级测试", file("DSH-Harness-Setup-9.2.0-x64.exe", installer), "1000000");
        release = service.publish(release.id(), "1000000");

        mockMvc.perform(get("/api/client/desktop-updates/windows/latest.yml"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/client/desktop-updates/windows/latest.yml")
                        .with(jwt().jwt(token -> token.subject("12501").claim("workcode", "12501"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/yaml"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("version: 9.2.0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "url: artifacts/" + release.id() + ".exe")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(sha512(installer))))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mockMvc.perform(get("/api/client/desktop-updates/windows/artifacts/{id}.exe", release.id())
                        .with(jwt().jwt(token -> token.subject("12501").claim("workcode", "12501"))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(installer))
                .andExpect(header().string("ETag", '"' + sha256(installer) + '"'));
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static String sha512(byte[] content) throws Exception {
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(content));
    }
}
