package ai.dsh.hub.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditService {
    private final AdminAuditLogRepository repository;

    public AdminAuditService(AdminAuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, String action, String targetType, String targetId, String outcome, String detail) {
        repository.save(new AdminAuditLog(actor, action, targetType, targetId, outcome, detail));
    }
}
