package ai.dsh.hub;

import ai.dsh.hub.config.AiHubProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AiHubProperties.class)
public class AiHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiHubApplication.class, args);
    }
}
