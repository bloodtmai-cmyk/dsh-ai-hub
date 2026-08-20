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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CapabilityApplicationService {
    private final CapabilityApplicationRepository applicationRepository;
    private final CapabilityService capabilityService;
    private final AdminAuditService auditService;

    public CapabilityApplicationService(CapabilityApplicationRepository applicationRepository,
                                        CapabilityService capabilityService, AdminAuditService auditService) {
        this.applicationRepository = applicationRepository;
        this.capabilityService = capabilityService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<StoreItem> store(String workcode) {
        IdentitySupport.validateWorkcode(workcode);
        Map<UUID, CapabilityApplication> latest = new LinkedHashMap<>();
        applicationRepository.findByWorkcodeOrderBySubmittedAtDesc(workcode)
                .forEach(item -> latest.putIfAbsent(item.getCapabilityId(), item));
        return capabilityService.market(workcode).stream().map(item -> {
            CapabilityApplication application = latest.get(item.capability().id());
            StoreState state = item.authorized() ? StoreState.AUTHORIZED
                    : application != null && application.getStatus() == CapabilityApplication.Status.PENDING
                    ? StoreState.PENDING
                    : application != null && application.getStatus() == CapabilityApplication.Status.REJECTED
                    ? StoreState.REJECTED : StoreState.AVAILABLE;
            return new StoreItem(item.capability(), state,
                    application == null ? null : ApplicationView.from(application, item.capability()));
        }).toList();
    }

    @Transactional
    public ApplicationView apply(String workcode, UUID capabilityId, String reason) {
        IdentitySupport.validateWorkcode(workcode);
        Capability capability = capabilityService.getCapability(capabilityId);
        if (capability.getStatus() != Capability.Status.PUBLISHED
                || capability.getType() == Capability.Type.TOOL
                || capability.getType() == Capability.Type.INSTRUCTION) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CAPABILITY_NOT_REQUESTABLE", "能力不存在或不可申请");
        }
        if (capabilityService.market(workcode).stream()
                .anyMatch(item -> item.capability().id().equals(capabilityId) && item.authorized())) {
            throw new ApiException(HttpStatus.CONFLICT, "CAPABILITY_ALREADY_GRANTED", "当前用户已获该能力授权");
        }
        applicationRepository.findTopByWorkcodeAndCapabilityIdOrderBySubmittedAtDesc(workcode, capabilityId)
                .filter(item -> item.getStatus() == CapabilityApplication.Status.PENDING)
                .ifPresent(item -> { throw new ApiException(HttpStatus.CONFLICT, "PENDING_APPLICATION_EXISTS", "已有待审批申请"); });
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank() || normalizedReason.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_APPLICATION_REASON", "申请理由不能为空且不超过 500 字符");
        }
        CapabilityApplication application = new CapabilityApplication(workcode, capabilityId, normalizedReason);
        applicationRepository.save(application);
        return ApplicationView.from(application, CapabilityService.CapabilityView.from(capability));
    }

    @Transactional(readOnly = true)
    public Page<ApplicationView> listForAdmin(CapabilityApplication.Status status, Pageable pageable) {
        Page<CapabilityApplication> page = status == null
                ? applicationRepository.findAll(pageable) : applicationRepository.findByStatus(status, pageable);
        return page.map(item -> ApplicationView.from(item,
                CapabilityService.CapabilityView.from(capabilityService.getCapability(item.getCapabilityId()))));
    }

    @Transactional
    public ApplicationView approve(UUID applicationId, String reviewer, Instant validUntil, String comment) {
        CapabilityApplication application = getForReview(applicationId);
        Capability capability = capabilityService.getCapability(application.getCapabilityId());
        capabilityService.grant(application.getWorkcode(), capability.getId(), Instant.now(), validUntil, reviewer);
        application.approve(reviewer, comment);
        auditService.record(reviewer, "APPROVE_CAPABILITY_APPLICATION", "CAPABILITY_APPLICATION",
                applicationId.toString(), "SUCCESS", "workcode=" + application.getWorkcode());
        return ApplicationView.from(application, CapabilityService.CapabilityView.from(capability));
    }

    @Transactional
    public ApplicationView reject(UUID applicationId, String reviewer, String comment) {
        CapabilityApplication application = getForReview(applicationId);
        Capability capability = capabilityService.getCapability(application.getCapabilityId());
        String normalized = comment == null ? "" : comment.trim();
        if (normalized.isBlank() || normalized.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DECISION_COMMENT_REQUIRED", "驳回意见不能为空且不超过 500 字符");
        }
        application.reject(reviewer, normalized);
        auditService.record(reviewer, "REJECT_CAPABILITY_APPLICATION", "CAPABILITY_APPLICATION",
                applicationId.toString(), "SUCCESS", "workcode=" + application.getWorkcode());
        return ApplicationView.from(application, CapabilityService.CapabilityView.from(capability));
    }

    private CapabilityApplication getForReview(UUID id) {
        return applicationRepository.findWithLockById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CAPABILITY_APPLICATION_NOT_FOUND", "能力申请不存在"));
    }

    public enum StoreState { AVAILABLE, PENDING, AUTHORIZED, REJECTED }
    public record StoreItem(CapabilityService.CapabilityView capability, StoreState state,
                            ApplicationView latestApplication) {
    }
    public record ApplicationView(UUID id, String workcode, CapabilityService.CapabilityView capability,
                                  String reason, CapabilityApplication.Status status, Instant submittedAt,
                                  Instant reviewedAt, String reviewer, String decisionComment) {
        static ApplicationView from(CapabilityApplication item, CapabilityService.CapabilityView capability) {
            return new ApplicationView(item.getId(), item.getWorkcode(), capability, item.getReason(), item.getStatus(),
                    item.getSubmittedAt(), item.getReviewedAt(), item.getReviewer(), item.getDecisionComment());
        }
    }
}
