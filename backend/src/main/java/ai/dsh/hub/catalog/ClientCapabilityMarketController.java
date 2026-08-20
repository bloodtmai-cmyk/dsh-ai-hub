package ai.dsh.hub.catalog;

import ai.dsh.hub.access.MenuPermissionGrant;
import ai.dsh.hub.access.MenuPermissionService;
import ai.dsh.hub.common.IdentitySupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/client/capability-market")
public class ClientCapabilityMarketController {
    private final CapabilityApplicationService service;
    private final MenuPermissionService menuPermissionService;

    public ClientCapabilityMarketController(CapabilityApplicationService service,
                                            MenuPermissionService menuPermissionService) {
        this.service = service;
        this.menuPermissionService = menuPermissionService;
    }

    @GetMapping
    public List<CapabilityApplicationService.StoreItem> store(Authentication authentication) {
        menuPermissionService.require(authentication, MenuPermissionGrant.MenuKey.PLUGIN_MARKET);
        return service.store(IdentitySupport.requireWorkcode(authentication));
    }

    @PostMapping("/applications")
    public CapabilityApplicationService.ApplicationView apply(Authentication authentication,
                                                               @Valid @RequestBody ApplyRequest request) {
        menuPermissionService.require(authentication, MenuPermissionGrant.MenuKey.PLUGIN_MARKET);
        return service.apply(IdentitySupport.requireWorkcode(authentication), request.capabilityId(), request.reason());
    }

    public record ApplyRequest(@NotNull UUID capabilityId, @NotBlank @Size(max = 500) String reason) {
    }
}
