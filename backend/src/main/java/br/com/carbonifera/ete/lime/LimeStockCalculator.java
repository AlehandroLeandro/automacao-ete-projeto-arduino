package br.com.carbonifera.ete.lime;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
public class LimeStockCalculator {

    // Estoque de cal = Σ ENTRY − Σ CONSUMPTION. Em empates de horário, a entrada
    // é aplicada antes do consumo para não bloquear consumos que dependem dela.
    private static final Comparator<LimeStockEvent> STOCK_EVENT_ORDER = Comparator
            .comparing(LimeStockEvent::recordedAt)
            .thenComparingInt(event -> typePriority(event.type()));

    public BigDecimal currentStock(List<LimeStockEvent> events, Instant asOf) {
        BigDecimal stock = BigDecimal.ZERO;
        for (LimeStockEvent event : events) {
            if (event.recordedAt().isAfter(asOf)) {
                continue;
            }
            stock = applyMovement(stock, event);
        }
        return stock;
    }

    public boolean stockStaysNonNegative(List<LimeStockEvent> events) {
        BigDecimal stock = BigDecimal.ZERO;
        for (LimeStockEvent event : orderedEvents(events)) {
            stock = applyMovement(stock, event);
            if (stock.signum() < 0) {
                return false;
            }
        }
        return true;
    }

    private BigDecimal applyMovement(BigDecimal stock, LimeStockEvent event) {
        return switch (event.type()) {
            case ENTRY -> stock.add(event.quantityBags());
            case CONSUMPTION -> stock.subtract(event.quantityBags());
        };
    }

    private List<LimeStockEvent> orderedEvents(List<LimeStockEvent> events) {
        return events.stream()
                .sorted(STOCK_EVENT_ORDER)
                .toList();
    }

    private static int typePriority(LimeRecordType type) {
        return switch (type) {
            case ENTRY -> 0;
            case CONSUMPTION -> 1;
        };
    }
}
