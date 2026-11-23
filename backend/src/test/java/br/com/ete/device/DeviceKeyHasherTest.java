package br.com.ete.device;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceKeyHasherTest {
    private final DeviceKeyHasher hasher = new DeviceKeyHasher();

    @Test
    void hashesAbcToKnownSha256Vector() {
        assertThat(hasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sameInputHasSameHashAndDifferentInputsHaveDifferentHashes() {
        assertThat(hasher.hash("device-key")).isEqualTo(hasher.hash("device-key"));
        assertThat(hasher.hash("device-key")).isNotEqualTo(hasher.hash("another-device-key"));
    }

    @Test
    void returnsSixtyFourLowercaseHexCharacters() {
        assertThat(hasher.hash("device-key")).matches("[0-9a-f]{64}");
    }
}
