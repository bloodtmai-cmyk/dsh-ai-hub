package ai.dsh.hub.access;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/menu-entitlements")
public class ClientMenuPermissionController {
    private final MenuPermissionService service;

    public ClientMenuPermissionController(MenuPermissionService service) {
        this.service = service;
    }

    @GetMapping
    public MenuPermissionService.Entitlements entitlements(Authentication authentication) {
        return service.entitlements(authentication);
    }
}
