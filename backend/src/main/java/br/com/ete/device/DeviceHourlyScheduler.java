package br.com.ete.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class DeviceHourlyScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeviceHourlyScheduler.class);

    private final DeviceHourlyAggregator aggregator;
    private final Clock clock;

    public DeviceHourlyScheduler(DeviceHourlyAggregator aggregator, Clock clock) {
        this.aggregator = aggregator;
        this.clock = clock;
    }

    @Scheduled(cron = "30 0 * * * *", zone = "America/Sao_Paulo")
    public void run() {
        try {
            aggregator.aggregateClosedHours(clock.instant());
        } catch (RuntimeException ex) {
            log.error("[device] failed to aggregate closed hours", ex);
        }
    }
}
