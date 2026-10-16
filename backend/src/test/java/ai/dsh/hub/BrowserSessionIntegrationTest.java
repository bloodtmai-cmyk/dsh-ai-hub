package ai.dsh.hub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 独立上下文，避免其他测试的 csrf() 后处理器替换实际 CookieCsrfTokenRepository。
@SpringBootTest(properties = "spring.application.name=dsh-ai-hub-browser-session-test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BrowserSessionIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper mapper;

    @Test
    void browserCsrfFlowSupportsLoginAndLogout() throws Exception {
        var csrfResponse = mockMvc.perform(get("/api/admin/csrf"))
                .andExpect(status().isOk()).andReturn().getResponse();
        var csrf = mapper.readTree(csrfResponse.getContentAsString());
        assertThat(csrfResponse.getCookie("XSRF-TOKEN")).isNotNull();
        var login = mockMvc.perform(post("/api/admin/session")
                        .cookie(csrfResponse.getCookies())
                        .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workcode\":\"1000000\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        var logoutCsrfResponse = mockMvc.perform(get("/api/admin/csrf").session(session))
                .andExpect(status().isOk()).andReturn().getResponse();
        var logoutCsrf = mapper.readTree(logoutCsrfResponse.getContentAsString());
        mockMvc.perform(delete("/api/admin/session").session(session)
                        .cookie(logoutCsrfResponse.getCookies())
                        .header(logoutCsrf.path("headerName").asText(), logoutCsrf.path("token").asText()))
                .andExpect(status().isOk());
        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/admin/platform-plugins"))
                .andExpect(status().isUnauthorized());
    }
}
