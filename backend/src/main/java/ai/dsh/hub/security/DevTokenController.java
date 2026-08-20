package ai.dsh.hub.security;

import ai.dsh.hub.common.IdentitySupport;
import ai.dsh.hub.config.AiHubProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Profile("local")
@RestController
@RequestMapping("/api/dev/tokens")
public class DevTokenController {
    private final JwtEncoder encoder;
    private final AiHubProperties properties;

    public DevTokenController(JwtEncoder encoder, AiHubProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    @PostMapping("/{workcode}")
    public Map<String, Object> token(@PathVariable String workcode) {
        IdentitySupport.validateWorkcode(workcode);
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.security().jwtIssuer())
                .subject(workcode)
                .audience(List.of(properties.security().jwtAudience()))
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("workcode", workcode)
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return Map.of("accessToken", value, "tokenType", "Bearer", "expiresIn", 3600);
    }
}
