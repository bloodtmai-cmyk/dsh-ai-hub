package ai.dsh.hub.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectToolExclusionRepository extends JpaRepository<SubjectToolExclusion, UUID> {
    Optional<SubjectToolExclusion> findByWorkcodeAndToolCapabilityId(String workcode, UUID toolCapabilityId);
    boolean existsByWorkcodeAndToolCapabilityIdAndEnabledTrue(String workcode, UUID toolCapabilityId);
    List<SubjectToolExclusion> findByWorkcodeAndMcpCapabilityIdOrderByCreatedAtAsc(String workcode, UUID mcpCapabilityId);
    List<SubjectToolExclusion> findByWorkcodeAndMcpCapabilityIdInAndEnabledTrue(
            String workcode, Collection<UUID> mcpCapabilityIds);
}
