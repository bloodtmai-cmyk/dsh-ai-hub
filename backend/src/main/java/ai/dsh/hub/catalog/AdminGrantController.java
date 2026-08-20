package ai.dsh.hub.catalog;

import ai.dsh.hub.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/grants")
public class AdminGrantController {
    private final CapabilityService service;

    public AdminGrantController(CapabilityService service) {
        this.service = service;
    }

    @GetMapping
    public List<SubjectGrant> list(@RequestParam String workcode) {
        return service.grants(workcode);
    }

    @GetMapping("/subjects")
    public PageResponse<CapabilityService.GrantSubjectSummary> subjects(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return PageResponse.from(service.grantSubjects(keyword,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))));
    }

    @PostMapping
    public SubjectGrant grant(@Valid @RequestBody GrantRequest request, Authentication authentication) {
        return service.grant(request.workcode(), request.capabilityId(), request.validFrom(), request.validUntil(),
                authentication.getName());
    }

    @DeleteMapping("/{id}")
    public void revoke(@PathVariable UUID id, Authentication authentication) {
        service.revokeGrant(id, authentication.getName());
    }

    @DeleteMapping("/subjects/{workcode}")
    public void revokeSubject(@PathVariable String workcode, Authentication authentication) {
        service.revokeSubjectGrants(workcode, authentication.getName());
    }

    @GetMapping("/subjects/{workcode}/mcps/{mcpId}/tools")
    public CapabilityService.McpToolGrantDetail mcpTools(@PathVariable String workcode,
                                                         @PathVariable UUID mcpId) {
        return service.mcpToolGrantDetail(workcode, mcpId);
    }

    @PostMapping("/subjects/{workcode}/mcps/{mcpId}/tools/{toolId}/exclusion")
    public SubjectToolExclusion excludeTool(@PathVariable String workcode, @PathVariable UUID mcpId,
                                            @PathVariable UUID toolId, Authentication authentication) {
        return service.excludeTool(workcode, mcpId, toolId, authentication.getName());
    }

    @DeleteMapping("/subjects/{workcode}/mcps/{mcpId}/tools/{toolId}/exclusion")
    public void allowTool(@PathVariable String workcode, @PathVariable UUID mcpId,
                          @PathVariable UUID toolId, Authentication authentication) {
        service.allowTool(workcode, mcpId, toolId, authentication.getName());
    }

    public record GrantRequest(@NotBlank String workcode, @NotNull UUID capabilityId,
                               Instant validFrom, Instant validUntil) {
    }
}
