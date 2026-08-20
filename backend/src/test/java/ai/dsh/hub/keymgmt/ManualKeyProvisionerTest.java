package ai.dsh.hub.keymgmt;

import ai.dsh.hub.common.ApiException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualKeyProvisionerTest {
    private final ManualKeyProvisioner provisioner = new ManualKeyProvisioner();

    @Test
    void bindsSuppliedKeyWithoutChangingIt() {
        UUID applicationId = UUID.randomUUID();
        var result = provisioner.provision(new KeyProvisioner.Request(
                applicationId, "1000001", "sk-manual-test-123456"));

        assertThat(result.providerKeyId()).isEqualTo("manual:" + applicationId);
        assertThat(result.secret()).isEqualTo("sk-manual-test-123456");
    }

    @Test
    void rejectsWhitespaceInSuppliedKey() {
        assertThatThrownBy(() -> provisioner.provision(new KeyProvisioner.Request(
                UUID.randomUUID(), "1000001", "sk-invalid key")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("有效的 API Key");
    }
}
