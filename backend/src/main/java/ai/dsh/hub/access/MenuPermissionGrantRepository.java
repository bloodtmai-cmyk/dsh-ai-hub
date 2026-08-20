package ai.dsh.hub.access;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuPermissionGrantRepository extends JpaRepository<MenuPermissionGrant, UUID> {
    Optional<MenuPermissionGrant> findBySubjectTypeAndSubjectRefAndMenuKey(
            MenuPermissionGrant.SubjectType subjectType, String subjectRef, MenuPermissionGrant.MenuKey menuKey);

    List<MenuPermissionGrant> findBySubjectTypeAndSubjectRefAndEnabledTrue(
            MenuPermissionGrant.SubjectType subjectType, String subjectRef);

    List<MenuPermissionGrant> findBySubjectTypeAndSubjectRefInAndEnabledTrue(
            MenuPermissionGrant.SubjectType subjectType, Collection<String> subjectRefs);

    @Query("""
            select g from MenuPermissionGrant g
            where (:subjectType is null or g.subjectType = :subjectType)
              and (:subjectRef = '' or lower(g.subjectRef) like lower(concat('%', :subjectRef, '%')))
              and (:menuKey is null or g.menuKey = :menuKey)
              and (:includeRevoked = true or g.enabled = true)
            """)
    Page<MenuPermissionGrant> search(@Param("subjectType") MenuPermissionGrant.SubjectType subjectType,
                                     @Param("subjectRef") String subjectRef,
                                     @Param("menuKey") MenuPermissionGrant.MenuKey menuKey,
                                     @Param("includeRevoked") boolean includeRevoked,
                                     Pageable pageable);
}
