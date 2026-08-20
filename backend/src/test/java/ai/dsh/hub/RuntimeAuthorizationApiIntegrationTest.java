package ai.dsh.hub;

import ai.dsh.hub.catalog.Capability;
import ai.dsh.hub.catalog.CapabilityRepository;
import ai.dsh.hub.catalog.CapabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RuntimeAuthorizationApiIntegrationTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    CapabilityService service;
    @Autowired
    CapabilityRepository repository;

    @BeforeEach
    void prepareAuthorizedTool() {
        String mcpRef = "api-test-mcp";
        String toolRef = "api-test-mcp/tool";
        if (repository.findByTypeAndExternalRefAndReleaseVersion(Capability.Type.MCP, mcpRef, "1").isPresent()) return;
        service.syncGateway(List.of(
                new CapabilityService.GatewayCapability(Capability.Type.MCP, null, mcpRef, "API Test MCP", "test", "1", "gateway:test"),
                new CapabilityService.GatewayCapability(Capability.Type.TOOL, mcpRef, toolRef, "API Test Tool", "test", "1", "gateway:test-tool")
        ));
        Capability mcp = repository.findByTypeAndExternalRefAndReleaseVersion(Capability.Type.MCP, mcpRef, "1").orElseThrow();
        service.approve(mcp.getId(), "1000000");
        service.publish(mcp.getId(), "1000000");
        service.grant("12348", mcp.getId(), null, null, "1000000");
    }

    @Test
    void internalCheckRequiresServiceIdentityAndReturnsEffectiveDecision() throws Exception {
        String body = """
                {"workcode":"12348","type":"TOOL","externalRef":"api-test-mcp/tool","action":"CALL"}
                """;
        mockMvc.perform(post("/api/internal/authorization/check")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/internal/authorization/check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-internal-service-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void clientCatalogUsesAuthenticatedWorkcode() throws Exception {
        mockMvc.perform(get("/api/client/catalog")
                        .with(jwt().jwt(token -> token.subject("12348").claim("workcode", "12348"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mcps[0].mcp.externalRef").value("api-test-mcp"))
                .andExpect(jsonPath("$.mcps[0].tools[0].externalRef").value("api-test-mcp/tool"));
    }
}
