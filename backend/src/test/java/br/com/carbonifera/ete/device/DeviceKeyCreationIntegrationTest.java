package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.config.DeviceProperties;
import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationService;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({DeviceKeyService.class, DeviceKeyCreationIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceKeyCreationIntegrationTest {
    @Autowired DeviceKeyService service;
    @Autowired DeviceKeyRepository repository;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean StationService stationService;
    @MockBean CurrentUserService currentUserService;
    @MockBean DeviceKeyGenerator generator;
    private Station station;
    private Station otherStation;
    private UserPrincipal principal;

    @BeforeEach
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            station = new Station("ETE A " + suffix, "ete-a-" + suffix);
            otherStation = new Station("ETE B " + suffix, "ete-b-" + suffix);
            AppUser user = new AppUser("Admin", "admin-" + suffix, "hash", UserRole.ADMIN, false);
            entityManager.persist(station);
            entityManager.persist(otherStation);
            entityManager.persist(user);
            entityManager.flush();
            principal = UserPrincipal.from(user);
            when(currentUserService.requireUser(principal)).thenReturn(user);
        });
        when(stationService.requireStation(station.getId())).thenReturn(station);
        when(stationService.requireStation(otherStation.getId())).thenReturn(otherStation);
        when(generator.generate()).thenAnswer(invocation -> UUID.randomUUID().toString());
    }

    @Test
    void concurrentCaseInsensitiveNamesCommitOneKeyAndRollbackConflict() throws Exception {
        CyclicBarrier afterBothPrechecks = new CyclicBarrier(2);
        when(generator.generate()).thenAnswer(invocation -> {
            afterBothPrechecks.await(10, TimeUnit.SECONDS);
            return UUID.randomUUID().toString();
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> createOutcome("Sensor"));
            var second = executor.submit(() -> createOutcome("SENSOR"));
            List<Object> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(DeviceKeyCreatedResponse.class::isInstance)).hasSize(1);
            assertThat(outcomes.stream().filter(BusinessException.class::isInstance)
                    .map(BusinessException.class::cast).map(BusinessException::getStatus))
                    .containsExactly(HttpStatus.CONFLICT);
        }
        assertThat(repository.findAll().stream().filter(key -> key.getStation().getId().equals(station.getId())))
                .hasSize(1);
        // A fresh transaction still succeeds after the losing transaction was rolled back.
        doReturn(UUID.randomUUID().toString()).when(generator).generate();
        assertThat(service.create("Another", station.getId(), principal).key().name()).isEqualTo("Another");
        assertThat(repository.findAll().stream().filter(key -> key.getStation().getId().equals(station.getId())))
                .hasSize(2);
    }

    @Test
    void sameNameInDifferentStationsCommitsBothKeys() {
        DeviceKeyCreatedResponse first = service.create("Sensor", station.getId(), principal);
        DeviceKeyCreatedResponse second = service.create("SENSOR", otherStation.getId(), principal);

        assertThat(repository.findById(first.key().id())).isPresent();
        assertThat(repository.findById(second.key().id())).isPresent();
        assertThat(first.key().stationId()).isEqualTo(station.getId());
        assertThat(second.key().stationId()).isEqualTo(otherStation.getId());
    }

    @Test
    void hashConstraintViolationIsNotReportedAsNameConflictAndRollsBack() {
        when(generator.generate()).thenReturn(UUID.randomUUID().toString());
        DeviceKeyCreatedResponse first = service.create("First", station.getId(), principal);

        assertThatThrownBy(() -> service.create("Different", station.getId(), principal))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(BusinessException.class);
        assertThat(repository.findAll().stream().filter(key -> key.getStation().getId().equals(station.getId())))
                .extracting(DeviceKey::getId).containsExactly(first.key().id());
    }

    private Object createOutcome(String name) {
        try {
            return service.create(name, station.getId(), principal);
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean DeviceProperties deviceProperties() {
            return new DeviceProperties("test-secret-which-is-at-least-32-chars", 5, 10);
        }
        @Bean DeviceKeyCipher cipher(DeviceProperties properties) { return new DeviceKeyCipher(properties); }
        @Bean DeviceKeyHasher hasher() { return new DeviceKeyHasher(); }
        @Bean Clock clock() { return Clock.systemUTC(); }
    }
}
