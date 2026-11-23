package br.com.ete.device;

import br.com.ete.config.JwtProperties;
import br.com.ete.security.JwtService;
import br.com.ete.station.Station;
import br.com.ete.user.AppUser;
import br.com.ete.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({DeviceAuthService.class, DeviceKeyHasher.class, JwtService.class, DeviceAuthServiceIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceAuthServiceIntegrationTest {
    private static final String KEY = "Abcdefghijklmnop12345678";
    private static final String IP = "192.0.2.100";
    @Autowired DeviceAuthService service;
    @Autowired DeviceKeyHasher hasher;
    @Autowired JwtService jwtService;
    @Autowired DeviceLoginThrottle throttle;
    @Autowired EntityManager entityManager;
    @SpyBean PlatformTransactionManager transactionManager;
    @SpyBean DataSource dataSource;
    private String loginKey;
    private UUID keyId;
    private UUID stationId;

    @BeforeEach
    void seed() {
        throttle.recordSuccess(IP);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            Station station = new Station("ETE " + suffix, "ete-" + suffix);
            AppUser user = new AppUser("Admin", "admin-" + suffix, "hash", UserRole.ADMIN, false);
            entityManager.persist(station);
            entityManager.persist(user);
            loginKey = suffix.replace("-", "").substring(0, 24);
            DeviceKey key = new DeviceKey(station, "Sensor", "encrypted", hasher.hash(loginKey), user);
            entityManager.persist(key);
            keyId = key.getId();
            stationId = station.getId();
        });
    }

    @Test
    void loginOutsideOpenSessionEmitsJwtWithLazyStationIdentity() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

        DeviceAuthResponse response = service.login(loginKey, IP);

        var claims = jwtService.parseClaims(response.accessToken());
        assertThat(claims.getSubject()).isEqualTo("device:" + keyId);
        assertThat(claims.get("role", String.class)).isEqualTo("DEVICE");
        assertThat(claims.get("deviceKeyId", String.class)).isEqualTo(keyId.toString());
        assertThat(claims.get("stationId", String.class)).isEqualTo(stationId.toString());
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(1200);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void blockedIpDoesNotBeginTransactionOrAcquireConnection() throws Exception {
        for (int i = 0; i < 10; i++) throttle.recordFailure(IP);
        clearInvocations(transactionManager, dataSource);

        assertThatThrownBy(() -> service.login(KEY, IP))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        verifyNoInteractions(transactionManager);
        verify(dataSource, never()).getConnection();
        verify(dataSource, never()).getConnection(any(), any());
    }

    @TestConfiguration
    static class Config {
        @Bean JwtProperties jwtProperties() {
            return new JwtProperties("jwt-secret-which-is-at-least-32-characters", 20, 7);
        }
        @Bean DeviceLoginThrottle throttle() { return new DeviceLoginThrottle(Clock.systemUTC()); }
    }
}
