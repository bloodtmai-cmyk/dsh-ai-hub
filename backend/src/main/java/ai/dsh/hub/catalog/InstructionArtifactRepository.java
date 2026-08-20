package ai.dsh.hub.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InstructionArtifactRepository extends JpaRepository<InstructionArtifact, UUID> {
    Optional<InstructionArtifact> findByCapabilityId(UUID capabilityId);
}
