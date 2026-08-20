package ai.dsh.hub.keymgmt;

import ai.dsh.hub.admin.AdminAuditService;
import ai.dsh.hub.common.ApiException;
import ai.dsh.hub.common.CryptoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ApiKeyApplicationService {
    private final ApiKeyApplicationRepository applicationRepository;
    private final ApiKeyBindingRepository bindingRepository;
    private final KeyProvisioner provisioner;
    private final CryptoService cryptoService;
    private final AdminAuditService auditService;

    public ApiKeyApplicationService(ApiKeyApplicationRepository applicationRepository,
                                    ApiKeyBindingRepository bindingRepository,
                                    KeyProvisioner provisioner,
                                    CryptoService cryptoService,
                                    AdminAuditService auditService) {
        this.applicationRepository = applicationRepository;
        this.bindingRepository = bindingRepository;
        this.provisioner = provisioner;
        this.cryptoService = cryptoService;
        this.auditService = auditService;
    }

    @Transactional
    public ApplicationView apply(String workcode, String purpose) {
        var latest = applicationRepository.findTopByWorkcodeOrderBySubmittedAtDesc(workcode);
        if (latest.isPresent()) {
            ApiKeyApplication application = latest.get();
            ApiKeyBinding binding = bindingRepository.findByApplicationId(application.getId()).orElse(null);
            if (application.getStatus() == ApiKeyApplication.Status.PENDING
                    || (application.getStatus() == ApiKeyApplication.Status.APPROVED
                    && binding != null && binding.isUsable())) {
                return ApplicationView.from(application, binding);
            }
        }
        ApiKeyApplication application = new ApiKeyApplication(workcode, purpose);
        applicationRepository.save(application);
        return ApplicationView.from(application, null);
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> listForUser(String workcode) {
        List<ApiKeyApplication> applications = applicationRepository.findByWorkcodeOrderBySubmittedAtDesc(workcode);
        Map<UUID, ApiKeyBinding> bindings = bindingsFor(applications);
        return applications.stream().map(item -> ApplicationView.from(item, bindings.get(item.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public Page<ApplicationView> listForAdmin(ApiKeyApplication.Status status, String workcode, Pageable pageable) {
        String query = workcode == null ? "" : workcode.trim();
        Page<ApiKeyApplication> page;
        if (status == null && query.isEmpty()) {
            page = applicationRepository.findAll(pageable);
        } else if (status == null) {
            page = applicationRepository.findByWorkcodeContainingIgnoreCase(query, pageable);
        } else if (query.isEmpty()) {
            page = applicationRepository.findByStatus(status, pageable);
        } else {
            page = applicationRepository.findByStatusAndWorkcodeContainingIgnoreCase(status, query, pageable);
        }
        Map<UUID, ApiKeyBinding> bindings = bindingsFor(page.getContent());
        return page.map(item -> ApplicationView.from(item, bindings.get(item.getId())));
    }

    @Transactional
    public ApplicationView approve(UUID applicationId, String reviewer, String provider,
                                   String baseUrl, String apiKey) {
        ApiKeyApplication application = getForReview(applicationId);
        if (application.getStatus() != ApiKeyApplication.Status.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_ALREADY_REVIEWED", "申请已处理");
        }
        return provisionAndApprove(application, reviewer, provider, baseUrl, apiKey, "APPROVE_API_KEY");
    }

    @Transactional
    public ApplicationView grant(String workcode, String reviewer, String provider,
                                 String baseUrl, String apiKey) {
        String normalizedWorkcode = workcode.trim();
        var latest = applicationRepository.findTopByWorkcodeOrderBySubmittedAtDesc(normalizedWorkcode);
        if (latest.isPresent()) {
            ApiKeyApplication application = latest.get();
            if (application.getStatus() == ApiKeyApplication.Status.PENDING) {
                throw new ApiException(HttpStatus.CONFLICT, "PENDING_KEY_APPLICATION_EXISTS",
                        "该用户已有待审批申请，请从列表中完成审批");
            }
            ApiKeyBinding binding = bindingRepository.findByApplicationId(application.getId()).orElse(null);
            if (application.getStatus() == ApiKeyApplication.Status.APPROVED
                    && binding != null && binding.isUsable()) {
                throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_KEY_EXISTS", "该用户已有生效中的 API Key");
            }
        }
        ApiKeyApplication application = applicationRepository.save(
                new ApiKeyApplication(normalizedWorkcode, "管理员主动授权"));
        return provisionAndApprove(application, reviewer, provider, baseUrl, apiKey, "GRANT_API_KEY");
    }

    private ApplicationView provisionAndApprove(ApiKeyApplication application, String reviewer,
                                                 String provider, String baseUrl, String apiKey,
                                                 String auditAction) {
        String normalizedProvider = normalizeProvider(provider);
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        KeyProvisioner.ProvisionedKey provisioned = provisioner.provision(
                new KeyProvisioner.Request(application.getId(), application.getWorkcode(), apiKey));
        UUID bindingId = UUID.randomUUID();
        ApiKeyBinding binding = new ApiKeyBinding(
                bindingId,
                application.getId(),
                provisioned.providerKeyId(),
                cryptoService.encrypt(provisioned.secret(), aad(bindingId)),
                cryptoService.sha256(provisioned.secret()),
                mask(provisioned.secret()),
                normalizedProvider,
                normalizedBaseUrl);
        bindingRepository.save(binding);
        application.approve(reviewer, null);
        auditService.record(reviewer, auditAction, "API_KEY_APPLICATION", application.getId().toString(),
                "SUCCESS", "workcode=" + application.getWorkcode());
        return ApplicationView.from(application, binding);
    }

    @Transactional
    public ApplicationView reject(UUID applicationId, String reviewer, String comment) {
        ApiKeyApplication application = getForReview(applicationId);
        application.reject(reviewer, comment);
        auditService.record(reviewer, "REJECT_API_KEY", "API_KEY_APPLICATION", applicationId.toString(),
                "SUCCESS", "workcode=" + application.getWorkcode());
        return ApplicationView.from(application, null);
    }

    @Transactional
    public ClaimedSecret claim(UUID applicationId, String workcode) {
        ApiKeyApplication application = getOwned(applicationId, workcode);
        if (application.getStatus() != ApiKeyApplication.Status.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "KEY_NOT_APPROVED", "API Key 尚未批准");
        }
        ApiKeyBinding binding = bindingRepository.findWithLockByApplicationId(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "KEY_NOT_PROVISIONED", "API Key 尚未签发"));
        if (binding.getProvider() == null || binding.getProvider().isBlank()
                || binding.getBaseUrl() == null || binding.getBaseUrl().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "MODEL_ACCESS_METADATA_MISSING",
                    "模型访问地址尚未配置，请联系管理员重新签发");
        }
        String secret = cryptoService.decrypt(binding.getSecretCiphertext(), aad(binding.getId()));
        binding.claim();
        return new ClaimedSecret(secret, binding.getProvider(), binding.getBaseUrl(),
                binding.getSecretMask(), binding.getClaimedAt());
    }

    @Transactional
    public ApplicationView revoke(UUID applicationId, String reviewer) {
        ApiKeyApplication application = get(applicationId);
        ApiKeyBinding binding = bindingRepository.findWithLockByApplicationId(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "KEY_NOT_PROVISIONED", "API Key 尚未签发"));
        provisioner.revoke(binding.getProviderKeyId());
        binding.revoke();
        application.markRevoked();
        auditService.record(reviewer, "REVOKE_API_KEY", "API_KEY_APPLICATION", applicationId.toString(),
                "SUCCESS", "workcode=" + application.getWorkcode());
        return ApplicationView.from(application, binding);
    }

    private ApiKeyApplication get(UUID id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KEY_APPLICATION_NOT_FOUND", "API Key 申请不存在"));
    }

    private ApiKeyApplication getForReview(UUID id) {
        return applicationRepository.findWithLockById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KEY_APPLICATION_NOT_FOUND", "API Key 申请不存在"));
    }

    private ApiKeyApplication getOwned(UUID id, String workcode) {
        ApiKeyApplication application = get(id);
        if (!application.getWorkcode().equals(workcode)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "KEY_APPLICATION_NOT_FOUND", "API Key 申请不存在");
        }
        return application;
    }

    private Map<UUID, ApiKeyBinding> bindingsFor(Collection<ApiKeyApplication> applications) {
        List<UUID> ids = applications.stream().map(ApiKeyApplication::getId).toList();
        if (ids.isEmpty()) return Map.of();
        return bindingRepository.findByApplicationIdIn(ids).stream()
                .collect(Collectors.toMap(ApiKeyBinding::getApplicationId, Function.identity()));
    }

    private static String mask(String secret) {
        if (secret.length() <= 12) return "****";
        return secret.substring(0, 7) + "..." + secret.substring(secret.length() - 4);
    }

    private static String aad(UUID bindingId) {
        return "api-key-binding:" + bindingId;
    }

    private static String normalizeProvider(String provider) {
        String normalized = provider == null ? "" : provider.trim();
        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MODEL_PROVIDER",
                    "请填写有效的模型 Provider 标识");
        }
        return normalized;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        try {
            URI uri = new URI(normalized);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new URISyntaxException(normalized, "unsupported model gateway URL");
            }
            return normalized.replaceAll("/+$", "");
        } catch (URISyntaxException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MODEL_GATEWAY_URL",
                    "请填写有效的模型网关地址");
        }
    }

    public record ApplicationView(UUID id, String workcode, String purpose,
                                  ApiKeyApplication.Status status, Instant submittedAt, Instant reviewedAt,
                                  String reviewer, String decisionComment, String secretMask,
                                  String provider, String baseUrl,
                                  ApiKeyBinding.Status keyStatus, Instant claimedAt) {
        static ApplicationView from(ApiKeyApplication application, ApiKeyBinding binding) {
            return new ApplicationView(application.getId(), application.getWorkcode(), application.getPurpose(),
                    application.getStatus(), application.getSubmittedAt(),
                    application.getReviewedAt(), application.getReviewer(), application.getDecisionComment(),
                    binding == null ? null : binding.getSecretMask(),
                    binding == null ? null : binding.getProvider(),
                    binding == null ? null : binding.getBaseUrl(),
                    binding == null ? null : binding.getEffectiveStatus(),
                    binding == null ? null : binding.getClaimedAt());
        }
    }

    public record ClaimedSecret(String apiKey, String provider, String baseUrl, String mask, Instant claimedAt) {
    }
}
