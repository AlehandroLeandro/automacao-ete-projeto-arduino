package br.com.ete.device;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceKeyGeneratorTest {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*-_=+?";

    private final DeviceKeyGenerator generator = new DeviceKeyGenerator();

    @Test
    void generatesExactlyTwentyFourCharacters() {
        assertThat(generator.generate()).hasSize(24);
    }

    @Test
    void usesOnlyAllowedCharacters() {
        assertThat(generator.generate().chars().allMatch(c -> ALPHABET.indexOf(c) >= 0)).isTrue();
    }

    @Test
    void includesEveryRequiredCharacterClassInOneThousandKeys() {
        for (int i = 0; i < 1000; i++) {
            String key = generator.generate();
            assertThat(key).matches(".*[A-Z].*")
                    .matches(".*[a-z].*")
                    .matches(".*[0-9].*")
                    .matches(".*[!@#$%^&*\\-_=?+].*");
        }
    }

    @Test
    void generatesOneThousandDistinctKeys() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            keys.add(generator.generate());
        }
        assertThat(keys).hasSize(1000);
    }
}
