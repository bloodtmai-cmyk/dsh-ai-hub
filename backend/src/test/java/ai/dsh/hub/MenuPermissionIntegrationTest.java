package ai.dsh.hub;

import ai.dsh.hub.access.MenuPermissionGrant;
import ai.dsh.hub.access.MenuPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MenuPermissionIntegrationTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    MenuPermissionService service;

    @Test
    void pluginMarketRequiresAnIndependentUserMenuGrant() throws Exception {
        String workcode = "12401";
        mockMvc.perform(get("/api/client/capability-market")
                        .with(jwt().jwt(token -> token.subject(workcode).claim("workcode", workcode))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MENU_NOT_GRANTED"));

        MenuPermissionGrant grant = service.grant(MenuPermissionGrant.SubjectType.USER, workcode,
                MenuPermissionGrant.MenuKey.PLUGIN_MARKET, "1000000");

        mockMvc.perform(get("/api/client/menu-entitlements")
                        .with(jwt().jwt(token -> token.subject(workcode).claim("workcode", workcode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menus[0]").value("PLUGIN_MARKET"));
        mockMvc.perform(get("/api/client/capability-market")
                        .with(jwt().jwt(token -> token.subject(workcode).claim("workcode", workcode))))
                .andExpect(status().isOk());

        service.revoke(grant.getId(), "1000000");
        mockMvc.perform(get("/api/client/capability-market")
                        .with(jwt().jwt(token -> token.subject(workcode).claim("workcode", workcode))))
                .andExpect(status().isForbidden());
    }

    @Test
    void departmentMenuGrantUsesTheTrustedJwtDepartmentClaim() throws Exception {
        service.grant(MenuPermissionGrant.SubjectType.DEPARTMENT, "IT.AI",
                MenuPermissionGrant.MenuKey.PLUGIN_MARKET, "1000000");

        mockMvc.perform(get("/api/client/capability-market")
                        .with(jwt().jwt(token -> token.subject("12402")
                                .claim("workcode", "12402")
                                .claim("department_codes", List.of("IT.AI", "IT.SECURITY")))))
                .andExpect(status().isOk());
    }

    @Test
    void menuPermissionSupportsListAndUpdateCrud() {
        MenuPermissionGrant grant = service.grant(MenuPermissionGrant.SubjectType.USER, "12403",
                MenuPermissionGrant.MenuKey.PLUGIN_MARKET, "1000000");
        service.replace(grant.getId(), MenuPermissionGrant.SubjectType.USER, "12404",
                MenuPermissionGrant.MenuKey.PLUGIN_MARKET, "1000000");

        assertThat(service.list(MenuPermissionGrant.SubjectType.USER, "12404",
                MenuPermissionGrant.MenuKey.PLUGIN_MARKET, false, PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(item -> assertThat(item.getSubjectRef()).isEqualTo("12404"));
    }
}
