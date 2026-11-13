package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.audit.AuditRecordType;
import br.com.carbonifera.ete.audit.AuditService;
import br.com.carbonifera.ete.config.DeviceProperties;
import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationService;
import br.com.carbonifera.ete.measurement.FlowMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhMeasurementRepository;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DeviceKeyServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final UUID STATION_ID = UUID.randomUUID();
    private static final UUID KEY_ID = UUID.randomUUID();

    @Mock DeviceKeyRepository repository;
    @Mock StationService stationService;
    @Mock CurrentUserService currentUserService;
    @Mock UserPrincipal principal;
    @Mock AuditService auditService;
    @Mock DeviceHourlyAggregator hourlyAggregator;
    @Mock FlowMeasurementRepository flowMeasurementRepository;
    @Mock PhMeasurementRepository phMeasurementRepository;

    private DeviceKeyGenerator generator;
    private DeviceKeyCipher cipher;
    private DeviceKeyHasher hasher;
    private DeviceKeyService service;
    private Station station;
    private AppUser user;

    @BeforeEach
    void setUp() {
        DeviceProperties properties = new DeviceProperties("test-secret-which-is-at-least-32-chars", 5, 10);
        generator = new DeviceKeyGenerator();
        cipher = new DeviceKeyCipher(properties);
        hasher = new DeviceKeyHasher();
        service = new DeviceKeyService(repository, generator, cipher, hasher, stationService,
                currentUserService, properties, Clock.fixed(NOW, ZoneOffset.UTC), auditService, hourlyAggregator,
                flowMeasurementRepository, phMeasurementRepository);
        station = new Station("ETE A", "ete-a");
        user = new AppUser("Admin", "admin", "hash", UserRole.ADMIN, false);
    }

    @Test
    void createsActiveKeyWithEncryptedValueAndMatchingHash() {
        stubCreate();
        DeviceKeyCreatedResponse result = service.create("Sensor", STATION_ID, principal);
        DeviceKey saved = repositorySavedKey();

        assertThat(result.key().active()).isTrue();
        assertThat(result.value()).hasSize(24);
        assertThat(saved.getEncryptedValue()).isNotEqualTo(result.value());
        assertThat(cipher.decrypt(saved.getEncryptedValue())).isEqualTo(result.value());
        assertThat(saved.getValueHash()).isEqualTo(hasher.hash(result.value()));
        assertThat(result.key().status()).isEqualTo(DeviceKeyStatus.NEVER);
    }

    @Test
    void rejectsCaseInsensitiveDuplicateNameWithConflict() {
        when(stationService.requireStation(STATION_ID)).thenReturn(station);
        when(repository.existsByStationAndNameKey(station, "sensor")).thenReturn(true);

        assertThatThrownBy(() -> service.create("SENSOR", STATION_ID, principal))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void allowsSameNameWhenRepositoryReportsNoDuplicateInOtherStation() {
        stubCreate();
        when(repository.existsByStationAndNameKey(station, "sensor")).thenReturn(false);

        assertThat(service.create("Sensor", STATION_ID, principal).key().name()).isEqualTo("Sensor");
    }

    @Test
    void listHasMetadataAndNeverContainsSecretValue() {
        DeviceKey key = key("stored-encrypted-secret", NOW.minusSeconds(120));
        when(repository.findAllByOrderByNameKeyAsc()).thenReturn(List.of(key));

        DeviceKeyResponse item = service.list().getFirst();

        assertThat(item).isEqualTo(DeviceKeyResponse.from(key, DeviceKeyStatus.ONLINE));
        assertThat(DeviceKeyResponse.class.getRecordComponents()).extracting(component -> component.getName())
                .containsExactly("id", "name", "stationId", "stationName", "active", "createdAt", "lastSeenAt", "status")
                .doesNotContain("value", "encryptedValue");
    }

    @Test
    void reportsOnlineAtThresholdOfflineAboveAndNeverWithoutSend() {
        when(repository.findAllByOrderByNameKeyAsc()).thenReturn(List.of(
                key("online", NOW.minusSeconds(300)), key("offline", NOW.minusSeconds(301)), key("never", null)));

        assertThat(service.list()).extracting(DeviceKeyResponse::status)
                .containsExactly(DeviceKeyStatus.ONLINE, DeviceKeyStatus.OFFLINE, DeviceKeyStatus.NEVER);
    }

    @Test
    void secretReturnsOriginalValueOnDemand() {
        String original = "stored-key-value";
        DeviceKey key = key(cipher.encrypt(original), null);
        when(repository.findById(KEY_ID)).thenReturn(Optional.of(key));

        assertThat(service.secret(KEY_ID).value()).isEqualTo(original);
    }

    @Test
    void decryptionFailureUsesGenericErrorWithoutSecretText() {
        DeviceKey key = key("invalid-ciphertext-that-is-never-returned", null);
        when(repository.findById(KEY_ID)).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> service.secret(KEY_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to retrieve device key")
                .hasNoCause();
    }

    @Test
    void missingSecretIdReturnsNotFound() {
        when(repository.findById(KEY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.secret(KEY_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivatesAndAuditsWithoutKeyMaterial() {
        DeviceKey key = key("secret-value", null);
        when(repository.findById(KEY_ID)).thenReturn(Optional.of(key));
        when(currentUserService.requireUser(principal)).thenReturn(user);

        service.setActive(KEY_ID, false, principal);

        assertThat(key.isActive()).isFalse();
        org.mockito.ArgumentCaptor<String> description = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(auditService).recordUpdate(org.mockito.ArgumentMatchers.eq(AuditRecordType.DEVICE_KEY),
                org.mockito.ArgumentMatchers.eq(KEY_ID), org.mockito.ArgumentMatchers.eq(station),
                org.mockito.ArgumentMatchers.eq(user), description.capture(), any());
        assertThat(description.getValue()).hasSizeGreaterThanOrEqualTo(10)
                .doesNotContain("secret-value", key.getValueHash());
    }

    @Test
    void reactivatesAndAudits() {
        DeviceKey key = key("secret-value", null);
        key.deactivate();
        when(repository.findById(KEY_ID)).thenReturn(Optional.of(key));
        when(currentUserService.requireUser(principal)).thenReturn(user);

        service.setActive(KEY_ID, true, principal);

        assertThat(key.isActive()).isTrue();
        verify(auditService).recordUpdate(org.mockito.ArgumentMatchers.eq(AuditRecordType.DEVICE_KEY),
                org.mockito.ArgumentMatchers.eq(KEY_ID), org.mockito.ArgumentMatchers.eq(station),
                org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.contains("reactivated"), any());
    }

    @Test
    void missingKeyCannotBeActivated() {
        when(repository.findById(KEY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive(KEY_ID, true, principal)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void missingKeyCannotBeDeactivated() {
        when(repository.findById(KEY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive(KEY_ID, false, principal)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteAggregatesDetachesAuditsThenDeletesKey() {
        DeviceKey key = key("secret-value", null);
        when(repository.lockById(KEY_ID)).thenReturn(Optional.of(key));
        when(currentUserService.requireUser(principal)).thenReturn(user);

        service.delete(KEY_ID, principal);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(hourlyAggregator, flowMeasurementRepository,
                phMeasurementRepository, auditService, repository);
        order.verify(hourlyAggregator).aggregateKeyIncludingPartial(key, NOW);
        order.verify(flowMeasurementRepository).clearDeviceKey(key);
        order.verify(phMeasurementRepository).clearDeviceKey(key);
        order.verify(auditService).recordDelete(AuditRecordType.DEVICE_KEY, KEY_ID, station, user, "Device key deleted");
        order.verify(repository).delete(key);
        assertThat(key.getName()).isEqualTo("Sensor");
        assertThat(key.getEncryptedValue()).isEqualTo("secret-value");
    }

    @Test
    void deleteAuditDescriptionDoesNotContainKeyValueOrHash() {
        DeviceKey key = key("secret-value", null);
        when(repository.lockById(KEY_ID)).thenReturn(Optional.of(key));
        when(currentUserService.requireUser(principal)).thenReturn(user);

        service.delete(KEY_ID, principal);

        verify(auditService).recordDelete(AuditRecordType.DEVICE_KEY, KEY_ID, station, user, "Device key deleted");
    }

    @Test
    void missingKeyCannotBeDeleted() {
        when(repository.lockById(KEY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(KEY_ID, principal)).isInstanceOf(ResourceNotFoundException.class);
        org.mockito.Mockito.verifyNoInteractions(hourlyAggregator, flowMeasurementRepository,
                phMeasurementRepository, auditService);
    }

    @Test
    void deleteRunsInTransaction() throws NoSuchMethodException {
        var method = DeviceKeyService.class.getMethod("delete", UUID.class, UserPrincipal.class);

        assertThat(method.getAnnotation(org.springframework.transaction.annotation.Transactional.class)).isNotNull();
    }

    private DeviceKey repositorySavedKey() {
        org.mockito.ArgumentCaptor<DeviceKey> captor = org.mockito.ArgumentCaptor.forClass(DeviceKey.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private void stubCreate() {
        when(stationService.requireStation(STATION_ID)).thenReturn(station);
        when(currentUserService.requireUser(principal)).thenReturn(user);
        when(repository.save(any(DeviceKey.class))).thenAnswer(invocation -> {
            DeviceKey key = invocation.getArgument(0);
            key.prePersist();
            return key;
        });
    }

    private DeviceKey key(String encryptedValue, Instant lastSeenAt) {
        DeviceKey key = new DeviceKey(station, "Sensor", encryptedValue, "hash", user);
        key.prePersist();
        if (lastSeenAt != null) key.recordSeen(lastSeenAt);
        setId(key);
        return key;
    }

    private void setId(DeviceKey key) {
        try {
            var field = DeviceKey.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(key, KEY_ID);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
