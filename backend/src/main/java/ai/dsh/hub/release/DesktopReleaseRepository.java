package ai.dsh.hub.release;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DesktopReleaseRepository extends JpaRepository<DesktopRelease, UUID> {
    boolean existsByPlatformAndVersion(DesktopRelease.Platform platform, String version);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select release from DesktopRelease release where release.id = :id")
    Optional<DesktopRelease> findLockedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<DesktopRelease> findByPlatformAndStatus(DesktopRelease.Platform platform, DesktopRelease.Status status);

    Optional<DesktopRelease> findFirstByPlatformAndStatusOrderByPublishedAtDesc(
            DesktopRelease.Platform platform, DesktopRelease.Status status);
}
