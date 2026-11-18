package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.config.DeviceProperties;
import br.com.carbonifera.ete.notification.NotificationCommand;
import br.com.carbonifera.ete.notification.NotificationDispatcher;
import br.com.carbonifera.ete.notification.NotificationRecipientResolver;
import br.com.carbonifera.ete.notification.NotificationType;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DeviceOfflineMonitorTest {
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private final DeviceKeyRepository keys = mock(DeviceKeyRepository.class);
    private final NotificationRecipientResolver recipients = mock(NotificationRecipientResolver.class);
    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final Station station = new Station("Boa Vista", "boa-vista");
    private final AppUser user = mock(AppUser.class);
    private final DeviceKey key = new DeviceKey(station, "Sensor 1", "encrypted", "hash", user);
    private final UUID id = UUID.randomUUID();
    private DeviceOfflineMonitor monitor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(key, "id", id);
        when(keys.findMonitorCandidateIds()).thenReturn(List.of(id));
        when(keys.lockById(id)).thenReturn(Optional.of(key));
        monitor = new DeviceOfflineMonitor(keys, recipients, dispatcher, new DeviceProperties("secret", 5, 10),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void alertsOnceAndUsesStationRecipientsAndDeduplicationKey() {
        Instant seen = NOW.minusSeconds(301);
        key.recordSeen(seen);
        when(recipients.deviceAlertRecipients(station)).thenReturn(List.of(user));
        monitor.run();
        monitor.run();
        ArgumentCaptor<NotificationCommand> capture = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(dispatcher).dispatch(capture.capture());
        NotificationCommand command = capture.getValue();
        assertThat(command.type()).isEqualTo(NotificationType.DEVICE_OFFLINE_ALERT);
        assertThat(command.message()).contains("Sensor 1", "Boa Vista");
        assertThat(command.recipients()).containsExactly(user);
        assertThat(command.dedupeKey()).isEqualTo("DEVICE_OFFLINE|" + id + "|" + seen);
        assertThat(key.getOfflineNotifiedForSeenAt()).isEqualTo(seen);
    }

    @Test
    void returnNotifiesOnceAndAllowsAnotherOutage() {
        key.recordSeen(NOW.minusSeconds(600));
        monitor.run();
        key.recordSeen(NOW.minusSeconds(10));
        monitor.run();
        monitor.run();
        assertThat(key.getOfflineNotifiedForSeenAt()).isNull();
        monitor = new DeviceOfflineMonitor(keys, recipients, dispatcher, new DeviceProperties("secret", 5, 10),
                Clock.fixed(NOW.plusSeconds(360), ZoneOffset.UTC));
        monitor.run();
        ArgumentCaptor<NotificationCommand> capture = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(dispatcher, times(3)).dispatch(capture.capture());
        assertThat(capture.getAllValues()).extracting(NotificationCommand::type).containsExactly(
                NotificationType.DEVICE_OFFLINE_ALERT, NotificationType.DEVICE_BACK_ONLINE,
                NotificationType.DEVICE_OFFLINE_ALERT);
        verify(recipients, times(3)).deviceAlertRecipients(station);
    }

    @Test
    void inactiveNeverSeenAndExactCutoffDoNotAlert() {
        monitor.run();
        key.recordSeen(NOW.minusSeconds(301));
        key.deactivate();
        monitor.run();
        key.activate();
        key.recordSeen(NOW.minusSeconds(300));
        monitor.run();
        verifyNoInteractions(dispatcher, recipients);
    }

    @Test
    void scheduledEverySixtySeconds() throws Exception {
        assertThat(DeviceOfflineMonitor.class.getMethod("run").getAnnotation(Scheduled.class).fixedDelay())
                .isEqualTo(60000);
    }
}
