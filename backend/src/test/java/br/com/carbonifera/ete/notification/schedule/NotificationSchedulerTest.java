package br.com.carbonifera.ete.notification.schedule;

import br.com.carbonifera.ete.config.SchedulingConfig;
import br.com.carbonifera.ete.notification.NotificationCommand;
import br.com.carbonifera.ete.notification.NotificationDispatcher;
import br.com.carbonifera.ete.notification.NotificationRecipientResolver;
import br.com.carbonifera.ete.notification.NotificationType;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSchedulerTest {

    private NotificationDispatcher dispatcher;
    private NotificationRecipientResolver resolver;
    private NotificationScheduler scheduler;
    private AppUser firstOperator;
    private AppUser secondOperator;

    @BeforeEach
    void setUp() {
        dispatcher = mock(NotificationDispatcher.class);
        resolver = mock(NotificationRecipientResolver.class);
        scheduler = new NotificationScheduler(
                dispatcher,
                resolver,
                Clock.fixed(Instant.parse("2026-07-10T07:55:00Z"), SchedulingConfig.APP_ZONE)
        );
        firstOperator = operator(UUID.fromString("11111111-1111-1111-1111-111111111111"), "first");
        secondOperator = operator(UUID.fromString("22222222-2222-2222-2222-222222222222"), "second");
        when(resolver.activeOperators()).thenReturn(List.of(firstOperator, secondOperator));
    }

    @Test
    void remindHourlyMeasurementDispatchesOneReminderPerActiveOperatorWithSlotDedupeKey() {
        scheduler.remindHourlyMeasurement();

        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(dispatcher, org.mockito.Mockito.times(2)).dispatch(captor.capture());

        assertThat(captor.getAllValues()).allSatisfy(command -> {
            assertThat(command.type()).isEqualTo(NotificationType.HOURLY_MEASUREMENT_REMINDER);
            assertThat(command.station()).isNull();
            assertThat(command.title()).isEqualTo("Coleta e aferições");
            assertThat(command.message()).isEqualTo("Não esqueça de fazer as aferições de pH e vazão");
            assertThat(command.targetUrl()).isEqualTo("/app/medicoes/ph");
            assertThat(command.scheduledAt()).isEqualTo(Instant.parse("2026-07-10T07:55:00Z"));
        });
        assertThat(captor.getAllValues())
                .extracting(NotificationCommand::dedupeKey)
                .containsExactly(
                        "HOURLY_MEASUREMENT_REMINDER|11111111-1111-1111-1111-111111111111|2026-07-10T04",
                        "HOURLY_MEASUREMENT_REMINDER|22222222-2222-2222-2222-222222222222|2026-07-10T04"
                );
    }

    @Test
    void remindCalMovementDispatchesOneReminderPerActiveOperatorWithShiftSlotDedupeKey() {
        scheduler = new NotificationScheduler(
                dispatcher,
                resolver,
                Clock.fixed(Instant.parse("2026-07-10T19:50:00Z"), SchedulingConfig.APP_ZONE)
        );

        scheduler.remindCalMovement();

        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(dispatcher, org.mockito.Mockito.times(2)).dispatch(captor.capture());

        assertThat(captor.getAllValues()).allSatisfy(command -> {
            assertThat(command.type()).isEqualTo(NotificationType.CAL_SHIFT_MOVEMENT_REMINDER);
            assertThat(command.station()).isNull();
            assertThat(command.title()).isEqualTo("Movimento de Cal durante o turno");
            assertThat(command.message()).isEqualTo("Faça as movimentações de cal que ficaram pendentes");
            assertThat(command.targetUrl()).isEqualTo("/app/medicoes/cal");
            assertThat(command.scheduledAt()).isEqualTo(Instant.parse("2026-07-10T19:50:00Z"));
        });
        assertThat(captor.getAllValues())
                .extracting(NotificationCommand::dedupeKey)
                .containsExactly(
                        "CAL_SHIFT_MOVEMENT_REMINDER|11111111-1111-1111-1111-111111111111|2026-07-10|1650",
                        "CAL_SHIFT_MOVEMENT_REMINDER|22222222-2222-2222-2222-222222222222|2026-07-10|1650"
                );
    }

    @Test
    void schedulerContinuesDispatchingRemainingOperatorsWhenOneDispatchFails() {
        doThrow(new RuntimeException("push unavailable"))
                .when(dispatcher)
                .dispatch(org.mockito.ArgumentMatchers.argThat(command -> command.recipients().contains(firstOperator)));

        scheduler.remindHourlyMeasurement();

        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(dispatcher, org.mockito.Mockito.times(2)).dispatch(captor.capture());
        assertThat(captor.getAllValues())
                .flatExtracting(NotificationCommand::recipients)
                .containsExactly(firstOperator, secondOperator);
    }

    private AppUser operator(UUID id, String username) {
        AppUser user = new AppUser(username, username, "hash", UserRole.OPERATOR, null, false);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
