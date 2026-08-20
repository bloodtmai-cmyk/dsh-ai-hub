package ai.dsh.hub.access;

import ai.dsh.hub.admin.AdminAuditService;
import ai.dsh.hub.common.ApiException;
import ai.dsh.hub.common.IdentitySupport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class MenuPermissionService {
    private final MenuPermissionGrantRepository repository;
    private final AdminAuditService auditService;

    public MenuPermissionService(MenuPermissionGrantRepository repository, AdminAuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<MenuPermissionGrant> list(MenuPermissionGrant.SubjectType subjectType, String subjectRef,
                                          MenuPermissionGrant.MenuKey menuKey, boolean includeRevoked,
                                          Pageable pageable) {
        return repository.search(subjectType, normalizeFilter(subjectRef), menuKey, includeRevoked, pageable);
    }

    @Transactional
    public MenuPermissionGrant grant(MenuPermissionGrant.SubjectType subjectType, String subjectRef,
                                     MenuPermissionGrant.MenuKey menuKey, String actor) {
        String normalizedRef = validateSubject(subjectType, subjectRef);
        MenuPermissionGrant grant = repository.findBySubjectTypeAndSubjectRefAndMenuKey(
                        subjectType, normalizedRef, menuKey)
                .map(existing -> {
                    existing.replace(subjectType, normalizedRef, menuKey, actor);
                    return existing;
                })
                .orElseGet(() -> new MenuPermissionGrant(subjectType, normalizedRef, menuKey, actor));
        repository.save(grant);
        auditService.record(actor, "GRANT_MENU_PERMISSION", "MENU_PERMISSION", grant.getId().toString(),
                "SUCCESS", subjectType + ":" + normalizedRef + ":" + menuKey);
        return grant;
    }

    @Transactional
    public MenuPermissionGrant replace(UUID id, MenuPermissionGrant.SubjectType subjectType, String subjectRef,
                                       MenuPermissionGrant.MenuKey menuKey, String actor) {
        MenuPermissionGrant grant = get(id);
        String normalizedRef = validateSubject(subjectType, subjectRef);
        repository.findBySubjectTypeAndSubjectRefAndMenuKey(subjectType, normalizedRef, menuKey)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, "MENU_PERMISSION_EXISTS", "相同菜单权限已存在");
                });
        grant.replace(subjectType, normalizedRef, menuKey, actor);
        auditService.record(actor, "UPDATE_MENU_PERMISSION", "MENU_PERMISSION", id.toString(), "SUCCESS",
                subjectType + ":" + normalizedRef + ":" + menuKey);
        return grant;
    }

    @Transactional
    public void revoke(UUID id, String actor) {
        MenuPermissionGrant grant = get(id);
        grant.revoke();
        auditService.record(actor, "REVOKE_MENU_PERMISSION", "MENU_PERMISSION", id.toString(), "SUCCESS",
                grant.getSubjectType() + ":" + grant.getSubjectRef() + ":" + grant.getMenuKey());
    }

    @Transactional(readOnly = true)
    public Entitlements entitlements(Authentication authentication) {
        String workcode = IdentitySupport.requireWorkcode(authentication);
        Set<MenuPermissionGrant.MenuKey> allowed = new HashSet<>();
        repository.findBySubjectTypeAndSubjectRefAndEnabledTrue(MenuPermissionGrant.SubjectType.USER, workcode)
                .stream().map(MenuPermissionGrant::getMenuKey).forEach(allowed::add);
        Set<String> departments = IdentitySupport.requireDepartmentCodes(authentication);
        if (!departments.isEmpty()) {
            repository.findBySubjectTypeAndSubjectRefInAndEnabledTrue(
                            MenuPermissionGrant.SubjectType.DEPARTMENT, departments)
                    .stream().map(MenuPermissionGrant::getMenuKey).forEach(allowed::add);
        }
        return new Entitlements(Set.copyOf(allowed));
    }

    @Transactional(readOnly = true)
    public void require(Authentication authentication, MenuPermissionGrant.MenuKey menuKey) {
        if (!entitlements(authentication).menus().contains(menuKey)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MENU_NOT_GRANTED", "当前用户未获该菜单访问权限");
        }
    }

    private MenuPermissionGrant get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MENU_PERMISSION_NOT_FOUND", "菜单权限不存在"));
    }

    private static String validateSubject(MenuPermissionGrant.SubjectType subjectType, String subjectRef) {
        if (subjectType == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MENU_SUBJECT_TYPE_REQUIRED", "请选择授权对象类型");
        }
        String normalized = subjectRef == null ? "" : subjectRef.trim();
        if (subjectType == MenuPermissionGrant.SubjectType.USER) {
            return IdentitySupport.validateWorkcode(normalized);
        }
        if (normalized.isBlank() || normalized.length() > 120
                || !normalized.matches("[A-Za-z0-9._:/-]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEPARTMENT_CODE", "部门编码格式不正确");
        }
        return normalized;
    }

    private static String normalizeFilter(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MENU_SUBJECT_FILTER", "筛选条件过长");
        }
        return normalized;
    }

    public record Entitlements(Set<MenuPermissionGrant.MenuKey> menus) {
    }
}
