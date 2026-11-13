package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.config.JwtProperties;
import br.com.carbonifera.ete.security.JwtService;
import br.com.carbonifera.ete.station.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceAuthServiceTest {
    private static final String IP = "192.0.2.10";
    private static final String VALID_KEY = "Abcdefghijklmnop12345678";
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    @Mock DeviceKeyRepository repository;
    @Mock JwtService jwtService;
    private DeviceKeyHasher hasher;
    private DeviceLoginThrottle throttle;
    private DeviceAuthService service;
    private DeviceKey activeKey;

    @BeforeEach
    void setUp() {
        hasher = new DeviceKeyHasher();
        throttle = new DeviceLoginThrottle(Clock.fixed(NOW, ZoneOffset.UTC));
        service = new DeviceAuthService(repository, hasher, jwtService, throttle,
                new JwtProperties("jwt-secret-which-is-at-least-32-characters", 20, 7));
        activeKey = new DeviceKey(new Station("ETE", "ete"), "Sensor", "encrypted", hasher.hash(VALID_KEY), null);
    }

    @Test
    void validActiveKeyReturnsDeviceAccessTokenAndConfiguredExpiryWithoutRefreshToken() {
        when(repository.findByValueHash(hasher.hash(VALID_KEY))).thenReturn(Optional.of(activeKey));
        when(jwtService.generateDeviceAccessToken(activeKey)).thenReturn("device-jwt");

        DeviceAuthResponse response = service.login(VALID_KEY, IP);

        assertThat(response).isEqualTo(new DeviceAuthResponse("device-jwt", "Bearer", 1200));
        verify(jwtService).generateDeviceAccessToken(activeKey);
        verify(repository).findByValueHash(hasher.hash(VALID_KEY));
    }

    @Test
    void nullOrWrongLengthKeysShareGenericAuthenticationFailureAndCountFailures() {
        for (String malformed : new String[]{null, "too-short"}) {
            assertThatThrownBy(() -> service.login(malformed, IP))
                    .isExactlyInstanceOf(BadCredentialsException.class)
                    .hasMessage("Credenciais inválidas");
        }

        for (int i = 0; i < 8; i++) {
            assertThatThrownBy(() -> service.login("short", IP))
                    .isExactlyInstanceOf(BadCredentialsException.class)
                    .hasMessage("Credenciais inválidas");
        }
        assertThat(throttle.isBlocked(IP)).isTrue();
        verifyNoInteractions(repository);
    }

    @Test
    void unknownAndInactiveKeysShareGenericAuthenticationFailure() {
        activeKey.deactivate();
        when(repository.findByValueHash(hasher.hash(VALID_KEY)))
                .thenReturn(Optional.empty(), Optional.of(activeKey));
        assertGenericFailure();
        assertGenericFailure();
    }

    @Test
    void blockedIpGetsTooManyRequestsWithoutDatabaseLookup() {
        for (int i = 0; i < 10; i++) throttle.recordFailure(IP);

        assertThatThrownBy(() -> service.login(VALID_KEY, IP))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verifyNoInteractions(repository);
    }

    @Test
    void successfulLoginClearsPriorFailures() {
        for (int i = 0; i < 9; i++) throttle.recordFailure(IP);
        when(repository.findByValueHash(hasher.hash(VALID_KEY))).thenReturn(Optional.of(activeKey));
        when(jwtService.generateDeviceAccessToken(activeKey)).thenReturn("device-jwt");

        service.login(VALID_KEY, IP);
        for (int i = 0; i < 9; i++) throttle.recordFailure(IP);

        assertThat(throttle.isBlocked(IP)).isFalse();
    }

    @Test
    void concurrentFailuresForOneIpCannotExceedThresholdBeforeBlocking() throws Exception {
        when(repository.findByValueHash(hasher.hash(VALID_KEY))).thenReturn(Optional.empty());
        var executor = Executors.newFixedThreadPool(12);
        try {
            for (int i = 0; i < 30; i++) {
                executor.submit(() -> {
                    try {
                        service.login(VALID_KEY, IP);
                    } catch (BadCredentialsException | ResponseStatusException ignored) {
                        // Expected for invalid credentials and subsequent blocked requests.
                    }
                });
            }
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(throttle.isBlocked(IP)).isTrue();
        verify(repository, times(10)).findByValueHash(hasher.hash(VALID_KEY));
    }

    private void assertGenericFailure() {
        assertThatThrownBy(() -> service.login(VALID_KEY, IP))
                .isExactlyInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciais inválidas");
    }
}
