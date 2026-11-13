package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.config.DeviceProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceKeyCipherTest {
    private static final String SECRET = "test-device-encryption-secret-with-32-characters";
    private static final String VALUE = "arduino-device-key-123";

    @Test
    void decryptsTheEncryptedValue() {
        DeviceKeyCipher cipher = cipher(SECRET);

        assertThat(cipher.decrypt(cipher.encrypt(VALUE))).isEqualTo(VALUE);
    }

    @Test
    void usesDifferentRandomIvForEachEncryption() {
        DeviceKeyCipher cipher = cipher(SECRET);

        assertThat(cipher.encrypt(VALUE)).isNotEqualTo(cipher.encrypt(VALUE));
    }

    @Test
    void encryptedOutputDoesNotContainOriginalValue() {
        String encrypted = cipher(SECRET).encrypt(VALUE);

        assertThat(encrypted).doesNotContain(VALUE);
    }

    @Test
    void rejectsTamperedCiphertextAndDifferentSecret() {
        DeviceKeyCipher cipher = cipher(SECRET);
        String encrypted = cipher.encrypt(VALUE);
        byte[] payload = Base64.getDecoder().decode(encrypted);
        payload[payload.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher("different-device-secret-with-32-chars").decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    private DeviceKeyCipher cipher(String secret) {
        return new DeviceKeyCipher(new DeviceProperties(secret, 5, 10));
    }
}
