package ai.dsh.hub.extension;

import ai.dsh.hub.common.ApiException;
import ai.dsh.hub.config.AiHubProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CordisExtensionHostClient implements ExtensionHostGateway {
    private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "::1", "localhost");

    private final boolean enabled;
    private final boolean required;
    private final List<String> requiredPlugins;
    private final RestClient client;

    public CordisExtensionHostClient(RestClient.Builder builder, AiHubProperties properties) {
        AiHubProperties.ExtensionHost config = properties.extensionHost();
        this.enabled = config.enabled();
        this.required = config.required();
        this.requiredPlugins = config.requiredPlugins();
        if (!enabled) {
            this.client = null;
            return;
        }
        validateConfig(config);
        this.client = builder.baseUrl(config.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .build();
    }

    @Override
    public Inventory inventory() {
        if (!enabled) return new Inventory(false, false, List.of(), "Cordis Extension Host 未启用");
        try {
            InventoryResponse response = client.get().uri("/internal/plugins").retrieve().body(InventoryResponse.class);
            return new Inventory(true, true, response == null ? List.of() : response.plugins(), null);
        } catch (RestClientException exception) {
            return new Inventory(true, false, List.of(), "Cordis Extension Host 不可用");
        }
    }

    @Override
    public Map<String, Object> invoke(String pluginId, String operation, Map<String, Object> payload) {
        if (!enabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EXTENSION_HOST_DISABLED", "Cordis Extension Host 未启用");
        }
        try {
            Map<String, Object> response = client.post()
                    .uri("/internal/plugins/{pluginId}/operations/{operation}", pluginId, operation)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "EXTENSION_HOST_INVALID_RESPONSE", "平台插件未返回结果");
            }
            return response;
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EXTENSION_HOST_REQUEST_FAILED", "平台插件调用失败");
        }
    }

    public void verifyRequired() {
        if (!required) return;
        Inventory inventory = inventory();
        if (!inventory.reachable()) throw new IllegalStateException("Cordis Extension Host is required but unavailable");
        Map<String, PluginDescriptor> plugins = inventory.plugins().stream()
                .collect(java.util.stream.Collectors.toMap(PluginDescriptor::id, plugin -> plugin));
        for (String pluginId : requiredPlugins) {
            PluginDescriptor plugin = plugins.get(pluginId);
            if (plugin == null) throw new IllegalStateException("Required Cordis plugin is missing: " + pluginId);
            if (!"READY".equals(plugin.state())) {
                throw new IllegalStateException("Required Cordis plugin is not ready: " + pluginId);
            }
        }
    }

    private static void validateConfig(AiHubProperties.ExtensionHost config) {
        if (config.baseUrl() == null || config.baseUrl().isBlank()) {
            throw new IllegalStateException("Cordis Extension Host URL is required");
        }
        URI uri = URI.create(config.baseUrl());
        if (!"http".equalsIgnoreCase(uri.getScheme()) || !LOOPBACK_HOSTS.contains(uri.getHost())) {
            throw new IllegalStateException("Cordis Extension Host must use loopback HTTP");
        }
        if (config.apiKey() == null || config.apiKey().length() < 24) {
            throw new IllegalStateException("Cordis Extension Host API key must contain at least 24 characters");
        }
    }

    private record InventoryResponse(List<PluginDescriptor> plugins) {
        private InventoryResponse {
            plugins = plugins == null ? List.of() : List.copyOf(plugins);
        }
    }
}
