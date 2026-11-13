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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
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
    @Mock PlatformTransactionManager transactionManager;
    private DeviceKeyHasher hasher;
    private DeviceLoginThrottle throttle;
    private DeviceAuthService service;
    private DeviceKey activeKey;

    @BeforeEach
    void setUp() {
        hasher = new DeviceKeyHasher();
        throttle = new DeviceLoginThrottle(Clock.fixed(NOW, ZoneOffset.UTC));
        service = new DeviceAuthService(repository, hasher, jwtService, throttle,
                new JwtProperties("jwt-secret-which-is-at-least-32-characters", 20, 7), transactionManager);
        lenient().when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
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
        verifyNoInteractions(repository, transactionManager);
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
        verifyNoInteractions(repository, transactionManager);
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
        var outcomes = new ArrayList<Future<HttpStatus>>();
        try {
            for (int i = 0; i < 30; i++) {
                outcomes.add(executor.submit(() -> {
                    try {
                        service.login(VALID_KEY, IP);
                        return HttpStatus.OK;
                    } catch (BadCredentialsException error) {
                        return HttpStatus.UNAUTHORIZED;
                    } catch (ResponseStatusException error) {
                        return HttpStatus.valueOf(error.getStatusCode().value());
                    }
                }));
            }
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        var statuses = new ArrayList<HttpStatus>();
        for (Future<HttpStatus> outcome : outcomes) statuses.add(outcome.get(5, TimeUnit.SECONDS));
        assertThat(statuses).hasSize(30);
        assertThat(statuses.stream().filter(status -> status == HttpStatus.UNAUTHORIZED)).hasSize(10);
        assertThat(statuses.stream().filter(status -> status == HttpStatus.TOO_MANY_REQUESTS)).hasSize(20);
        assertThat(throttle.isBlocked(IP)).isTrue();
        verify(repository, times(10)).findByValueHash(hasher.hash(VALID_KEY));
    }

    @Test
    void pendingSuccessFinishesBeforeTenConcurrentFailuresEstablishANewBlock() throws Exception {
        for (int i = 0; i < 9; i++) throttle.recordFailure(IP);
        CountDownLatch successInIo = new CountDownLatch(1);
        CountDownLatch releaseSuccess = new CountDownLatch(1);
        CountDownLatch failuresStarted = new CountDownLatch(10);
        CountDownLatch failureCompleted = new CountDownLatch(1);
        when(repository.findByValueHash(hasher.hash(VALID_KEY))).thenReturn(Optional.of(activeKey));
        when(jwtService.generateDeviceAccessToken(activeKey)).thenAnswer(invocation -> {
            successInIo.countDown();
            assertThat(releaseSuccess.await(5, TimeUnit.SECONDS)).isTrue();
            return "pending-jwt";
        });
        var executor = Executors.newFixedThreadPool(11);
        try {
            Future<DeviceAuthResponse> success = executor.submit(() -> service.login(VALID_KEY, IP));
            assertThat(successInIo.await(5, TimeUnit.SECONDS)).isTrue();
            var failures = new ArrayList<Future<?>>();
            for (int i = 0; i < 10; i++) {
                failures.add(executor.submit(() -> {
                    failuresStarted.countDown();
                    try {
                        assertThatThrownBy(() -> service.login("short", IP))
                                .isExactlyInstanceOf(BadCredentialsException.class)
                                .hasMessage("Credenciais inválidas");
                    } finally {
                        failureCompleted.countDown();
                    }
                }));
            }
            assertThat(failuresStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(failureCompleted.await(250, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(throttle.isBlocked(IP)).isFalse();
            assertThat(failures).allSatisfy(future -> assertThat(future.isDone()).isFalse());
            releaseSuccess.countDown();
            assertThat(success.get(5, TimeUnit.SECONDS))
                    .isEqualTo(new DeviceAuthResponse("pending-jwt", "Bearer", 1200));
            for (Future<?> failure : failures) failure.get(5, TimeUnit.SECONDS);
            assertThat(throttle.isBlocked(IP)).isTrue();
            assertThatThrownBy(() -> service.login(VALID_KEY, IP))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        } finally {
            releaseSuccess.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void differentIpCanFinishLoginWhileFirstIpWaitsForDatabaseIo() throws Exception {
        String otherKey = "Zbcdefghijklmnop12345678";
        String otherIp = "192.0.2.11"; // Different lock stripe from 192.0.2.10.
        CountDownLatch firstInIo = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        when(repository.findByValueHash(hasher.hash(VALID_KEY))).thenAnswer(invocation -> {
            firstInIo.countDown();
            assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
            return Optional.empty();
        });
        when(repository.findByValueHash(hasher.hash(otherKey))).thenReturn(Optional.of(activeKey));
        when(jwtService.generateDeviceAccessToken(activeKey)).thenReturn("other-ip-jwt");
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> assertThatThrownBy(() -> service.login(VALID_KEY, IP))
                    .isExactlyInstanceOf(BadCredentialsException.class));
            assertThat(firstInIo.await(5, TimeUnit.SECONDS)).isTrue();
            Future<DeviceAuthResponse> second = executor.submit(() -> service.login(otherKey, otherIp));
            assertThat(second.get(2, TimeUnit.SECONDS))
                    .isEqualTo(new DeviceAuthResponse("other-ip-jwt", "Bearer", 1200));
            assertThat(first.isDone()).isFalse();
            assertThat(throttle.isBlocked(otherIp)).isFalse();
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertGenericFailure() {
        assertThatThrownBy(() -> service.login(VALID_KEY, IP))
                .isExactlyInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciais inválidas");
    }
}
