package ai.dsh.hub.extension;

import java.util.List;
import java.util.Map;

public interface ExtensionHostGateway {
    Inventory inventory();

    Map<String, Object> invoke(String pluginId, String operation, Map<String, Object> payload);

    record Inventory(boolean enabled, boolean reachable, List<PluginDescriptor> plugins, String message) {
        public Inventory {
            plugins = plugins == null ? List.of() : List.copyOf(plugins);
        }
    }

    record PluginDescriptor(
            String id,
            String displayName,
            String description,
            String version,
            String apiVersion,
            String kind,
            List<String> provides,
            List<String> requires,
            List<String> permissions,
            List<String> secrets,
            List<UiSlot> uiSlots,
            String state,
            String detail,
            String registeredAt
    ) {
    }

    record UiSlot(String id, String label, String page) {
    }
}
