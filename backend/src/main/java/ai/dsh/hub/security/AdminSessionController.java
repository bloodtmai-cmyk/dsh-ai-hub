package ai.dsh.hub.security;

import ai.dsh.hub.admin.AdminAuditService;
import ai.dsh.hub.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminSessionController {
    private final AuthenticationManager authenticationManager;
    private final AdminAuditService auditService;
    private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AdminSessionController(AuthenticationManager authenticationManager, AdminAuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/session")
    public SessionResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
                                 HttpServletResponse servletResponse) {
        try {
            Authentication authenticated = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.workcode(), request.password()));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authenticated);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, servletRequest, servletResponse);
            auditService.record(authenticated.getName(), "ADMIN_LOGIN", "ADMIN_SESSION", null, "SUCCESS", null);
            return new SessionResponse(authenticated.getName(), true);
        } catch (BadCredentialsException exception) {
            auditService.record("anonymous", "ADMIN_LOGIN", "ADMIN_SESSION", null, "DENIED", "invalid credentials");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "工号或密码错误");
        }
    }

    @GetMapping("/session")
    public SessionResponse session(Authentication authentication) {
        return new SessionResponse(authentication.getName(), true);
    }

    @DeleteMapping("/session")
    public void logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        auditService.record(authentication.getName(), "ADMIN_LOGOUT", "ADMIN_SESSION", null, "SUCCESS", null);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    public record LoginRequest(@NotBlank String workcode, @NotBlank String password) {
    }

    public record SessionResponse(String workcode, boolean authenticated) {
    }
}
