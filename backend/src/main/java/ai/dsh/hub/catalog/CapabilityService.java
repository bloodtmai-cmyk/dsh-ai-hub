package ai.dsh.hub.catalog;

import ai.dsh.hub.admin.AdminAuditService;
import ai.dsh.hub.common.ApiException;
import ai.dsh.hub.common.IdentitySupport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CapabilityService {
    private final CapabilityRepository capabilityRepository;
    private final SubjectGrantRepository grantRepository;
    private final SubjectToolExclusionRepository toolExclusionRepository;
    private final BundleMemberRepository bundleMemberRepository;
    private final AdminAuditService auditService;

    public CapabilityService(CapabilityRepository capabilityRepository, SubjectGrantRepository grantRepository,
                             SubjectToolExclusionRepository toolExclusionRepository,
                             BundleMemberRepository bundleMemberRepository, AdminAuditService auditService) {
        this.capabilityRepository = capabilityRepository;
        this.grantRepository = grantRepository;
        this.toolExclusionRepository = toolExclusionRepository;
        this.bundleMemberRepository = bundleMemberRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Capability create(CreateCommand command, String actor) {
        if (command.type() == Capability.Type.TOOL && command.parentId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOOL_PARENT_REQUIRED", "Tool 必须关联所属 MCP");
        }
        if (command.type() == Capability.Type.TOOL) {
            Capability parent = get(command.parentId());
            if (parent.getType() != Capability.Type.MCP) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOOL_PARENT", "Tool 只能关联 MCP");
            }
        }
        if ((command.type() == Capability.Type.SKILL || command.type() == Capability.Type.INSTRUCTION)
                && (command.integrityHash() == null || command.integrityHash().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ARTIFACT_HASH_REQUIRED", "上传制品必须提供完整性哈希");
        }
        if (command.type() == Capability.Type.BUNDLE && command.parentId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BUNDLE_PARENT", "Bundle 不能设置所属能力");
        }
        Capability entity = new Capability(command.type(), command.parentId(), command.sourceKind(),
                command.externalRef(), command.name(), command.description(), command.releaseVersion(),
                command.sourceRef(), command.integrityHash());
        capabilityRepository.save(entity);
        auditService.record(actor, "DISCOVER_CAPABILITY", "CAPABILITY", entity.getId().toString(), "SUCCESS",
                entity.getType() + ":" + entity.getExternalRef());
        return entity;
    }

    @Transactional
    public SyncResult syncGateway(List<GatewayCapability> discovered) {
        List<GatewayCapability> ordered = discovered.stream()
                .sorted(Comparator.comparing(item -> item.type() == Capability.Type.MCP ? 0 : 1))
                .toList();
        Map<String, UUID> mcpIds = new LinkedHashMap<>();
        int created = 0;
        int updated = 0;
        for (GatewayCapability item : ordered) {
            if (item.type() != Capability.Type.MCP && item.type() != Capability.Type.TOOL) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GATEWAY_CAPABILITY", "Gateway 只能同步 MCP 和 Tool");
            }
            UUID parentId = null;
            if (item.type() == Capability.Type.TOOL) {
                parentId = mcpIds.get(item.parentExternalRef());
                if (parentId == null) {
                    parentId = capabilityRepository.findByTypeAndExternalRefAndStatus(
                                    Capability.Type.MCP, item.parentExternalRef(), Capability.Status.PUBLISHED)
                            .stream().findFirst().map(Capability::getId)
                            .orElseGet(() -> capabilityRepository.findByTypeAndExternalRefAndReleaseVersion(
                                            Capability.Type.MCP, item.parentExternalRef(), item.releaseVersion())
                                    .map(Capability::getId).orElse(null));
                }
                if (parentId == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "MCP_PARENT_NOT_FOUND",
                            "Tool 的所属 MCP 未在同批次或 Hub 中发现: " + item.parentExternalRef());
                }
            }
            var existing = capabilityRepository.findByTypeAndExternalRefAndReleaseVersion(
                    item.type(), item.externalRef(), item.releaseVersion());
            Capability entity;
            if (existing.isPresent()) {
                entity = existing.get();
                entity.refreshDiscovery(parentId, item.name(), item.description(), item.sourceRef(), null);
                updated++;
            } else {
                entity = new Capability(item.type(), parentId, Capability.SourceKind.GATEWAY, item.externalRef(),
                        item.name(), item.description(), item.releaseVersion(), item.sourceRef(), null);
                entity = capabilityRepository.save(entity);
                created++;
            }
            if (item.type() == Capability.Type.MCP) {
                mcpIds.put(item.externalRef(), entity.getId());
            } else {
                Capability parent = get(parentId);
                if (parent.getStatus() == Capability.Status.PUBLISHED) {
                    entity.publishWithParent();
                }
            }
        }
        return new SyncResult(created, updated, Instant.now());
    }

    @Transactional
    public Capability approve(UUID id, String actor) {
        Capability entity = get(id);
        if (entity.getType() == Capability.Type.TOOL) {
            throw new ApiException(HttpStatus.CONFLICT, "TOOL_LIFECYCLE_MANAGED_BY_MCP",
                    "Tool 随所属 MCP 审批，无需单独操作");
        }
        entity.approve();
        if (entity.getType() == Capability.Type.MCP) {
            capabilityRepository.findByParentIdOrderByNameAsc(id).stream()
                    .filter(tool -> tool.getStatus() == Capability.Status.DISCOVERED)
                    .forEach(Capability::approve);
        }
        auditService.record(actor, "APPROVE_CAPABILITY", "CAPABILITY", id.toString(), "SUCCESS", null);
        return entity;
    }

    @Transactional
    public Capability publish(UUID id, String actor) {
        Capability entity = get(id);
        if (entity.getType() == Capability.Type.TOOL) {
            throw new ApiException(HttpStatus.CONFLICT, "TOOL_LIFECYCLE_MANAGED_BY_MCP",
                    "Tool 随所属 MCP 发布，无需单独操作");
        }
        if (entity.getType() == Capability.Type.INSTRUCTION) {
            validateInstructionReplacement(entity);
        }
        if (entity.getType() == Capability.Type.BUNDLE) {
            List<BundleMember> members = bundleMemberRepository.findByBundleIdOrderBySortOrderAsc(id);
            if (members.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "EMPTY_BUNDLE", "Bundle 至少包含一个能力后才能发布");
            }
            Map<UUID, Capability> memberMap = capabilityRepository.findByIdIn(
                    members.stream().map(BundleMember::getMemberId).toList()).stream()
                    .collect(Collectors.toMap(Capability::getId, Function.identity()));
            boolean unavailable = members.stream().filter(BundleMember::isRequired)
                    .map(member -> memberMap.get(member.getMemberId()))
                    .anyMatch(member -> member == null || member.getStatus() != Capability.Status.PUBLISHED);
            if (unavailable) {
                throw new ApiException(HttpStatus.CONFLICT, "BUNDLE_MEMBER_NOT_PUBLISHED", "Bundle 的必选能力必须全部发布");
            }
        }
        List<Capability> replaced = capabilityRepository.findByTypeAndExternalRefAndStatus(
                        entity.getType(), entity.getExternalRef(), Capability.Status.PUBLISHED).stream()
                .filter(other -> !other.getId().equals(id))
                .toList();
        if (entity.getType() == Capability.Type.CLIENT_PLUGIN) {
            validateClientPluginReplacement(entity, replaced);
        }
        replaced.forEach(this::disableCapabilityAndChildren);
        entity.publish();
        if (entity.getType() == Capability.Type.CLIENT_PLUGIN) {
            migrateClientPluginReplacement(entity, replaced, actor);
        }
        if (entity.getType() == Capability.Type.MCP) {
            capabilityRepository.findByParentIdOrderByNameAsc(id).forEach(Capability::publishWithParent);
        }
        auditService.record(actor, "PUBLISH_CAPABILITY", "CAPABILITY", id.toString(), "SUCCESS", null);
        return entity;
    }

    @Transactional
    public Capability disable(UUID id, String actor) {
        Capability entity = get(id);
        if (entity.getType() == Capability.Type.TOOL) {
            throw new ApiException(HttpStatus.CONFLICT, "TOOL_LIFECYCLE_MANAGED_BY_MCP",
                    "Tool 随所属 MCP 停用，不能单独操作");
        }
        if (entity.getType() == Capability.Type.INSTRUCTION) {
            throw new ApiException(HttpStatus.CONFLICT, "INSTRUCTION_REPLACEMENT_REQUIRED",
                    "已生效企业指令不能停用，请发布更高版本覆盖");
        }
        disableCapabilityAndChildren(entity);
        auditService.record(actor, "DISABLE_CAPABILITY", "CAPABILITY", id.toString(), "SUCCESS", null);
        return entity;
    }

    @Transactional
    public SubjectGrant grant(String workcode, UUID capabilityId, Instant validFrom, Instant validUntil, String actor) {
        IdentitySupport.validateWorkcode(workcode);
        Capability capability = get(capabilityId);
        if (capability.getType() == Capability.Type.TOOL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MCP_GRANT_REQUIRED",
                    "Tool 必须随所属 MCP 授权，可在 MCP 详情中单独排除");
        }
        if (capability.getType() == Capability.Type.INSTRUCTION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GLOBAL_INSTRUCTION_NOT_GRANTABLE",
                    "企业指令全员生效，不需要用户授权");
        }
        if (capability.getStatus() != Capability.Status.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "CAPABILITY_NOT_PUBLISHED", "只能授权已发布能力");
        }
        Instant effectiveFrom = validFrom == null ? Instant.now() : validFrom;
        if (validUntil != null && !validUntil.isAfter(effectiveFrom)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GRANT_WINDOW", "授权失效时间必须晚于生效时间");
        }
        SubjectGrant grant = grantRepository.findByWorkcodeAndCapabilityId(workcode, capabilityId)
                .map(existing -> {
                    existing.replaceWindow(effectiveFrom, validUntil, actor);
                    return existing;
                })
                .orElseGet(() -> new SubjectGrant(workcode, capabilityId, effectiveFrom, validUntil, actor));
        grantRepository.save(grant);
        auditService.record(actor, "GRANT_CAPABILITY", "SUBJECT_GRANT", grant.getId().toString(), "SUCCESS",
                "workcode=" + workcode + ", capability=" + capabilityId);
        return grant;
    }

    @Transactional
    public void revokeGrant(UUID grantId, String actor) {
        SubjectGrant grant = grantRepository.findById(grantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GRANT_NOT_FOUND", "授权不存在"));
        grant.revoke();
        auditService.record(actor, "REVOKE_CAPABILITY", "SUBJECT_GRANT", grantId.toString(), "SUCCESS",
                "workcode=" + grant.getWorkcode());
    }

    @Transactional(readOnly = true)
    public AuthorizationDecision check(String workcode, Capability.Type type, String externalRef) {
        IdentitySupport.validateWorkcode(workcode);
        List<Capability> candidates = capabilityRepository.findByTypeAndExternalRefAndStatus(
                type, externalRef, Capability.Status.PUBLISHED);
        if (candidates.isEmpty()) {
            return AuthorizationDecision.denied("NOT_PUBLISHED");
        }
        Capability capability = candidates.getFirst();
        Instant now = Instant.now();
        EffectiveAccess access = effectiveAccess(workcode, now);
        if (!access.capabilityIds().contains(capability.getId())) {
            if (type == Capability.Type.TOOL && capability.getParentId() != null
                    && access.capabilityIds().contains(capability.getParentId())
                    && toolExclusionRepository.existsByWorkcodeAndToolCapabilityIdAndEnabledTrue(
                    workcode, capability.getId())) {
                return AuthorizationDecision.denied("TOOL_EXCLUDED");
            }
            return AuthorizationDecision.denied("SUBJECT_NOT_GRANTED");
        }
        if (type == Capability.Type.TOOL) {
            Capability parent = capabilityRepository.findById(capability.getParentId()).orElse(null);
            if (parent == null || parent.getStatus() != Capability.Status.PUBLISHED) {
                return AuthorizationDecision.denied("PARENT_MCP_NOT_PUBLISHED");
            }
            if (!access.capabilityIds().contains(parent.getId())) {
                return AuthorizationDecision.denied("PARENT_MCP_NOT_GRANTED");
            }
        }
        return AuthorizationDecision.allowed(capability.getId(), capability.getReleaseVersion());
    }

    @Transactional(readOnly = true)
    public Catalog catalog(String workcode) {
        IdentitySupport.validateWorkcode(workcode);
        Instant now = Instant.now();
        EffectiveAccess access = effectiveAccess(workcode, now);
        Map<UUID, Capability> capabilities = capabilityRepository.findByIdIn(access.capabilityIds()).stream()
                .filter(item -> item.getStatus() == Capability.Status.PUBLISHED)
                .collect(Collectors.toMap(Capability::getId, Function.identity()));

        List<McpCatalogItem> mcps = capabilities.values().stream()
                .filter(item -> item.getType() == Capability.Type.MCP)
                .sorted(Comparator.comparing(Capability::getName))
                .map(mcp -> new McpCatalogItem(CapabilityView.from(mcp), capabilities.values().stream()
                        .filter(tool -> tool.getType() == Capability.Type.TOOL && mcp.getId().equals(tool.getParentId()))
                        .sorted(Comparator.comparing(Capability::getName))
                        .map(CapabilityView::from).toList()))
                .toList();
        List<CapabilityView> skills = capabilities.values().stream()
                .filter(item -> item.getType() == Capability.Type.SKILL)
                .sorted(Comparator.comparing(Capability::getName))
                .map(CapabilityView::from)
                .toList();
        List<CapabilityView> plugins = capabilities.values().stream()
                .filter(item -> item.getType() == Capability.Type.CLIENT_PLUGIN)
                .sorted(Comparator.comparing(Capability::getName))
                .map(CapabilityView::from)
                .toList();
        List<CapabilityView> instructions = capabilityRepository.findByStatusOrderByNameAsc(
                        Capability.Status.PUBLISHED).stream()
                .filter(item -> item.getType() == Capability.Type.INSTRUCTION)
                .sorted(Comparator.comparing(Capability::getName))
                .map(CapabilityView::from)
                .toList();
        List<BundleCatalogItem> bundles = access.grantedBundleIds().stream()
                .map(capabilities::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(Capability::getName))
                .map(bundle -> new BundleCatalogItem(CapabilityView.from(bundle),
                        bundleMemberRepository.findByBundleIdOrderBySortOrderAsc(bundle.getId()).stream()
                                .map(member -> capabilities.get(member.getMemberId()))
                                .filter(java.util.Objects::nonNull)
                                .map(CapabilityView::from).toList()))
                .toList();
        return new Catalog(mcps, skills, bundles, plugins, instructions, now);
    }

    @Transactional(readOnly = true)
    public List<MarketCapability> market(String workcode) {
        IdentitySupport.validateWorkcode(workcode);
        Set<UUID> authorized = marketAuthorizedIds(workcode, Instant.now());
        return capabilityRepository.findByStatusOrderByNameAsc(Capability.Status.PUBLISHED).stream()
                .filter(item -> item.getType() != Capability.Type.TOOL
                        && item.getType() != Capability.Type.INSTRUCTION)
                .map(item -> new MarketCapability(CapabilityView.from(item), authorized.contains(item.getId())))
                .toList();
    }

    private Set<UUID> marketAuthorizedIds(String workcode, Instant now) {
        Set<UUID> directIds = grantRepository.findEffective(workcode, now).stream()
                .map(SubjectGrant::getCapabilityId).collect(Collectors.toSet());
        Map<UUID, Capability> direct = capabilityRepository.findByIdIn(directIds).stream()
                .filter(item -> item.getStatus() == Capability.Status.PUBLISHED)
                .collect(Collectors.toMap(Capability::getId, Function.identity()));
        Set<UUID> authorized = new java.util.HashSet<>(direct.keySet());
        Set<UUID> bundleIds = direct.values().stream()
                .filter(item -> item.getType() == Capability.Type.BUNDLE)
                .map(Capability::getId).collect(Collectors.toSet());
        if (!bundleIds.isEmpty()) {
            Set<UUID> memberIds = bundleMemberRepository.findByBundleIdIn(bundleIds).stream()
                    .map(BundleMember::getMemberId).collect(Collectors.toSet());
            capabilityRepository.findByIdIn(memberIds).stream()
                    .filter(item -> item.getStatus() == Capability.Status.PUBLISHED)
                    .map(Capability::getId).forEach(authorized::add);
        }
        return Set.copyOf(authorized);
    }

    @Transactional
    public BundleDetail createBundle(CreateBundleCommand command, String actor) {
        List<UUID> memberIds = command.memberIds() == null ? List.of() : command.memberIds().stream().distinct().toList();
        validateBundleMembers(memberIds);
        Capability bundle = create(new CreateCommand(Capability.Type.BUNDLE, null, Capability.SourceKind.MANUAL,
                command.externalRef(), command.name(), command.description(), command.releaseVersion(), null, null), actor);
        saveBundleMembers(bundle.getId(), memberIds);
        auditService.record(actor, "CONFIGURE_BUNDLE", "CAPABILITY", bundle.getId().toString(), "SUCCESS",
                "members=" + memberIds.size());
        return bundleDetail(bundle);
    }

    @Transactional
    public BundleDetail replaceBundleMembers(UUID bundleId, List<UUID> memberIds, String actor) {
        Capability bundle = get(bundleId);
        if (bundle.getType() != Capability.Type.BUNDLE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_BUNDLE", "目标能力不是 Bundle");
        }
        if (bundle.getStatus() == Capability.Status.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "PUBLISHED_BUNDLE_IMMUTABLE", "已发布 Bundle 的成员不可修改，请创建新版本");
        }
        List<UUID> normalized = memberIds == null ? List.of() : memberIds.stream().distinct().toList();
        validateBundleMembers(normalized);
        bundleMemberRepository.deleteByBundleId(bundleId);
        saveBundleMembers(bundleId, normalized);
        auditService.record(actor, "CONFIGURE_BUNDLE", "CAPABILITY", bundleId.toString(), "SUCCESS",
                "members=" + normalized.size());
        return bundleDetail(bundle);
    }

    @Transactional(readOnly = true)
    public BundleDetail bundle(UUID bundleId) {
        return bundleDetail(get(bundleId));
    }

    @Transactional(readOnly = true)
    public Capability requireAuthorized(UUID capabilityId, String workcode, Capability.Type expectedType) {
        IdentitySupport.validateWorkcode(workcode);
        Capability capability = get(capabilityId);
        if (capability.getType() != expectedType || capability.getStatus() != Capability.Status.PUBLISHED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CAPABILITY_NOT_AVAILABLE", "能力不存在或未发布");
        }
        EffectiveAccess access = effectiveAccess(workcode, Instant.now());
        if (!access.capabilityIds().contains(capabilityId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CAPABILITY_NOT_GRANTED", "当前用户未获授权");
        }
        return capability;
    }

    @Transactional(readOnly = true)
    public Capability requirePublishedInstruction(UUID capabilityId) {
        Capability capability = get(capabilityId);
        if (capability.getType() != Capability.Type.INSTRUCTION
                || capability.getStatus() != Capability.Status.PUBLISHED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "INSTRUCTION_NOT_ACTIVE", "企业指令不存在或已被新版本替换");
        }
        return capability;
    }

    @Transactional
    public SubjectToolExclusion excludeTool(String workcode, UUID mcpId, UUID toolId, String actor) {
        IdentitySupport.validateWorkcode(workcode);
        Capability mcp = requirePublishedMcp(mcpId);
        Capability tool = requireToolOfMcp(toolId, mcpId);
        if (!effectiveAccess(workcode, Instant.now()).capabilityIds().contains(mcp.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "MCP_NOT_GRANTED", "用户尚未获得该 MCP 服务授权");
        }
        SubjectToolExclusion exclusion = toolExclusionRepository.findByWorkcodeAndToolCapabilityId(workcode, toolId)
                .map(existing -> {
                    existing.restore(actor);
                    return existing;
                })
                .orElseGet(() -> new SubjectToolExclusion(workcode, mcpId, toolId, actor));
        toolExclusionRepository.save(exclusion);
        auditService.record(actor, "EXCLUDE_MCP_TOOL", "SUBJECT_TOOL_EXCLUSION", exclusion.getId().toString(),
                "SUCCESS", "workcode=" + workcode + ", mcp=" + mcpId + ", tool=" + tool.getExternalRef());
        return exclusion;
    }

    @Transactional
    public void allowTool(String workcode, UUID mcpId, UUID toolId, String actor) {
        IdentitySupport.validateWorkcode(workcode);
        requirePublishedMcp(mcpId);
        Capability tool = requireToolOfMcp(toolId, mcpId);
        SubjectToolExclusion exclusion = toolExclusionRepository.findByWorkcodeAndToolCapabilityId(workcode, toolId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOOL_EXCLUSION_NOT_FOUND",
                        "该 Tool 当前未被排除"));
        exclusion.revoke();
        auditService.record(actor, "ALLOW_MCP_TOOL", "SUBJECT_TOOL_EXCLUSION", exclusion.getId().toString(),
                "SUCCESS", "workcode=" + workcode + ", mcp=" + mcpId + ", tool=" + tool.getExternalRef());
    }

    @Transactional(readOnly = true)
    public McpToolGrantDetail mcpToolGrantDetail(String workcode, UUID mcpId) {
        IdentitySupport.validateWorkcode(workcode);
        Capability mcp = requirePublishedMcp(mcpId);
        EffectiveAccess access = effectiveAccess(workcode, Instant.now());
        if (!access.capabilityIds().contains(mcpId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MCP_NOT_GRANTED", "用户尚未获得该 MCP 服务授权");
        }
        Set<UUID> excluded = toolExclusionRepository
                .findByWorkcodeAndMcpCapabilityIdOrderByCreatedAtAsc(workcode, mcpId).stream()
                .filter(SubjectToolExclusion::isEnabled)
                .map(SubjectToolExclusion::getToolCapabilityId)
                .collect(Collectors.toSet());
        List<ToolGrantState> tools = capabilityRepository.findByParentIdAndStatus(
                        mcpId, Capability.Status.PUBLISHED).stream()
                .sorted(Comparator.comparing(Capability::getName, String.CASE_INSENSITIVE_ORDER))
                .map(tool -> new ToolGrantState(CapabilityView.from(tool), excluded.contains(tool.getId())))
                .toList();
        return new McpToolGrantDetail(CapabilityView.from(mcp), tools);
    }

    @Transactional(readOnly = true)
    public Page<Capability> list(Capability.Type type, Capability.Status status, Capability.Status excludeStatus,
                                 boolean directoryOnly, Pageable pageable) {
        if (status != null && excludeStatus != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONFLICTING_STATUS_FILTERS",
                    "状态包含与排除条件不能同时使用");
        }
        if (type != null && status != null) return capabilityRepository.findByTypeAndStatus(type, status, pageable);
        if (type != null && excludeStatus != null) {
            return capabilityRepository.findByTypeAndStatusNot(type, excludeStatus, pageable);
        }
        if (type != null) return capabilityRepository.findByType(type, pageable);
        if (directoryOnly && status != null) {
            return capabilityRepository.findByTypeNotInAndStatus(
                    List.of(Capability.Type.TOOL, Capability.Type.INSTRUCTION), status, pageable);
        }
        if (directoryOnly && excludeStatus != null) {
            return capabilityRepository.findByTypeNotInAndStatusNot(
                    List.of(Capability.Type.TOOL, Capability.Type.INSTRUCTION), excludeStatus, pageable);
        }
        if (directoryOnly) {
            return capabilityRepository.findByTypeNotIn(
                    List.of(Capability.Type.TOOL, Capability.Type.INSTRUCTION), pageable);
        }
        if (status != null) return capabilityRepository.findByStatus(status, pageable);
        if (excludeStatus != null) return capabilityRepository.findByStatusNot(excludeStatus, pageable);
        return capabilityRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<SubjectGrant> grants(String workcode) {
        IdentitySupport.validateWorkcode(workcode);
        return grantRepository.findByWorkcodeOrderByCreatedAtDesc(workcode);
    }

    @Transactional(readOnly = true)
    public List<Capability> mcpTools(UUID mcpId) {
        Capability mcp = get(mcpId);
        if (mcp.getType() != Capability.Type.MCP) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_AN_MCP", "目标能力不是 MCP 服务");
        }
        return capabilityRepository.findByParentIdOrderByNameAsc(mcpId);
    }

    @Transactional(readOnly = true)
    public Page<GrantSubjectSummary> grantSubjects(String keyword, Pageable pageable) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (!normalizedKeyword.matches("[0-9]{0,12}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WORKCODE_FILTER", "工号筛选只能包含数字");
        }
        Instant now = Instant.now();
        Page<String> workcodes = grantRepository.findEffectiveWorkcodes(normalizedKeyword, now, pageable);
        if (workcodes.isEmpty()) {
            return workcodes.map(workcode -> new GrantSubjectSummary(workcode, List.of(), 0, null));
        }

        List<SubjectGrant> effectiveGrants = grantRepository.findEffectiveByWorkcodeIn(workcodes.getContent(), now);
        Set<UUID> capabilityIds = effectiveGrants.stream()
                .map(SubjectGrant::getCapabilityId)
                .collect(Collectors.toSet());
        Map<UUID, Capability> capabilities = capabilityRepository.findByIdIn(capabilityIds).stream()
                .collect(Collectors.toMap(Capability::getId, Function.identity()));
        Map<String, List<SubjectGrant>> byWorkcode = effectiveGrants.stream()
                .collect(Collectors.groupingBy(SubjectGrant::getWorkcode));

        return workcodes.map(workcode -> {
            List<GrantCapabilitySummary> grantedCapabilities = byWorkcode.getOrDefault(workcode, List.of()).stream()
                    .map(grant -> toGrantCapabilitySummary(grant, capabilities.get(grant.getCapabilityId())))
                    .filter(item -> item != null)
                    .sorted(Comparator.comparing(GrantCapabilitySummary::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            Instant nearestExpiry = grantedCapabilities.stream()
                    .map(GrantCapabilitySummary::validUntil)
                    .filter(value -> value != null)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            return new GrantSubjectSummary(workcode, grantedCapabilities, grantedCapabilities.size(), nearestExpiry);
        });
    }

    @Transactional
    public void revokeSubjectGrants(String workcode, String actor) {
        IdentitySupport.validateWorkcode(workcode);
        List<SubjectGrant> grants = grantRepository.findByWorkcodeAndEnabledTrue(workcode);
        if (grants.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GRANT_SUBJECT_NOT_FOUND", "该用户暂无可撤销授权");
        }
        grants.forEach(SubjectGrant::revoke);
        auditService.record(actor, "REVOKE_SUBJECT_GRANTS", "GRANT_SUBJECT", workcode, "SUCCESS",
                "revoked=" + grants.size());
    }

    private static GrantCapabilitySummary toGrantCapabilitySummary(SubjectGrant grant, Capability capability) {
        if (capability == null) return null;
        return new GrantCapabilitySummary(capability.getId(), capability.getType(), capability.getName(),
                capability.getReleaseVersion(), grant.getValidUntil());
    }

    Capability getCapability(UUID id) {
        return get(id);
    }

    private Capability get(UUID id) {
        return capabilityRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CAPABILITY_NOT_FOUND", "能力不存在"));
    }

    private EffectiveAccess effectiveAccess(String workcode, Instant now) {
        Set<UUID> directIds = grantRepository.findEffective(workcode, now).stream()
                .map(SubjectGrant::getCapabilityId).collect(Collectors.toSet());
        Map<UUID, Capability> direct = capabilityRepository.findByIdIn(directIds).stream()
                .filter(item -> item.getStatus() == Capability.Status.PUBLISHED
                        && item.getType() != Capability.Type.INSTRUCTION)
                .collect(Collectors.toMap(Capability::getId, Function.identity()));
        Set<UUID> bundleIds = direct.values().stream().filter(item -> item.getType() == Capability.Type.BUNDLE)
                .map(Capability::getId).collect(Collectors.toSet());
        Set<UUID> effective = new java.util.HashSet<>(direct.keySet());
        if (!bundleIds.isEmpty()) {
            Set<UUID> memberIds = bundleMemberRepository.findByBundleIdIn(bundleIds).stream()
                    .map(BundleMember::getMemberId).collect(Collectors.toSet());
            capabilityRepository.findByIdIn(memberIds).stream()
                    .filter(item -> item.getStatus() == Capability.Status.PUBLISHED)
                    .map(Capability::getId).forEach(effective::add);
        }

        Map<UUID, Capability> effectiveCapabilities = capabilityRepository.findByIdIn(effective).stream()
                .collect(Collectors.toMap(Capability::getId, Function.identity()));
        effectiveCapabilities.values().stream()
                .filter(item -> item.getType() == Capability.Type.TOOL && item.getParentId() != null)
                .map(Capability::getParentId)
                .map(capabilityRepository::findById)
                .flatMap(java.util.Optional::stream)
                .filter(parent -> parent.getType() == Capability.Type.MCP
                        && parent.getStatus() == Capability.Status.PUBLISHED)
                .map(Capability::getId)
                .forEach(effective::add);

        Map<UUID, Capability> expandedCapabilities = capabilityRepository.findByIdIn(effective).stream()
                .collect(Collectors.toMap(Capability::getId, Function.identity()));
        Set<UUID> mcpIds = expandedCapabilities.values().stream()
                .filter(item -> item.getType() == Capability.Type.MCP)
                .map(Capability::getId)
                .collect(Collectors.toSet());
        Set<UUID> excludedToolIds = mcpIds.isEmpty() ? Set.of()
                : toolExclusionRepository.findByWorkcodeAndMcpCapabilityIdInAndEnabledTrue(workcode, mcpIds).stream()
                .map(SubjectToolExclusion::getToolCapabilityId)
                .collect(Collectors.toSet());

        effective.removeIf(id -> {
            Capability capability = expandedCapabilities.get(id);
            return capability != null && capability.getType() == Capability.Type.TOOL;
        });
        mcpIds.stream()
                .flatMap(mcpId -> capabilityRepository.findByParentIdAndStatus(
                        mcpId, Capability.Status.PUBLISHED).stream())
                .filter(tool -> !excludedToolIds.contains(tool.getId()))
                .map(Capability::getId)
                .forEach(effective::add);
        return new EffectiveAccess(Set.copyOf(effective), Set.copyOf(bundleIds));
    }

    private void validateBundleMembers(List<UUID> memberIds) {
        if (memberIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_BUNDLE", "Bundle 至少需要一个成员");
        }
        List<Capability> members = capabilityRepository.findByIdIn(memberIds);
        if (members.size() != memberIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BUNDLE_MEMBER_NOT_FOUND", "Bundle 包含不存在的能力");
        }
        if (members.stream().anyMatch(member -> member.getType() == Capability.Type.BUNDLE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NESTED_BUNDLE_NOT_ALLOWED", "Bundle 不能嵌套 Bundle");
        }
        if (members.stream().anyMatch(member -> member.getType() == Capability.Type.TOOL)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BUNDLE_MCP_REQUIRED",
                    "Bundle 只需包含 MCP 服务，Tool 会随 MCP 自动生效");
        }
        if (members.stream().anyMatch(member -> member.getType() == Capability.Type.INSTRUCTION)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GLOBAL_INSTRUCTION_NOT_BUNDLE_MEMBER",
                    "企业指令全员生效，不能加入 Bundle");
        }
    }

    private void validateInstructionReplacement(Capability instruction) {
        SemanticVersion candidate = SemanticVersion.parse(instruction.getReleaseVersion());
        capabilityRepository.findByTypeAndExternalRefAndStatus(Capability.Type.INSTRUCTION,
                        instruction.getExternalRef(), Capability.Status.PUBLISHED).stream()
                .filter(active -> !active.getId().equals(instruction.getId()))
                .filter(active -> candidate.compareTo(SemanticVersion.parse(active.getReleaseVersion())) <= 0)
                .findFirst()
                .ifPresent(active -> {
                    throw new ApiException(HttpStatus.CONFLICT, "INSTRUCTION_VERSION_NOT_HIGHER",
                            "新企业指令版本必须高于当前生效版本 " + active.getReleaseVersion());
                });
    }

    private void validateClientPluginReplacement(Capability candidate, List<Capability> replaced) {
        SemanticVersion version = SemanticVersion.parse(candidate.getReleaseVersion());
        replaced.stream()
                .filter(active -> version.compareTo(SemanticVersion.parse(active.getReleaseVersion())) <= 0)
                .findFirst()
                .ifPresent(active -> {
                    throw new ApiException(HttpStatus.CONFLICT, "CLIENT_PLUGIN_VERSION_NOT_HIGHER",
                            "客户端插件新版本必须高于当前版本 " + active.getReleaseVersion());
                });
    }

    private void migrateClientPluginReplacement(Capability candidate, List<Capability> replaced, String actor) {
        for (Capability previous : replaced) {
            for (SubjectGrant grant : grantRepository.findByCapabilityIdAndEnabledTrue(previous.getId())) {
                SubjectGrant migrated = grantRepository.findByWorkcodeAndCapabilityId(
                                grant.getWorkcode(), candidate.getId())
                        .map(existing -> {
                            existing.replaceWindow(grant.getValidFrom(), grant.getValidUntil(), actor);
                            return existing;
                        })
                        .orElseGet(() -> new SubjectGrant(grant.getWorkcode(), candidate.getId(),
                                grant.getValidFrom(), grant.getValidUntil(), actor));
                grantRepository.save(migrated);
                grant.revoke();
            }
            for (BundleMember member : bundleMemberRepository.findByMemberId(previous.getId())) {
                if (bundleMemberRepository.existsByBundleIdAndMemberId(member.getBundleId(), candidate.getId())) {
                    bundleMemberRepository.delete(member);
                } else {
                    member.replaceMember(candidate.getId());
                }
            }
        }
    }

    private void disableCapabilityAndChildren(Capability capability) {
        capability.disable();
        if (capability.getType() == Capability.Type.MCP) {
            capabilityRepository.findByParentIdOrderByNameAsc(capability.getId()).forEach(Capability::disable);
        }
    }

    private Capability requirePublishedMcp(UUID mcpId) {
        Capability mcp = get(mcpId);
        if (mcp.getType() != Capability.Type.MCP || mcp.getStatus() != Capability.Status.PUBLISHED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MCP_NOT_AVAILABLE", "MCP 服务不存在或未发布");
        }
        return mcp;
    }

    private Capability requireToolOfMcp(UUID toolId, UUID mcpId) {
        Capability tool = get(toolId);
        if (tool.getType() != Capability.Type.TOOL || !mcpId.equals(tool.getParentId())
                || tool.getStatus() != Capability.Status.PUBLISHED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MCP_TOOL_NOT_AVAILABLE", "Tool 不属于该 MCP 或尚未发布");
        }
        return tool;
    }

    private void saveBundleMembers(UUID bundleId, List<UUID> memberIds) {
        List<BundleMember> members = new ArrayList<>();
        for (int i = 0; i < memberIds.size(); i++) {
            members.add(new BundleMember(bundleId, memberIds.get(i), true, i));
        }
        bundleMemberRepository.saveAll(members);
    }

    private BundleDetail bundleDetail(Capability bundle) {
        if (bundle.getType() != Capability.Type.BUNDLE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_BUNDLE", "目标能力不是 Bundle");
        }
        Map<UUID, Capability> capabilities = capabilityRepository.findByIdIn(
                bundleMemberRepository.findByBundleIdOrderBySortOrderAsc(bundle.getId()).stream()
                        .map(BundleMember::getMemberId).toList()).stream()
                .collect(Collectors.toMap(Capability::getId, Function.identity()));
        List<CapabilityView> members = bundleMemberRepository.findByBundleIdOrderBySortOrderAsc(bundle.getId()).stream()
                .map(member -> capabilities.get(member.getMemberId()))
                .filter(java.util.Objects::nonNull).map(CapabilityView::from).toList();
        return new BundleDetail(CapabilityView.from(bundle), members);
    }

    public record CreateCommand(Capability.Type type, UUID parentId, Capability.SourceKind sourceKind,
                                String externalRef, String name, String description, String releaseVersion,
                                String sourceRef, String integrityHash) {
    }
    public record GatewayCapability(Capability.Type type, String parentExternalRef, String externalRef,
                                    String name, String description, String releaseVersion, String sourceRef) {
    }
    public record SyncResult(int created, int updated, Instant synchronizedAt) {
    }
    public record AuthorizationDecision(boolean allowed, String reason, UUID capabilityId,
                                        String releaseVersion, Instant evaluatedAt) {
        static AuthorizationDecision allowed(UUID id, String version) {
            return new AuthorizationDecision(true, "ALLOWED", id, version, Instant.now());
        }
        static AuthorizationDecision denied(String reason) {
            return new AuthorizationDecision(false, reason, null, null, Instant.now());
        }
    }
    public record CapabilityView(UUID id, Capability.Type type, String externalRef, String name, String description,
                                 String releaseVersion, String sourceRef, String integrityHash) {
        static CapabilityView from(Capability item) {
            return new CapabilityView(item.getId(), item.getType(), item.getExternalRef(), item.getName(),
                    item.getDescription(), item.getReleaseVersion(), item.getSourceRef(), item.getIntegrityHash());
        }
    }
    public record McpCatalogItem(CapabilityView mcp, List<CapabilityView> tools) {
    }
    public record BundleCatalogItem(CapabilityView bundle, List<CapabilityView> members) {
    }
    public record Catalog(List<McpCatalogItem> mcps, List<CapabilityView> skills,
                          List<BundleCatalogItem> bundles, List<CapabilityView> plugins,
                          List<CapabilityView> instructions, Instant evaluatedAt) {
    }
    public record MarketCapability(CapabilityView capability, boolean authorized) {
    }
    public record CreateBundleCommand(String externalRef, String name, String description, String releaseVersion,
                                      List<UUID> memberIds) {
    }
    public record BundleDetail(CapabilityView bundle, List<CapabilityView> members) {
    }
    public record GrantSubjectSummary(String workcode, List<GrantCapabilitySummary> capabilities,
                                      int activeGrantCount, Instant nearestExpiry) {
    }
    public record GrantCapabilitySummary(UUID capabilityId, Capability.Type type, String name, String releaseVersion,
                                         Instant validUntil) {
    }
    public record McpToolGrantDetail(CapabilityView mcp, List<ToolGrantState> tools) {
    }
    public record ToolGrantState(CapabilityView tool, boolean excluded) {
    }
    private record EffectiveAccess(Set<UUID> capabilityIds, Set<UUID> grantedBundleIds) {
    }
}
