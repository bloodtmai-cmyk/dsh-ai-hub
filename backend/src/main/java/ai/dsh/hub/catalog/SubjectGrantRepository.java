package ai.dsh.hub.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectGrantRepository extends JpaRepository<SubjectGrant, UUID> {
    Optional<SubjectGrant> findByWorkcodeAndCapabilityId(String workcode, UUID capabilityId);
    List<SubjectGrant> findByWorkcodeOrderByCreatedAtDesc(String workcode);
    List<SubjectGrant> findByWorkcodeAndEnabledTrue(String workcode);
    List<SubjectGrant> findByCapabilityIdAndEnabledTrue(UUID capabilityId);

    @Query(value = """
            select distinct g.workcode from SubjectGrant g
            where g.enabled = true
              and g.validFrom <= :now and (g.validUntil is null or g.validUntil > :now)
              and (:keyword = '' or g.workcode like concat('%', :keyword, '%'))
            order by g.workcode
            """, countQuery = """
            select count(distinct g.workcode) from SubjectGrant g
            where g.enabled = true
              and g.validFrom <= :now and (g.validUntil is null or g.validUntil > :now)
              and (:keyword = '' or g.workcode like concat('%', :keyword, '%'))
            """)
    Page<String> findEffectiveWorkcodes(@Param("keyword") String keyword, @Param("now") Instant now,
                                        Pageable pageable);

    @Query("""
            select g from SubjectGrant g
            where g.workcode in :workcodes and g.enabled = true
              and g.validFrom <= :now and (g.validUntil is null or g.validUntil > :now)
            """)
    List<SubjectGrant> findEffectiveByWorkcodeIn(@Param("workcodes") Collection<String> workcodes,
                                                 @Param("now") Instant now);

    @Query("""
            select g from SubjectGrant g
            where g.workcode = :workcode and g.enabled = true
              and g.validFrom <= :now and (g.validUntil is null or g.validUntil > :now)
            """)
    List<SubjectGrant> findEffective(@Param("workcode") String workcode, @Param("now") Instant now);

    @Query("""
            select count(g) > 0 from SubjectGrant g
            where g.workcode = :workcode and g.capabilityId = :capabilityId and g.enabled = true
              and g.validFrom <= :now and (g.validUntil is null or g.validUntil > :now)
            """)
    boolean isEffective(@Param("workcode") String workcode, @Param("capabilityId") UUID capabilityId,
                        @Param("now") Instant now);
}
