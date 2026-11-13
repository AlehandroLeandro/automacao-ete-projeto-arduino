package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.audit.AuditAction;
import br.com.carbonifera.ete.audit.AuditEvent;
import br.com.carbonifera.ete.audit.AuditEventRepository;
import br.com.carbonifera.ete.audit.AuditRecordType;
import br.com.carbonifera.ete.audit.AuditService;
import br.com.carbonifera.ete.config.DeviceProperties;
import br.com.carbonifera.ete.measurement.FlowMeasurement;
import br.com.carbonifera.ete.measurement.FlowMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhMeasurement;
import br.com.carbonifera.ete.measurement.PhMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhWaterType;
import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationScopeService;
import br.com.carbonifera.ete.station.StationService;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({DeviceKeyService.class, DeviceHourlyAggregator.class, AuditService.class,
        DeviceKeyLifecycleIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceKeyLifecycleIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-13T13:30:00Z");
    private static final Instant CLOSED = Instant.parse("2026-11-13T13:00:00Z");
    private static final Instant PARTIAL = Instant.parse("2026-11-13T14:00:00Z");
    @Autowired DeviceKeyService service;
    @Autowired DeviceKeyRepository keys;
    @Autowired DeviceReadingRepository readings;
    @Autowired FlowMeasurementRepository flows;
    @Autowired PhMeasurementRepository phs;
    @Autowired AuditEventRepository audits;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean StationService stationService;
    @MockBean StationScopeService stationScopeService;
    @MockBean CurrentUserService currentUserService;
    @MockBean DeviceKeyGenerator generator;
    private TransactionTemplate transaction;
    private DeviceKey key;
    private AppUser user;
    private UserPrincipal principal;
    private List<UUID> readingIds;

    @BeforeEach
    void seed() {
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            Station station = new Station("ETE " + suffix, "ete-" + suffix);
            user = new AppUser("Admin", "admin-" + suffix, "hash", UserRole.ADMIN, false);
            entityManager.persist(station);
            entityManager.persist(user);
            key = new DeviceKey(station, "Sensor " + suffix, "encrypted-secret", suffix, user);
            key.recordLiveFlow(new BigDecimal("20"), new BigDecimal("200"), NOW);
            key.recordLivePh(new BigDecimal("9"), new BigDecimal("9"), NOW);
            entityManager.persist(key);
            flows.save(FlowMeasurement.fromDevice(station, new BigDecimal("100"), new BigDecimal("10"),
                    CLOSED, key, key.getName(), CLOSED, 2));
            for (PhWaterType type : PhWaterType.values()) {
                phs.save(PhMeasurement.fromDevice(station, type, new BigDecimal("6"),
                        CLOSED, key, key.getName(), CLOSED, 2));
            }
            for (Instant at : List.of(CLOSED.minusSeconds(600), NOW.minusSeconds(60))) {
                readings.save(new DeviceReading(key, DeviceReadingType.FLOW, new BigDecimal("40"),
                        new BigDecimal("400"), at));
                readings.save(new DeviceReading(key, DeviceReadingType.PH_RAW, new BigDecimal("9"), null, at));
                readings.save(new DeviceReading(key, DeviceReadingType.PH_TREATED, new BigDecimal("9"), null, at));
            }
        });
        readingIds = readings.findAllByDeviceKeyOrderByMeasuredAtAsc(key).stream().map(DeviceReading::getId).toList();
        principal = UserPrincipal.from(user);
        when(currentUserService.requireUser(principal)).thenReturn(user);
    }

    @Test
    void deleteCommitsClosedAndPartialAveragesPreservesNamesAndRecordsAudit() {
        service.delete(key.getId(), principal);

        transaction.executeWithoutResult(status -> {
            assertThat(keys.findById(key.getId())).isEmpty();
            assertThat(readings.findAllByDeviceKeyOrderByMeasuredAtAsc(key)).isEmpty();
            var retainedFlows = retainedFlows();
            assertThat(retainedFlows).extracting(FlowMeasurement::getDeviceWindowEnd)
                    .containsExactlyInAnyOrder(CLOSED, PARTIAL);
            for (FlowMeasurement flow : retainedFlows) {
                boolean closed = flow.getDeviceWindowEnd().equals(CLOSED);
                assertThat(flow.getDeviceKey()).isNull();
                assertThat(flow.getDeviceName()).isEqualTo(key.getName());
                assertThat(flow.getSampleCount()).isEqualTo(closed ? 3 : 1);
                assertThat(flow.getFlowLiters()).isEqualByComparingTo(closed ? "200" : "400");
                assertThat(flow.getHeightCm()).isEqualByComparingTo(closed ? "20" : "40");
                assertThat(flow.getDeviceWindowEnd()).isIn(CLOSED, PARTIAL);
                assertThat(flow.getMeasuredAt()).isEqualTo(closed ? CLOSED : NOW);
            }
            var retainedPhs = retainedPhs();
            assertThat(retainedPhs).extracting(PhMeasurement::getType, PhMeasurement::getDeviceWindowEnd)
                    .containsExactlyInAnyOrder(tuple(PhWaterType.RAW, CLOSED), tuple(PhWaterType.RAW, PARTIAL),
                            tuple(PhWaterType.TREATED, CLOSED), tuple(PhWaterType.TREATED, PARTIAL));
            for (PhMeasurement ph : retainedPhs) {
                boolean closed = ph.getDeviceWindowEnd().equals(CLOSED);
                assertThat(ph.getDeviceKey()).isNull();
                assertThat(ph.getDeviceName()).isEqualTo(key.getName());
                assertThat(ph.getSampleCount()).isEqualTo(closed ? 3 : 1);
                assertThat(ph.getPh()).isEqualByComparingTo(closed ? "7" : "9");
                assertThat(ph.getDeviceWindowEnd()).isIn(CLOSED, PARTIAL);
                assertThat(ph.getMeasuredAt()).isEqualTo(closed ? CLOSED : NOW);
            }
            var events = audits.findAllByRecordTypeAndRecordIdOrderByOccurredAtDesc(AuditRecordType.DEVICE_KEY, key.getId());
            assertThat(events).hasSize(1);
            assertAudit(events.getFirst(), AuditAction.DELETED, "Device key deleted");
        });
    }

    @Test
    void failureAfterBothBulkUpdatesRollsBackAveragesReferencesAndRawDeletion() {
        when(currentUserService.requireUser(principal)).thenAnswer(invocation -> {
            // This callback runs after both real bulk updates, inside delete's transaction.
            entityManager.flush();
            assertThat(retainedFlows()).hasSize(2).allSatisfy(flow -> assertThat(flow.getDeviceKey()).isNull());
            assertThat(retainedPhs()).hasSize(4).allSatisfy(ph -> assertThat(ph.getDeviceKey()).isNull());
            assertThat(readings.findAllByDeviceKeyOrderByMeasuredAtAsc(key)).isEmpty();
            throw new IllegalStateException("failure after bulk updates");
        });

        assertThatThrownBy(() -> service.delete(key.getId(), principal))
                .isInstanceOf(IllegalStateException.class).hasMessage("failure after bulk updates");

        transaction.executeWithoutResult(status -> {
            DeviceKey retained = keys.findById(key.getId()).orElseThrow();
            assertThat(retained.getLastFlowLiters()).isEqualByComparingTo("200");
            assertThat(retained.getLastRawPh()).isEqualByComparingTo("9");
            assertThat(retained.getLastTreatedPh()).isEqualByComparingTo("9");
            assertThat(retained.getLastHeightCm()).isEqualByComparingTo("20");
            assertThat(readings.findAllByDeviceKeyOrderByMeasuredAtAsc(retained)).extracting(DeviceReading::getId)
                    .containsExactlyInAnyOrderElementsOf(readingIds);
            assertThat(retainedFlows()).hasSize(1).allSatisfy(flow -> {
                assertThat(flow.getDeviceKey().getId()).isEqualTo(key.getId());
                assertThat(flow.getDeviceName()).isEqualTo(key.getName());
                assertThat(flow.getFlowLiters()).isEqualByComparingTo("100");
                assertThat(flow.getHeightCm()).isEqualByComparingTo("10");
                assertThat(flow.getSampleCount()).isEqualTo(2);
            });
            assertThat(retainedPhs()).hasSize(2).allSatisfy(ph -> {
                assertThat(ph.getDeviceKey().getId()).isEqualTo(key.getId());
                assertThat(ph.getDeviceName()).isEqualTo(key.getName());
                assertThat(ph.getPh()).isEqualByComparingTo("6");
                assertThat(ph.getSampleCount()).isEqualTo(2);
            });
            assertThat(audits.findAllByRecordTypeAndRecordIdOrderByOccurredAtDesc(AuditRecordType.DEVICE_KEY, key.getId())).isEmpty();
        });
    }

    @Test
    void deactivateAndReactivateCommitStateAndAuditChanges() {
        service.setActive(key.getId(), false, principal);
        transaction.executeWithoutResult(status -> assertThat(keys.findById(key.getId()).orElseThrow().isActive()).isFalse());
        service.setActive(key.getId(), true, principal);
        transaction.executeWithoutResult(status -> {
            assertThat(keys.findById(key.getId()).orElseThrow().isActive()).isTrue();
            var events = audits.findAllByRecordTypeAndRecordIdOrderByOccurredAtDesc(AuditRecordType.DEVICE_KEY, key.getId());
            assertThat(events).extracting(AuditEvent::getDescription)
                    .containsExactlyInAnyOrder("Device key deactivated", "Device key reactivated");
            for (AuditEvent event : events) {
                boolean deactivated = event.getDescription().equals("Device key deactivated");
                assertAudit(event, AuditAction.UPDATED, deactivated ? "Device key deactivated" : "Device key reactivated");
                assertThat(event.getChanges()).hasSize(1);
                var change = event.getChanges().getFirst();
                assertThat(change.getName()).isEqualTo("active");
                assertThat(change.getBeforeValue()).isEqualTo(deactivated ? "true" : "false");
                assertThat(change.getAfterValue()).isEqualTo(deactivated ? "false" : "true");
            }
        });
    }

    private List<FlowMeasurement> retainedFlows() {
        return flows.findAll().stream().filter(flow -> key.getName().equals(flow.getDeviceName())).toList();
    }

    private List<PhMeasurement> retainedPhs() {
        return phs.findAll().stream().filter(ph -> key.getName().equals(ph.getDeviceName())).toList();
    }

    private void assertAudit(AuditEvent event, AuditAction action, String description) {
        assertThat(event.getRecordType()).isEqualTo(AuditRecordType.DEVICE_KEY);
        assertThat(event.getRecordId()).isEqualTo(key.getId());
        assertThat(event.getAction()).isEqualTo(action);
        assertThat(event.getStation().getId()).isEqualTo(key.getStation().getId());
        assertThat(event.getUser().getId()).isEqualTo(user.getId());
        assertThat(event.getDescription()).isEqualTo(description).hasSizeGreaterThanOrEqualTo(10)
                .doesNotContain(key.getEncryptedValue(), key.getValueHash());
    }

    @TestConfiguration
    static class Config {
        @Bean DeviceProperties deviceProperties() {
            return new DeviceProperties("test-secret-which-is-at-least-32-chars", 5, 10);
        }
        @Bean DeviceKeyCipher cipher(DeviceProperties properties) { return new DeviceKeyCipher(properties); }
        @Bean DeviceKeyHasher hasher() { return new DeviceKeyHasher(); }
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
