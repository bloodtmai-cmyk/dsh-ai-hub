package ai.dsh.hub.catalog;

import ai.dsh.hub.common.IdentitySupport;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/catalog")
public class ClientCatalogController {
    private final CapabilityService service;

    public ClientCatalogController(CapabilityService service) {
        this.service = service;
    }

    @GetMapping
    public CapabilityService.Catalog catalog(Authentication authentication) {
        return service.catalog(IdentitySupport.requireWorkcode(authentication));
    }
}
