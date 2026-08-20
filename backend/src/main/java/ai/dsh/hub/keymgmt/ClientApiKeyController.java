package ai.dsh.hub.keymgmt;

import ai.dsh.hub.common.IdentitySupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/client/key-applications")
public class ClientApiKeyController {
    private final ApiKeyApplicationService service;

    public ClientApiKeyController(ApiKeyApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ApiKeyApplicationService.ApplicationView apply(Authentication authentication,
                                                          @Valid @RequestBody ApplyRequest request) {
        return service.apply(IdentitySupport.requireWorkcode(authentication), request.purpose());
    }

    @GetMapping
    public List<ApiKeyApplicationService.ApplicationView> list(Authentication authentication) {
        return service.listForUser(IdentitySupport.requireWorkcode(authentication));
    }

    @PostMapping("/{id}/secret:claim")
    public ApiKeyApplicationService.ClaimedSecret claim(@PathVariable UUID id, Authentication authentication) {
        return service.claim(id, IdentitySupport.requireWorkcode(authentication));
    }

    public record ApplyRequest(@NotBlank @Size(max = 500) String purpose) {
    }
}
