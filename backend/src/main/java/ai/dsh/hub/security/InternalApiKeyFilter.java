package ai.dsh.hub.security;

import ai.dsh.hub.config.AiHubProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public class InternalApiKeyFilter extends OncePerRequestFilter {
    private final byte[] expected;

    public InternalApiKeyFilter(AiHubProperties properties) {
        this.expected = properties.security().internalApiKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        byte[] actual = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        if (!MessageDigest.isEqual(expected, actual)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":\"INVALID_SERVICE_IDENTITY\",\"message\":\"内部服务身份无效\"}");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "gateway-service", "", List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
        filterChain.doFilter(request, response);
    }
}
