package br.com.carbonifera.ete.stoppage;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

public record ShiftSlice(
        LocalDate shiftDate,
        ShiftType shiftType,
        Instant startAt,
        Instant endAt,
        Duration duration
) {
}
