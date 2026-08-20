package ai.dsh.hub.extension;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExtensionHostReadiness implements ApplicationRunner {
    private final CordisExtensionHostClient client;

    public ExtensionHostReadiness(CordisExtensionHostClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) {
        client.verifyRequired();
    }
}
