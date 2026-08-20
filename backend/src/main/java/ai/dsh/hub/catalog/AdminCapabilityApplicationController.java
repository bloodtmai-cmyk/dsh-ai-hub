package ai.dsh.hub.catalog;

import ai.dsh.hub.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/capability-applications")
public class AdminCapabilityApplicationController {
    private final CapabilityApplicationService service;

    public AdminCapabilityApplicationController(CapabilityApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<CapabilityApplicationService.ApplicationView> list(
            @RequestParam(required = false) CapabilityApplication.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return PageResponse.from(service.listForAdmin(status,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "submittedAt"))));
    }

    @PostMapping("/{id}/approve")
    public CapabilityApplicationService.ApplicationView approve(@PathVariable UUID id,
                                                                 @Valid @RequestBody ApproveRequest request,
                                                                 Authentication authentication) {
        return service.approve(id, authentication.getName(), request.validUntil(), request.comment());
    }

    @PostMapping("/{id}/reject")
    public CapabilityApplicationService.ApplicationView reject(@PathVariable UUID id,
                                                                @Valid @RequestBody RejectRequest request,
                                                                Authentication authentication) {
        return service.reject(id, authentication.getName(), request.comment());
    }

    public record ApproveRequest(Instant validUntil, @Size(max = 500) String comment) {
    }
    public record RejectRequest(@NotBlank @Size(max = 500) String comment) {
    }
}
