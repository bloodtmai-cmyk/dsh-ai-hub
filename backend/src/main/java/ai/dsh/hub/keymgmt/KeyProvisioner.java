package ai.dsh.hub.keymgmt;

import java.util.UUID;

public interface KeyProvisioner {
    ProvisionedKey provision(Request request);
    void revoke(String providerKeyId);

    record Request(UUID applicationId, String workcode, String suppliedSecret) {
    }

    record ProvisionedKey(String providerKeyId, String secret) {
    }
}
