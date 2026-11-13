package br.com.carbonifera.ete.device;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.doThrow;

class DeviceHourlySchedulerTest {

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:30Z");

    private DeviceHourlyAggregator aggregator;
    private DeviceHourlyScheduler scheduler;

    @BeforeEach
    void setUp() {
        aggregator = mock(DeviceHourlyAggregator.class);
        scheduler = new DeviceHourlyScheduler(aggregator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void runAggregatesUsingClockInstant() {
        scheduler.run();

        verify(aggregator).aggregateClosedHours(NOW);
        verifyNoMoreInteractions(aggregator);
    }

    @Test
    void runContainsAggregatorException() {
        IllegalStateException failure = new IllegalStateException("aggregation failed");
        doThrow(failure)
                .when(aggregator).aggregateClosedHours(NOW);

        Logger logger = (Logger) LoggerFactory.getLogger(DeviceHourlyScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();
            verify(aggregator).aggregateClosedHours(NOW);
            verifyNoMoreInteractions(aggregator);
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).contains("failed to aggregate closed hours");
                assertThat(event.getThrowableProxy()).isInstanceOfSatisfying(ThrowableProxy.class,
                        throwable -> assertThat(throwable.getThrowable()).isSameAs(failure));
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void scheduledAnnotationUsesHourlyCronAndSaoPauloZone() throws NoSuchMethodException {
        Method run = DeviceHourlyScheduler.class.getMethod("run");
        Scheduled scheduled = run.getAnnotation(Scheduled.class);

        org.assertj.core.api.Assertions.assertThat(scheduled).isNotNull();
        org.assertj.core.api.Assertions.assertThat(scheduled.cron()).isEqualTo("30 0 * * * *");
        org.assertj.core.api.Assertions.assertThat(scheduled.zone()).isEqualTo("America/Sao_Paulo");
    }
}
