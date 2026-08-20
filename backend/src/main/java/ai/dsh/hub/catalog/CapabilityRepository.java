package ai.dsh.hub.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapabilityRepository extends JpaRepository<Capability, UUID> {
    Optional<Capability> findByTypeAndExternalRefAndReleaseVersion(Capability.Type type, String externalRef, String releaseVersion);
    List<Capability> findByTypeAndExternalRefAndStatus(Capability.Type type, String externalRef, Capability.Status status);
    List<Capability> findByParentIdAndStatus(UUID parentId, Capability.Status status);
    List<Capability> findByParentIdOrderByNameAsc(UUID parentId);
    List<Capability> findByIdIn(Collection<UUID> ids);
    Page<Capability> findByType(Capability.Type type, Pageable pageable);
    Page<Capability> findByStatus(Capability.Status status, Pageable pageable);
    Page<Capability> findByStatusNot(Capability.Status status, Pageable pageable);
    Page<Capability> findByTypeAndStatus(Capability.Type type, Capability.Status status, Pageable pageable);
    Page<Capability> findByTypeAndStatusNot(Capability.Type type, Capability.Status status, Pageable pageable);
    Page<Capability> findByTypeNotIn(Collection<Capability.Type> types, Pageable pageable);
    Page<Capability> findByTypeNotInAndStatus(Collection<Capability.Type> types, Capability.Status status,
                                              Pageable pageable);
    Page<Capability> findByTypeNotInAndStatusNot(Collection<Capability.Type> types, Capability.Status status,
                                                 Pageable pageable);
    List<Capability> findByStatusOrderByNameAsc(Capability.Status status);
}
