package br.com.carbonifera.ete.stoppage;

import br.com.carbonifera.ete.audit.AuditService;
import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.notification.NotificationDispatcher;
import br.com.carbonifera.ete.notification.NotificationRecipientResolver;
import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationScopeService;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoppageConcurrencyTest {

    @Test
    void concurrentStartsTranslateConstraintViolationToBusinessException() throws Exception {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService scope = mock(StationScopeService.class);
        CurrentUserService users = mock(CurrentUserService.class);
        Station station = new Station("ITE A", "ite-a");
        ReflectionTestUtils.setField(station, "id", UUID.randomUUID());
        AppUser user = new AppUser("Operator", "operator", "password", UserRole.OPERATOR, station, false);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        UserPrincipal principal = UserPrincipal.from(user);
        StoppageStartRequest request = new StoppageStartRequest(station.getId(), StoppageReason.FALTA_DE_AGUA, null, Instant.parse("2026-07-01T10:00:00Z"));
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean firstSave = new AtomicBoolean(true);
        when(scope.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(users.requireUser(principal)).thenReturn(user);
        when(repository.findFirstByStationAndStatusAndDeletedFalseOrderByStartAtDesc(station, StoppageStatus.ACTIVE)).thenReturn(Optional.empty());
        when(repository.save(any(Stoppage.class))).thenAnswer(invocation -> {
            if (!firstSave.getAndSet(false)) throw new org.springframework.dao.DataIntegrityViolationException("duplicate");
            return invocation.getArgument(0);
        });
        StoppageService service = new StoppageService(repository, scope, users, mock(ShiftCalculator.class), mock(AuditService.class), mock(NotificationDispatcher.class), mock(NotificationRecipientResolver.class), Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> { await(start); return service.start(request, principal); });
            Future<?> second = executor.submit(() -> { await(start); return service.start(request, principal); });
            start.countDown();
            int successes = 0;
            int businessErrors = 0;
            for (Future<?> future : new Future<?>[]{first, second}) {
                try { future.get(); successes++; } catch (Exception exception) {
                    if (exception.getCause() instanceof BusinessException && exception.getCause().getMessage().equals("Station already has an active stoppage")) businessErrors++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(businessErrors).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
}
