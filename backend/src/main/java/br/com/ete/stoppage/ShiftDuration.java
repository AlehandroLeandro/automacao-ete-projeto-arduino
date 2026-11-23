package br.com.ete.stoppage;

import java.time.Duration;
import java.time.LocalDate;

public record ShiftDuration(
        LocalDate shiftDate,
        ShiftType shiftType,
        Duration duration
) {
}
