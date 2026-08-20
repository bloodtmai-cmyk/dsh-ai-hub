package ai.dsh.hub.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SkillArtifactRepository extends JpaRepository<SkillArtifact, UUID> {
    Optional<SkillArtifact> findByCapabilityId(UUID capabilityId);
}
