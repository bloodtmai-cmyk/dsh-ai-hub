package ai.dsh.hub.admin;

import ai.dsh.hub.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {
    private final AdminAuditLogRepository repository;

    public AdminAuditController(AdminAuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public PageResponse<AdminAuditLog> list(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "30") int size) {
        return PageResponse.from(repository.findAllByOrderByOccurredAtDesc(PageRequest.of(page, Math.min(size, 100))));
    }
}
