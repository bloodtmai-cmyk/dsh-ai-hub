package ai.dsh.hub.catalog;

import ai.dsh.hub.common.PageResponse;
import ai.dsh.hub.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/admin/capabilities")
public class AdminCapabilityController {
    private final CapabilityService service;

    public AdminCapabilityController(CapabilityService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<Capability> list(@RequestParam(required = false) Capability.Type type,
                                         @RequestParam(required = false) Capability.Status status,
                                         @RequestParam(required = false) Capability.Status excludeStatus,
                                         @RequestParam(defaultValue = "false") boolean directoryOnly,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        return PageResponse.from(service.list(type, status, excludeStatus, directoryOnly,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "updatedAt"))));
    }

    @GetMapping("/{id}/tools")
    public List<Capability> tools(@PathVariable UUID id) {
        return service.mcpTools(id);
    }

    @PostMapping
    public Capability create(@Valid @RequestBody CreateRequest request, Authentication authentication) {
        if (request.type() == Capability.Type.SKILL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SKILL_UPLOAD_REQUIRED", "Skill 必须通过文件上传创建");
        }
        if (request.type() == Capability.Type.BUNDLE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BUNDLE_ENDPOINT_REQUIRED", "Bundle 必须通过组合接口创建");
        }
        if (request.type() == Capability.Type.CLIENT_PLUGIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLUGIN_UPLOAD_REQUIRED", "客户端插件必须通过文件上传创建");
        }
        if (request.type() == Capability.Type.INSTRUCTION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSTRUCTION_UPLOAD_REQUIRED", "企业指令必须通过 AGENTS.md 上传创建");
        }
        return service.create(request.toCommand(), authentication.getName());
    }

    @PostMapping("/{id}/approve")
    public Capability approve(@PathVariable UUID id, Authentication authentication) {
        return service.approve(id, authentication.getName());
    }

    @PostMapping("/{id}/publish")
    public Capability publish(@PathVariable UUID id, Authentication authentication) {
        return service.publish(id, authentication.getName());
    }

    @PostMapping("/{id}/disable")
    public Capability disable(@PathVariable UUID id, Authentication authentication) {
        return service.disable(id, authentication.getName());
    }

    public record CreateRequest(
            @NotNull Capability.Type type,
            UUID parentId,
            @NotNull Capability.SourceKind sourceKind,
            @NotBlank @Size(max = 240) String externalRef,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 1000) String description,
            @NotBlank @Size(max = 80) String releaseVersion,
            @Size(max = 500) String sourceRef,
            @Size(max = 128) String integrityHash
    ) {
        CapabilityService.CreateCommand toCommand() {
            return new CapabilityService.CreateCommand(type, parentId, sourceKind, externalRef, name, description,
                    releaseVersion, sourceRef, integrityHash);
        }
    }
}
