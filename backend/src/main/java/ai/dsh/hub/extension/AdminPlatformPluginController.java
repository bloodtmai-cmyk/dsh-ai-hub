package ai.dsh.hub.extension;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/platform-plugins")
public class AdminPlatformPluginController {
    private final ExtensionHostGateway extensionHost;

    public AdminPlatformPluginController(ExtensionHostGateway extensionHost) {
        this.extensionHost = extensionHost;
    }

    @GetMapping
    public ExtensionHostGateway.Inventory inventory() {
        return extensionHost.inventory();
    }
}
