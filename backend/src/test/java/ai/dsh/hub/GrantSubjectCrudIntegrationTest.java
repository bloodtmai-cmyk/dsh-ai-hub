package ai.dsh.hub;

import ai.dsh.hub.catalog.Capability;
import ai.dsh.hub.catalog.CapabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@AutoConfigureMockMvc
class GrantSubjectCrudIntegrationTest {

    @Autowired
    CapabilityService service;
    @Autowired
    MockMvc mockMvc;

    @Test
    void listsCurrentGrantSubjectsAndRevokesTheirWholeGrantSet() throws Exception {
        Capability capability = service.create(new CapabilityService.CreateCommand(
                Capability.Type.MCP, null, Capability.SourceKind.MANUAL,
                "grant-subject-test", "授权列表测试能力", "验证用户授权列表", "1", null, null), "1000001");
        service.approve(capability.getId(), "1000001");
        service.publish(capability.getId(), "1000001");
        service.grant("26001", capability.getId(), null, null, "1000001");

        mockMvc.perform(get("/api/admin/grants/subjects")
                        .param("keyword", "26001")
                        .with(user("1000001").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].workcode").value("26001"))
                .andExpect(jsonPath("$.content[0].activeGrantCount").value(1))
                .andExpect(jsonPath("$.content[0].capabilities[0].name").value("授权列表测试能力"));

        mockMvc.perform(delete("/api/admin/grants/subjects/26001")
                        .with(user("1000001").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(service.grants("26001")).allSatisfy(grant -> assertThat(grant.isEnabled()).isFalse());
    }
}
