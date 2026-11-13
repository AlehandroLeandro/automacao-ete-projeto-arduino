package br.com.carbonifera.ete.audit;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.device.DeviceKey;
import br.com.carbonifera.ete.device.DeviceKeyRepository;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.measurement.FlowMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhMeasurementRepository;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationScopeService;
import br.com.carbonifera.ete.stoppage.StoppageRepository;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class AuditServiceTest {

    @Test
    void shortDescriptionFailsWithoutSavingAuditEvent() {
        AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
        DeviceKeyRepository deviceKeyRepository = mock(DeviceKeyRepository.class);
        AuditService service = new AuditService(
                auditEventRepository,
                mock(PhMeasurementRepository.class),
                mock(FlowMeasurementRepository.class),
                mock(StoppageRepository.class),
                mock(StationScopeService.class),
                deviceKeyRepository
        );

        assertThatThrownBy(() -> service.recordDelete(
                AuditRecordType.PH_MEASUREMENT,
                UUID.randomUUID(),
                new Station("ITE A", "ite-a"),
                new AppUser("Operator", "operator", "hash", UserRole.OPERATOR, false),
                "curta"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Audit description must have between 10 and 500 characters");

        verify(auditEventRepository, never()).save(any());
    }

    @Test
    void historyReturnsEventsForExistingDeviceKey() {
        AuditEventRepository events = mock(AuditEventRepository.class);
        DeviceKeyRepository keys = mock(DeviceKeyRepository.class);
        Station station = new Station("ITE A", "ite-a");
        ReflectionTestUtils.setField(station, "id", UUID.randomUUID());
        AppUser admin = new AppUser("Admin", "admin", "hash", UserRole.ADMIN, false);
        ReflectionTestUtils.setField(admin, "id", UUID.randomUUID());
        DeviceKey key = new DeviceKey(station, "Sensor", "encrypted", "hash", admin);
        UUID keyId = UUID.randomUUID();
        ReflectionTestUtils.setField(key, "id", keyId);
        AuditEvent event = new AuditEvent(AuditRecordType.DEVICE_KEY, AuditAction.UPDATED, keyId,
                station, admin, "Device key updated");
        when(keys.findById(keyId)).thenReturn(Optional.of(key));
        when(events.findAllByRecordTypeAndRecordIdOrderByOccurredAtDesc(AuditRecordType.DEVICE_KEY, keyId))
                .thenReturn(List.of(event));
        AuditService service = service(events, keys);

        var history = service.history(AuditRecordType.DEVICE_KEY, keyId, UserPrincipal.from(admin));

        assertThat(history).hasSize(1);
        assertThat(history.get(0).recordType()).isEqualTo(AuditRecordType.DEVICE_KEY);
        assertThat(history.get(0).recordId()).isEqualTo(keyId);
    }

    @Test
    void historyReturnsNotFoundForDeletedDeviceKey() {
        AuditEventRepository events = mock(AuditEventRepository.class);
        DeviceKeyRepository keys = mock(DeviceKeyRepository.class);
        UUID keyId = UUID.randomUUID();
        when(keys.findById(keyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(events, keys).history(AuditRecordType.DEVICE_KEY, keyId, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Device key not found");
        verify(events, never()).findAllByRecordTypeAndRecordIdOrderByOccurredAtDesc(any(), any());
    }

    private AuditService service(AuditEventRepository events, DeviceKeyRepository keys) {
        return new AuditService(events, mock(PhMeasurementRepository.class), mock(FlowMeasurementRepository.class),
                mock(StoppageRepository.class), mock(StationScopeService.class), keys);
    }
}
