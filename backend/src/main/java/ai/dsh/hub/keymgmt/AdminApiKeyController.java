package ai.dsh.hub.keymgmt;

import ai.dsh.hub.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/key-applications")
public class AdminApiKeyController {
    private final ApiKeyApplicationService service;

    public AdminApiKeyController(ApiKeyApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ApiKeyApplicationService.ApplicationView> list(
            @RequestParam(required = false) ApiKeyApplication.Status status,
            @RequestParam(required = false) String workcode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return PageResponse.from(service.listForAdmin(status, workcode,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "submittedAt"))));
    }

    @PostMapping("/grant")
    public ApiKeyApplicationService.ApplicationView grant(@Valid @RequestBody GrantRequest request,
                                                          Authentication authentication) {
        return service.grant(request.workcode(), authentication.getName(), request.provider(),
                request.baseUrl(), request.apiKey());
    }

    @PostMapping("/{id}/approve")
    public ApiKeyApplicationService.ApplicationView approve(@PathVariable UUID id,
                                                            @Valid @RequestBody ApproveRequest request,
                                                            Authentication authentication) {
        return service.approve(id, authentication.getName(), request.provider(), request.baseUrl(), request.apiKey());
    }

    @PostMapping("/{id}/reject")
    public ApiKeyApplicationService.ApplicationView reject(@PathVariable UUID id,
                                                           @Valid @RequestBody DecisionRequest request,
                                                           Authentication authentication) {
        return service.reject(id, authentication.getName(), request.comment());
    }

    @PostMapping("/{id}/revoke")
    public ApiKeyApplicationService.ApplicationView revoke(@PathVariable UUID id, Authentication authentication) {
        return service.revoke(id, authentication.getName());
    }

    public record ApproveRequest(@NotBlank @Size(max = 80) String provider,
                                 @NotBlank @Size(max = 1000) String baseUrl,
                                 @NotBlank @Size(max = 4096) String apiKey) {
    }

    public record DecisionRequest(@NotBlank @Size(max = 500) String comment) {
    }

    public record GrantRequest(
            @NotBlank @Size(max = 12) @Pattern(regexp = "[A-Za-z0-9]+") String workcode,
            @NotBlank @Size(max = 80) String provider,
            @NotBlank @Size(max = 1000) String baseUrl,
            @NotBlank @Size(max = 4096) String apiKey
    ) {
    }
}
