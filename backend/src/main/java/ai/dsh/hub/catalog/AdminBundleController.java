package ai.dsh.hub.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/bundles")
public class AdminBundleController {
    private final CapabilityService service;

    public AdminBundleController(CapabilityService service) {
        this.service = service;
    }

    @PostMapping
    public CapabilityService.BundleDetail create(@Valid @RequestBody CreateRequest request,
                                                 Authentication authentication) {
        return service.createBundle(new CapabilityService.CreateBundleCommand(request.externalRef(), request.name(),
                request.description(), request.releaseVersion(), request.memberIds()), authentication.getName());
    }

    @GetMapping("/{id}")
    public CapabilityService.BundleDetail detail(@PathVariable UUID id) {
        return service.bundle(id);
    }

    @PutMapping("/{id}/members")
    public CapabilityService.BundleDetail replaceMembers(@PathVariable UUID id,
                                                         @Valid @RequestBody MembersRequest request,
                                                         Authentication authentication) {
        return service.replaceBundleMembers(id, request.memberIds(), authentication.getName());
    }

    public record CreateRequest(
            @NotBlank @Size(max = 240) String externalRef,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 1000) String description,
            @NotBlank @Size(max = 80) String releaseVersion,
            @NotEmpty @Size(max = 100) List<UUID> memberIds
    ) {
    }

    public record MembersRequest(@NotEmpty @Size(max = 100) List<UUID> memberIds) {
    }
}
