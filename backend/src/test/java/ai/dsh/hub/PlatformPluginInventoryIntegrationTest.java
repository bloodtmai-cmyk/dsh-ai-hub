package ai.dsh.hub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformPluginInventoryIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void administratorCanReadTheDisabledExtensionHostSnapshot() throws Exception {
        var login = mvc.perform(post("/api/admin/session").with(csrf())
                        .contentType("application/json")
                        .content("{\"workcode\":\"1000000\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        mvc.perform(get("/api/admin/platform-plugins").session((org.springframework.mock.web.MockHttpSession) login.getRequest().getSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reachable").value(false))
                .andExpect(jsonPath("$.plugins").isEmpty());
    }
}
