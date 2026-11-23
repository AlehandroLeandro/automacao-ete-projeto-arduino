package br.com.ete.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveConfigurationValidatorTest {

    private final SensitiveConfigurationValidator validator = new SensitiveConfigurationValidator();
    private final JwtProperties jwtProperties = new JwtProperties("jwt_secret_with_at_least_32_characters", 15, 30);
    private final InitialAdminProperties initialAdminProperties =
            new InitialAdminProperties("Admin", "admin", "admin_password");
    private final MockEnvironment environment = new MockEnvironment()
            .withProperty("DB_PASSWORD", "database_password");

    @Test
    void rejectsDeviceEncryptionSecretPlaceholder() {
        assertThatThrownBy(() -> validate("replace_with_a_random_device_key_secret_with_32_chars"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEVICE_KEY_ENCRYPTION_SECRET");
    }

    @Test
    void rejectsDeviceEncryptionSecretShorterThan32Characters() {
        assertThatThrownBy(() -> validate("too_short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEVICE_KEY_ENCRYPTION_SECRET");
    }

    @Test
    void acceptsDeviceEncryptionSecretWithAtLeast32Characters() {
        assertThatCode(() -> validate("test_device_key_encryption_secret_32_chars_minimum"))
                .doesNotThrowAnyException();
    }

    private void validate(String encryptionSecret) throws Exception {
        DeviceProperties deviceProperties = new DeviceProperties(encryptionSecret, 5, 10);
        CommandLineRunner runner = validator.validateSensitiveConfiguration(
                jwtProperties, initialAdminProperties, deviceProperties, environment);
        runner.run();
    }
}
