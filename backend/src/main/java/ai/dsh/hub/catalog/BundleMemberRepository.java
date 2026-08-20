package ai.dsh.hub.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BundleMemberRepository extends JpaRepository<BundleMember, UUID> {
    List<BundleMember> findByBundleIdOrderBySortOrderAsc(UUID bundleId);
    List<BundleMember> findByBundleIdIn(Collection<UUID> bundleIds);
    List<BundleMember> findByMemberId(UUID memberId);
    boolean existsByBundleIdAndMemberId(UUID bundleId, UUID memberId);
    void deleteByBundleId(UUID bundleId);
}
