package ai.dsh.hub.audit;

import ai.dsh.hub.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/conversations")
public class AdminConversationAuditController {
    private final ConversationAuditService service;

    public AdminConversationAuditController(ConversationAuditService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ConversationAuditService.Summary> list(
            @RequestParam(required = false) String workcode,
            @RequestParam(required = false) ConversationAudit.Status status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return PageResponse.from(service.search(workcode, status, from, to,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "startedAt"))));
    }

    @GetMapping("/{id}")
    public ConversationAuditService.Detail detail(@PathVariable UUID id, Authentication authentication) {
        return service.detail(id, authentication.getName());
    }
}
