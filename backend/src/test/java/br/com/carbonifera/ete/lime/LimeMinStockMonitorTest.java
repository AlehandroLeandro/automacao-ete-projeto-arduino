package br.com.carbonifera.ete.lime;

import br.com.carbonifera.ete.notification.NotificationCommand;
import br.com.carbonifera.ete.notification.NotificationDispatcher;
import br.com.carbonifera.ete.notification.NotificationRecipientResolver;
import br.com.carbonifera.ete.notification.NotificationType;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LimeMinStockMonitorTest {

    private LimeRecordRepository repository;
    private NotificationDispatcher dispatcher;
    private NotificationRecipientResolver resolver;
    private LimeMinStockMonitor monitor;
    private Station station;
    private AppUser user;

    @BeforeEach
    void setUp() {
        repository = mock(LimeRecordRepository.class);
        dispatcher = mock(NotificationDispatcher.class);
        resolver = mock(NotificationRecipientResolver.class);
        monitor = new LimeMinStockMonitor(
                repository,
                new LimeStockCalculator(),
                dispatcher,
                resolver,
                Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC)
        );
        station = station(UUID.fromString("33333333-3333-3333-3333-333333333333"), "ITE A");
        user = user();
        when(resolver.activeSupervisors()).thenReturn(List.of(user));
    }

    @Test
    void evaluateDispatchesAlertWhenStockCrossesToMinimumAndMarksStationAsAlerted() {
        station.updateLimeThreshold(new BigDecimal("10"));
        stock("30", "20");

        monitor.evaluate(station);

        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(dispatcher).dispatch(captor.capture());
        NotificationCommand command = captor.getValue();
        assertThat(command.type()).isEqualTo(NotificationType.LIME_MIN_STOCK_ALERT);
        assertThat(command.recipients()).containsExactly(user);
        assertThat(command.station()).isEqualTo(station);
        assertThat(command.title()).isEqualTo("Estoque mínimo de Cal na ETE ITE A");
        assertThat(command.message()).isEqualTo("Estoque atual de 10 bags");
        assertThat(command.targetUrl()).isEqualTo("/app/dashboard?station=33333333-3333-3333-3333-333333333333");
        assertThat(command.dedupeKey()).isNull();
        assertThat(command.scheduledAt()).isEqualTo(Instant.parse("2026-07-10T12:00:00Z"));
        assertThat(station.isLimeMinStockAlerted()).isTrue();
    }

    @Test
    void evaluateDoesNotDispatchAgainWhileStockRemainsBelowMinimum() {
        station.updateLimeThreshold(new BigDecimal("10"));
        station.setLimeMinStockAlerted(true);
        stock("30", "25");

        monitor.evaluate(station);

        verify(dispatcher, never()).dispatch(org.mockito.ArgumentMatchers.any());
        assertThat(station.isLimeMinStockAlerted()).isTrue();
    }

    @Test
    void evaluateRearmsWithoutDispatchWhenStockReturnsAboveMinimum() {
        station.updateLimeThreshold(new BigDecimal("10"));
        station.setLimeMinStockAlerted(true);
        stock("30", "5");

        monitor.evaluate(station);

        verify(dispatcher, never()).dispatch(org.mockito.ArgumentMatchers.any());
        assertThat(station.isLimeMinStockAlerted()).isFalse();
    }

    @Test
    void evaluateDispatchesAgainAfterStockWasRearmedAndCrossesDownAgain() {
        station.updateLimeThreshold(new BigDecimal("10"));
        stock("30", "25");
        monitor.evaluate(station);
        station.setLimeMinStockAlerted(false);
        stock("30", "20");

        monitor.evaluate(station);

        verify(dispatcher, org.mockito.Mockito.times(2)).dispatch(org.mockito.ArgumentMatchers.any());
        assertThat(station.isLimeMinStockAlerted()).isTrue();
    }

    @Test
    void evaluateDoesNotDispatchWhenStationHasNoMinimumConfigured() {
        stock("30", "30");

        monitor.evaluate(station);

        verify(dispatcher, never()).dispatch(org.mockito.ArgumentMatchers.any());
        assertThat(station.isLimeMinStockAlerted()).isFalse();
    }

    private void stock(String entry, String consumption) {
        when(repository.findAllByStationAndRecordedAtLessThanEqualOrderByRecordedAtAsc(
                org.mockito.ArgumentMatchers.eq(station),
                org.mockito.ArgumentMatchers.any(Instant.class)
        )).thenReturn(List.of(
                limeRecord(LimeRecordType.ENTRY, entry, Instant.parse("2026-07-10T08:00:00Z")),
                limeRecord(LimeRecordType.CONSUMPTION, consumption, Instant.parse("2026-07-10T09:00:00Z"))
        ));
    }

    private LimeRecord limeRecord(LimeRecordType type, String quantityBags, Instant recordedAt) {
        LimeRecord record = new LimeRecord(station, type, new BigDecimal(quantityBags), recordedAt, null, user);
        ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
        return record;
    }

    private Station station(UUID stationId, String name) {
        Station station = new Station(name, name.toLowerCase().replace(" ", "-"));
        ReflectionTestUtils.setField(station, "id", stationId);
        return station;
    }

    private AppUser user() {
        AppUser user = new AppUser("Supervisor", "supervisor", "hash", UserRole.SUPERVISOR, null, false);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
