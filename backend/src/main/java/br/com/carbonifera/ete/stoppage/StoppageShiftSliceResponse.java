package br.com.carbonifera.ete.stoppage;

import java.time.LocalDate;

public record StoppageShiftSliceResponse(
        LocalDate shiftDate,
        ShiftType shiftType,
        long durationMinutes
) {

    public static StoppageShiftSliceResponse from(ShiftDuration duration) {
        return new StoppageShiftSliceResponse(
                duration.shiftDate(),
                duration.shiftType(),
                duration.duration().toMinutes()
        );
    }
}
