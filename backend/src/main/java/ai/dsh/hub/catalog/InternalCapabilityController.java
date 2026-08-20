package ai.dsh.hub.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/internal")
public class InternalCapabilityController {
    private final CapabilityService service;
    private final GatewayCatalogConnector gatewayCatalog;

    public InternalCapabilityController(CapabilityService service, GatewayCatalogConnector gatewayCatalog) {
        this.service = service;
        this.gatewayCatalog = gatewayCatalog;
    }

    @PostMapping("/gateway/capabilities/sync")
    public CapabilityService.SyncResult sync(@Valid @RequestBody SyncRequest request) {
        return service.syncGateway(gatewayCatalog.normalize(request.capabilities()));
    }

    @PostMapping("/authorization/check")
    public CapabilityService.AuthorizationDecision check(@Valid @RequestBody CheckRequest request) {
        return service.check(request.workcode(), request.type(), request.externalRef());
    }

    @GetMapping("/authorization/snapshot/{workcode}")
    public CapabilityService.Catalog snapshot(@org.springframework.web.bind.annotation.PathVariable String workcode) {
        return service.catalog(workcode);
    }

    public record SyncRequest(@NotEmpty @Size(max = 1000) List<@Valid SyncItem> capabilities) {
    }

    public record SyncItem(
            @NotNull Capability.Type type,
            String parentExternalRef,
            @NotBlank @Size(max = 240) String externalRef,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 1000) String description,
            @NotBlank @Size(max = 80) String releaseVersion,
            @Size(max = 500) String sourceRef
    ) {
        CapabilityService.GatewayCapability toCommand() {
            return new CapabilityService.GatewayCapability(type, parentExternalRef, externalRef, name, description,
                    releaseVersion, sourceRef);
        }
    }

    public record CheckRequest(@NotBlank String workcode, @NotNull Capability.Type type,
                               @NotBlank String externalRef, @NotNull Action action) {
    }

    public enum Action { LIST, CALL, DOWNLOAD }
}
