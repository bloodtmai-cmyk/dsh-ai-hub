package ai.dsh.hub.keymgmt;

import ai.dsh.hub.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ManualKeyProvisioner implements KeyProvisioner {
    @Override
    public ProvisionedKey provision(Request request) {
        String secret = request.suppliedSecret();
        if (secret == null || secret.isBlank() || secret.length() > 4096
                || secret.chars().anyMatch(Character::isWhitespace)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_API_KEY", "请填写有效的 API Key");
        }
        return new ProvisionedKey("manual:" + request.applicationId(), secret);
    }

    @Override
    public void revoke(String providerKeyId) {
        // Manually supplied credentials must also be disabled in their upstream provider.
    }
}
