package ai.dsh.hub.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {
    Page<AdminAuditLog> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
