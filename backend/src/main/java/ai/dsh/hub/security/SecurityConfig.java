package ai.dsh.hub.security;

import ai.dsh.hub.config.AiHubProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class SecurityConfig {
    @Bean
    @Profile({"local", "test"})
    SecretKey jwtSecretKey(AiHubProperties properties) {
        byte[] decoded = Base64.getDecoder().decode(properties.security().jwtHmacSecret());
        if (decoded.length < 32) {
            throw new IllegalStateException("AI_HUB_JWT_HMAC_SECRET must decode to at least 32 bytes");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    @Profile({"local", "test"})
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, AiHubProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(jwtValidator(properties));
        return decoder;
    }

    @Bean
    @Profile({"local", "test"})
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    @Profile("prod")
    JwtDecoder productionJwtDecoder(AiHubProperties properties) {
        String jwkSetUri = properties.security().jwtJwkSetUri();
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            throw new IllegalStateException("AI_HUB_JWT_JWK_SET_URI is required in prod");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(jwtValidator(properties));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> jwtValidator(AiHubProperties properties) {
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.security().jwtIssuer());
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(properties.security().jwtAudience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "JWT audience is invalid", null));
        return new DelegatingOAuth2TokenValidator<>(issuer, audience);
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("workcode");
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService adminUserDetailsService(AiHubProperties properties) {
        return username -> {
            if (!properties.admin().workcode().equals(username)) {
                throw new UsernameNotFoundException("Admin account not found");
            }
            return User.withUsername(properties.admin().workcode())
                    .password(properties.admin().password())
                    .roles("ADMIN")
                    .build();
        };
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    @Order(1)
    SecurityFilterChain internalApiSecurity(HttpSecurity http, AiHubProperties properties) throws Exception {
        return http
                .securityMatcher("/api/internal/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalApiKeyFilter(properties), org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().hasRole("SERVICE"))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain clientApiSecurity(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        return http
                .securityMatcher("/api/client/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain webSecurity(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setHeaderName("X-XSRF-TOKEN");
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .ignoringRequestMatchers("/api/dev/**"))
                .securityContext(context -> context.requireExplicitSave(true))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/admin/csrf", "/api/admin/session", "/api/dev/**", "/actuator/health").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(Customizer.withDefaults())
                .build();
    }
}
