package ai.dsh.hub.keymgmt;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyBindingRepository extends JpaRepository<ApiKeyBinding, UUID> {
    Optional<ApiKeyBinding> findByApplicationId(UUID applicationId);
    List<ApiKeyBinding> findByApplicationIdIn(Collection<UUID> applicationIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ApiKeyBinding b where b.applicationId = :applicationId")
    Optional<ApiKeyBinding> findWithLockByApplicationId(@Param("applicationId") UUID applicationId);
}
