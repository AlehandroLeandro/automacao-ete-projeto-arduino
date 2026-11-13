package br.com.carbonifera.ete.notification.schedule;

import br.com.carbonifera.ete.config.SchedulingConfig;
import br.com.carbonifera.ete.notification.NotificationCommand;
import br.com.carbonifera.ete.notification.NotificationDispatcher;
import br.com.carbonifera.ete.notification.NotificationRecipientResolver;
import br.com.carbonifera.ete.notification.NotificationType;
import br.com.carbonifera.ete.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    private static final DateTimeFormatter HOURLY_SLOT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH");
    private static final DateTimeFormatter DATE_SLOT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter SHIFT_SLOT_FORMATTER = DateTimeFormatter.ofPattern("HHmm");

    private final NotificationDispatcher dispatcher;
    private final NotificationRecipientResolver resolver;
    private final Clock clock;

    public NotificationScheduler(
            NotificationDispatcher dispatcher,
            NotificationRecipientResolver resolver,
            Clock clock
    ) {
        this.dispatcher = dispatcher;
        this.resolver = resolver;
        this.clock = clock;
    }

    @Scheduled(cron = "0 55 * * * *", zone = "America/Sao_Paulo")
    void remindHourlyMeasurement() {
        dispatchForOperators(this::hourlyMeasurementCommand);
    }

    @Scheduled(cron = "0 50 4,16 * * *", zone = "America/Sao_Paulo")
    void remindCalMovement() {
        dispatchForOperators(this::calMovementCommand);
    }

    private void dispatchForOperators(NotificationCommandFactory commandFactory) {
        List<AppUser> operators;
        try {
            operators = resolver.activeOperators();
        } catch (RuntimeException ex) {
            log.error("[notifications] failed to resolve scheduled reminder recipients", ex);
            return;
        }

        ZonedDateTime runAt = now();
        for (AppUser operator : operators) {
            try {
                dispatcher.dispatch(commandFactory.create(operator, runAt));
            } catch (RuntimeException ex) {
                log.error("[notifications] failed to dispatch scheduled reminder to {}", operator.getUsername(), ex);
            }
        }
    }

    private NotificationCommand hourlyMeasurementCommand(AppUser operator, ZonedDateTime now) {
        String slot = now.format(HOURLY_SLOT_FORMATTER);
        return new NotificationCommand(
                NotificationType.HOURLY_MEASUREMENT_REMINDER,
                List.of(operator),
                null,
                "Coleta e aferições",
                "Não esqueça de fazer as aferições de pH e vazão",
                "/app/medicoes/ph",
                "HOURLY_MEASUREMENT_REMINDER|" + operator.getId() + "|" + slot,
                now.toInstant()
        );
    }

    private NotificationCommand calMovementCommand(AppUser operator, ZonedDateTime now) {
        String slot = now.format(DATE_SLOT_FORMATTER) + "|" + now.format(SHIFT_SLOT_FORMATTER);
        return new NotificationCommand(
                NotificationType.CAL_SHIFT_MOVEMENT_REMINDER,
                List.of(operator),
                null,
                "Movimento de Cal durante o turno",
                "Faça as movimentações de cal que ficaram pendentes",
                "/app/medicoes/cal",
                "CAL_SHIFT_MOVEMENT_REMINDER|" + operator.getId() + "|" + slot,
                now.toInstant()
        );
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(clock).withZoneSameInstant(SchedulingConfig.APP_ZONE);
    }

    @FunctionalInterface
    private interface NotificationCommandFactory {
        NotificationCommand create(AppUser operator, ZonedDateTime now);
    }
}
