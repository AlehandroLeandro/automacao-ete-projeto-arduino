package br.com.carbonifera.ete.lime;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LimeStockCalculatorTest {

    private final LimeStockCalculator calculator = new LimeStockCalculator();

    @Test
    void currentStockSomaEntradasMenosConsumos() {
        List<LimeStockEvent> events = List.of(
                event(LimeRecordType.ENTRY, "50.00", "2026-06-01T06:00:00Z"),
                event(LimeRecordType.ENTRY, "20.00", "2026-06-01T09:00:00Z"),
                event(LimeRecordType.CONSUMPTION, "15.00", "2026-06-01T10:00:00Z")
        );

        BigDecimal stock = calculator.currentStock(events, Instant.parse("2026-06-01T11:00:00Z"));

        assertThat(stock).isEqualByComparingTo("55.00");
    }

    @Test
    void currentStockEhZeroQuandoNaoHaEventos() {
        BigDecimal stock = calculator.currentStock(List.of(), Instant.parse("2026-06-01T08:00:00Z"));

        assertThat(stock).isEqualByComparingTo("0");
    }

    @Test
    void currentStockIgnoraEventosPosterioresAoInstanteConsultado() {
        List<LimeStockEvent> events = List.of(
                event(LimeRecordType.ENTRY, "10.00", "2026-06-01T06:00:00Z"),
                event(LimeRecordType.CONSUMPTION, "4.00", "2026-06-01T07:00:00Z"),
                event(LimeRecordType.ENTRY, "30.00", "2026-06-01T09:00:00Z")
        );

        BigDecimal stock = calculator.currentStock(events, Instant.parse("2026-06-01T08:00:00Z"));

        assertThat(stock).isEqualByComparingTo("6.00");
    }

    @Test
    void stockStaysNonNegativeRejeitaConsumoAcimaDoEstoqueDisponivel() {
        List<LimeStockEvent> events = List.of(
                event(LimeRecordType.ENTRY, "10.00", "2026-06-01T08:00:00Z"),
                event(LimeRecordType.CONSUMPTION, "11.00", "2026-06-01T09:00:00Z")
        );

        assertThat(calculator.stockStaysNonNegative(events)).isFalse();
    }

    @Test
    void stockStaysNonNegativePermiteConsumoQueDeixaEstoqueEmZero() {
        List<LimeStockEvent> events = List.of(
                event(LimeRecordType.ENTRY, "10.00", "2026-06-01T08:00:00Z"),
                event(LimeRecordType.CONSUMPTION, "10.00", "2026-06-01T09:00:00Z")
        );

        assertThat(calculator.stockStaysNonNegative(events)).isTrue();
    }

    @Test
    void stockStaysNonNegativeAplicaEntradaAntesDoConsumoNoMesmoHorario() {
        List<LimeStockEvent> events = List.of(
                event(LimeRecordType.CONSUMPTION, "10.00", "2026-06-01T09:00:00Z"),
                event(LimeRecordType.ENTRY, "10.00", "2026-06-01T09:00:00Z")
        );

        assertThat(calculator.stockStaysNonNegative(events)).isTrue();
    }

    private LimeStockEvent event(LimeRecordType type, String quantity, String recordedAt) {
        return new LimeStockEvent(type, new BigDecimal(quantity), Instant.parse(recordedAt));
    }
}
