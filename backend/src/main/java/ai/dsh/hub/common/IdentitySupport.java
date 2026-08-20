package ai.dsh.hub.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class IdentitySupport {
    private static final Pattern WORKCODE = Pattern.compile("^[0-9]{5,12}$");

    private IdentitySupport() {
    }

    public static String requireWorkcode(Authentication authentication) {
        String workcode = authentication.getName();
        if (!WORKCODE.matcher(workcode).matches()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_IDENTITY", "Token 缺少有效工号");
        }
        return workcode;
    }

    public static String validateWorkcode(String workcode) {
        if (workcode == null || !WORKCODE.matcher(workcode).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WORKCODE", "工号必须为 5 到 12 位数字");
        }
        return workcode;
    }

    public static Set<String> requireDepartmentCodes(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return Set.of();
        }
        Object claim = jwtAuthentication.getTokenAttributes().get("department_codes");
        if (claim == null) return Set.of();

        Collection<?> values = claim instanceof Collection<?> collection
                ? collection : claim instanceof String text ? Set.of(text.split(",")) : null;
        if (values == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_IDENTITY", "Token 部门信息格式无效");
        }
        Set<String> departments = new LinkedHashSet<>();
        for (Object value : values) {
            String normalized = value == null ? "" : value.toString().trim();
            if (normalized.isBlank() || normalized.length() > 120
                    || !normalized.matches("[A-Za-z0-9._:/-]+")) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_IDENTITY", "Token 部门信息格式无效");
            }
            departments.add(normalized);
        }
        return Set.copyOf(departments);
    }
}
