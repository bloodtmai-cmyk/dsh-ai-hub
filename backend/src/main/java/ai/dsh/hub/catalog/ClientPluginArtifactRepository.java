package ai.dsh.hub.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientPluginArtifactRepository extends JpaRepository<ClientPluginArtifact, UUID> {
    Optional<ClientPluginArtifact> findByCapabilityId(UUID capabilityId);
}
