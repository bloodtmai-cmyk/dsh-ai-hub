package ai.dsh.hub.keymgmt;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyApplicationRepository extends JpaRepository<ApiKeyApplication, UUID> {
    List<ApiKeyApplication> findByWorkcodeOrderBySubmittedAtDesc(String workcode);
    Optional<ApiKeyApplication> findTopByWorkcodeOrderBySubmittedAtDesc(String workcode);
    Page<ApiKeyApplication> findByStatus(ApiKeyApplication.Status status, Pageable pageable);
    Page<ApiKeyApplication> findByWorkcodeContainingIgnoreCase(String workcode, Pageable pageable);
    Page<ApiKeyApplication> findByStatusAndWorkcodeContainingIgnoreCase(ApiKeyApplication.Status status,
                                                                        String workcode,
                                                                        Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ApiKeyApplication a where a.id = :id")
    Optional<ApiKeyApplication> findWithLockById(@Param("id") UUID id);
}
