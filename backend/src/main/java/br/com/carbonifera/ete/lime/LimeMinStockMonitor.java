package br.com.carbonifera.ete.lime;

import br.com.carbonifera.ete.notification.NotificationCommand;
import br.com.carbonifera.ete.notification.NotificationDispatcher;
import br.com.carbonifera.ete.notification.NotificationRecipientResolver;
import br.com.carbonifera.ete.notification.NotificationType;
import br.com.carbonifera.ete.station.Station;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

@Service
public class LimeMinStockMonitor {

    private final LimeRecordRepository repository;
    private final LimeStockCalculator stockCalculator;
    private final NotificationDispatcher dispatcher;
    private final NotificationRecipientResolver resolver;
    private final Clock clock;

    public LimeMinStockMonitor(
            LimeRecordRepository repository,
            LimeStockCalculator stockCalculator,
            NotificationDispatcher dispatcher,
            NotificationRecipientResolver resolver,
            Clock clock
    ) {
        this.repository = repository;
        this.stockCalculator = stockCalculator;
        this.dispatcher = dispatcher;
        this.resolver = resolver;
        this.clock = clock;
    }

    @Transactional
    public void evaluate(Station station) {
        BigDecimal minimum = station.getMinLimeStockBags();
        if (minimum == null) {
            return;
        }

        Instant now = Instant.now(clock);
        BigDecimal stock = currentStock(station, now);
        boolean atOrBelowMinimum = stock.compareTo(minimum) <= 0;
        if (atOrBelowMinimum && !station.isLimeMinStockAlerted()) {
            dispatchAlert(station, stock, now);
            station.setLimeMinStockAlerted(true);
            return;
        }

        if (!atOrBelowMinimum && station.isLimeMinStockAlerted()) {
            station.setLimeMinStockAlerted(false);
        }
    }

    private BigDecimal currentStock(Station station, Instant asOf) {
        return stockCalculator.currentStock(
                repository.findAllByStationAndRecordedAtLessThanEqualOrderByRecordedAtAsc(station, asOf)
                        .stream()
                        .map(record -> new LimeStockEvent(record.getType(), record.getQuantityBags(), record.getRecordedAt()))
                        .toList(),
                asOf
        );
    }

    private void dispatchAlert(Station station, BigDecimal stock, Instant scheduledAt) {
        dispatcher.dispatch(new NotificationCommand(
                NotificationType.LIME_MIN_STOCK_ALERT,
                resolver.activeSupervisors(),
                station,
                "Estoque mínimo de Cal na " + stationNotificationName(station),
                "Estoque atual de " + stock.stripTrailingZeros().toPlainString() + " bags",
                "/app/dashboard?station=" + station.getId(),
                null,
                scheduledAt
        ));
    }
    private String stationNotificationName(Station station) {
        return station.getName().startsWith("ETE ") ? station.getName() : "ETE " + station.getName();
    }
}
