package ai.dsh.hub.catalog;

import ai.dsh.hub.common.ApiException;
import ai.dsh.hub.config.AiHubProperties;
import ai.dsh.hub.extension.ExtensionHostGateway;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GatewayCatalogConnector {
    private final boolean extensionHostEnabled;
    private final ExtensionHostGateway extensionHost;

    public GatewayCatalogConnector(AiHubProperties properties, ExtensionHostGateway extensionHost) {
        this.extensionHostEnabled = properties.extensionHost().enabled();
        this.extensionHost = extensionHost;
    }

    public List<CapabilityService.GatewayCapability> normalize(List<InternalCapabilityController.SyncItem> items) {
        if (!extensionHostEnabled) return items.stream().map(InternalCapabilityController.SyncItem::toCommand).toList();
        List<Map<String, Object>> payloadItems = items.stream().map(this::payload).toList();
        Map<String, Object> response = extensionHost.invoke("gateway-catalog", "normalize",
                Map.of("capabilities", payloadItems));
        Object rawCapabilities = response.get("capabilities");
        if (!(rawCapabilities instanceof List<?> capabilities)) throw invalidResponse();
        return capabilities.stream().map(this::command).toList();
    }

    private Map<String, Object> payload(InternalCapabilityController.SyncItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", item.type().name());
        if (item.parentExternalRef() != null) payload.put("parentExternalRef", item.parentExternalRef());
        payload.put("externalRef", item.externalRef());
        payload.put("name", item.name());
        payload.put("description", item.description());
        payload.put("releaseVersion", item.releaseVersion());
        if (item.sourceRef() != null) payload.put("sourceRef", item.sourceRef());
        return payload;
    }

    private CapabilityService.GatewayCapability command(Object candidate) {
        if (!(candidate instanceof Map<?, ?> item)) throw invalidResponse();
        try {
            Capability.Type type = Capability.Type.valueOf(requiredString(item.get("type")));
            return new CapabilityService.GatewayCapability(
                    type,
                    optionalString(item.get("parentExternalRef")),
                    requiredString(item.get("externalRef")),
                    requiredString(item.get("name")),
                    requiredString(item.get("description")),
                    requiredString(item.get("releaseVersion")),
                    optionalString(item.get("sourceRef"))
            );
        } catch (IllegalArgumentException exception) {
            throw invalidResponse();
        }
    }

    private static String requiredString(Object value) {
        if (!(value instanceof String string)) throw invalidResponse();
        return string;
    }

    private static String optionalString(Object value) {
        return value instanceof String string ? string : null;
    }

    private static ApiException invalidResponse() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "GATEWAY_CATALOG_PLUGIN_INVALID_RESPONSE",
                "Gateway Catalog 插件返回无效结果");
    }
}
