package ai.dsh.hub.access;

import ai.dsh.hub.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/menu-permissions")
public class AdminMenuPermissionController {
    private final MenuPermissionService service;

    public AdminMenuPermissionController(MenuPermissionService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<MenuPermissionGrant> list(
            @RequestParam(required = false) MenuPermissionGrant.SubjectType subjectType,
            @RequestParam(required = false) String subjectRef,
            @RequestParam(required = false) MenuPermissionGrant.MenuKey menuKey,
            @RequestParam(defaultValue = "false") boolean includeRevoked,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return PageResponse.from(service.list(subjectType, subjectRef, menuKey, includeRevoked,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "updatedAt"))));
    }

    @PostMapping
    public MenuPermissionGrant grant(@Valid @RequestBody Request request, Authentication authentication) {
        return service.grant(request.subjectType(), request.subjectRef(), request.menuKey(), authentication.getName());
    }

    @PutMapping("/{id}")
    public MenuPermissionGrant replace(@PathVariable UUID id, @Valid @RequestBody Request request,
                                       Authentication authentication) {
        return service.replace(id, request.subjectType(), request.subjectRef(), request.menuKey(),
                authentication.getName());
    }

    @DeleteMapping("/{id}")
    public void revoke(@PathVariable UUID id, Authentication authentication) {
        service.revoke(id, authentication.getName());
    }

    public record Request(@NotNull MenuPermissionGrant.SubjectType subjectType,
                          @NotBlank String subjectRef,
                          @NotNull MenuPermissionGrant.MenuKey menuKey) {
    }
}
