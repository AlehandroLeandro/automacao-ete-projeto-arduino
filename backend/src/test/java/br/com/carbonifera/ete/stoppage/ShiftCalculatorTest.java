package br.com.carbonifera.ete.stoppage;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftCalculatorTest {

    private final ZoneId zoneId = ZoneId.of("America/Sao_Paulo");
    private final ShiftCalculator calculator = new ShiftCalculator(zoneId, LocalTime.of(5, 0), LocalTime.of(17, 0));

    @Test
    void splitByShiftSupportsStoppageCrossingMidnightAndShiftBoundaries() {
        ZonedDateTime start = ZonedDateTime.of(2026, 6, 1, 16, 30, 0, 0, zoneId);
        ZonedDateTime end = ZonedDateTime.of(2026, 6, 2, 5, 30, 0, 0, zoneId);

        List<ShiftDuration> durations = calculator.splitByShift(start.toInstant(), end.toInstant());
        List<ShiftSlice> slices = calculator.splitInstantsByShift(start.toInstant(), end.toInstant());

        assertThat(durations).hasSize(3);
        assertThat(durations.get(0)).isEqualTo(new ShiftDuration(LocalDate.of(2026, 6, 1), ShiftType.DAY, Duration.ofMinutes(30)));
        assertThat(durations.get(1)).isEqualTo(new ShiftDuration(LocalDate.of(2026, 6, 1), ShiftType.NIGHT, Duration.ofHours(12)));
        assertThat(durations.get(2)).isEqualTo(new ShiftDuration(LocalDate.of(2026, 6, 2), ShiftType.DAY, Duration.ofMinutes(30)));
        assertThat(slices.get(0).startAt()).isEqualTo(start.toInstant());
        assertThat(slices.get(0).endAt()).isEqualTo(ZonedDateTime.of(2026, 6, 1, 17, 0, 0, 0, zoneId).toInstant());
        assertThat(slices.get(2).startAt()).isEqualTo(ZonedDateTime.of(2026, 6, 2, 5, 0, 0, 0, zoneId).toInstant());
        assertThat(slices.get(2).endAt()).isEqualTo(end.toInstant());
    }

    @Test
    void splitByShiftKeepsEarlyMorningInsidePreviousNightShift() {
        ZonedDateTime start = ZonedDateTime.of(2026, 6, 2, 1, 0, 0, 0, zoneId);
        ZonedDateTime end = ZonedDateTime.of(2026, 6, 2, 4, 0, 0, 0, zoneId);

        List<ShiftDuration> durations = calculator.splitByShift(start.toInstant(), end.toInstant());

        assertThat(durations).containsExactly(
                new ShiftDuration(LocalDate.of(2026, 6, 1), ShiftType.NIGHT, Duration.ofHours(3))
        );
    }
}
