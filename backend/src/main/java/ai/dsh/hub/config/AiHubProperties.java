package ai.dsh.hub.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "ai-hub")
public record AiHubProperties(
        @Valid Admin admin,
        @Valid Security security,
        @Valid Audit audit,
        @Valid Release release,
        @Valid ExtensionHost extensionHost
) {
    public record Admin(@NotBlank String workcode, @NotBlank String password) {
    }

    public record Security(
            String jwtHmacSecret,
            String jwtJwkSetUri,
            @NotBlank String jwtIssuer,
            @NotBlank String jwtAudience,
            @NotBlank String masterKey,
            @NotBlank String internalApiKey
    ) {
    }

    public record Audit(@Positive int retentionDays, @Positive int maxContentLength) {
    }

    public record Release(@NotBlank String artifactRoot, @Positive long maxArtifactBytes) {
    }

    public record ExtensionHost(
            boolean enabled,
            boolean required,
            String baseUrl,
            String apiKey,
            List<String> requiredPlugins
    ) {
        public ExtensionHost {
            requiredPlugins = requiredPlugins == null ? List.of() : List.copyOf(requiredPlugins);
        }
    }
}
