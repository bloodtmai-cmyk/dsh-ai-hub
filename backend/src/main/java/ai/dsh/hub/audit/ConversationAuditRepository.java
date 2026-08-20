package ai.dsh.hub.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ConversationAuditRepository extends JpaRepository<ConversationAudit, UUID>,
        JpaSpecificationExecutor<ConversationAudit> {
    Optional<ConversationAudit> findByWorkcodeAndSessionIdAndTurnId(String workcode, String sessionId, String turnId);
}
