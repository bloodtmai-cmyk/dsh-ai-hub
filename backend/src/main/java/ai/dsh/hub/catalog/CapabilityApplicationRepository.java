package ai.dsh.hub.catalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapabilityApplicationRepository extends JpaRepository<CapabilityApplication, UUID> {
    List<CapabilityApplication> findByWorkcodeOrderBySubmittedAtDesc(String workcode);
    Optional<CapabilityApplication> findTopByWorkcodeAndCapabilityIdOrderBySubmittedAtDesc(String workcode, UUID capabilityId);
    Page<CapabilityApplication> findByStatus(CapabilityApplication.Status status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from CapabilityApplication application where application.id = :id")
    Optional<CapabilityApplication> findWithLockById(UUID id);
}
