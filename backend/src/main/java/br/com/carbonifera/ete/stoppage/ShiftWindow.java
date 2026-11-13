package br.com.carbonifera.ete.stoppage;

import java.time.Instant;
import java.time.LocalDate;

public record ShiftWindow(
        LocalDate shiftDate,
        ShiftType shiftType,
        Instant startAt,
        Instant endAt
) {
}
