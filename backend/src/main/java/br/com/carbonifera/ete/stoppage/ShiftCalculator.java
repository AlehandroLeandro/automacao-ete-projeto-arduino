package br.com.carbonifera.ete.stoppage;

import br.com.carbonifera.ete.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class ShiftCalculator {

    private final ZoneId zoneId;
    private final LocalTime dayStart;
    private final LocalTime nightStart;

    @Autowired
    public ShiftCalculator(AppProperties properties) {
        this(properties.timezone(), properties.shiftDayStart(), properties.shiftNightStart());
    }

    public ShiftCalculator(ZoneId zoneId, LocalTime dayStart, LocalTime nightStart) {
        this.zoneId = zoneId;
        this.dayStart = dayStart;
        this.nightStart = nightStart;
    }

    public List<ShiftDuration> splitByShift(Instant startAt, Instant endAt) {
        return splitInstantsByShift(startAt, endAt)
                .stream()
                .map(slice -> new ShiftDuration(slice.shiftDate(), slice.shiftType(), slice.duration()))
                .toList();
    }

    public List<ShiftSlice> splitInstantsByShift(Instant startAt, Instant endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Horário de fim deve ser maior que o horário de início");
        }

        List<ShiftSlice> slices = new ArrayList<>();
        ZonedDateTime current = startAt.atZone(zoneId);
        ZonedDateTime end = endAt.atZone(zoneId);

        while (current.isBefore(end)) {
            ShiftType shiftType = shiftType(current.toLocalTime());
            ZonedDateTime nextBoundary = nextBoundary(current, shiftType);
            ZonedDateTime segmentEnd = nextBoundary.isBefore(end) ? nextBoundary : end;

            slices.add(new ShiftSlice(
                    shiftDate(current, shiftType),
                    shiftType,
                    current.toInstant(),
                    segmentEnd.toInstant(),
                    Duration.between(current, segmentEnd)
            ));
            current = segmentEnd;
        }

        return slices;
    }

    public ShiftWindow lastCompletedShiftWindow(Instant reference) {
        ShiftWindow currentWindow = currentShiftWindow(reference);
        return currentShiftWindow(currentWindow.startAt().minusMillis(1));
    }

    public ShiftWindow currentShiftWindow(Instant reference) {
        ZonedDateTime current = reference.atZone(zoneId);
        ShiftType type = shiftType(current.toLocalTime());
        LocalDate date = shiftDate(current, type);

        ZonedDateTime start;
        ZonedDateTime end;
        if (type == ShiftType.DAY) {
            start = ZonedDateTime.of(current.toLocalDate(), dayStart, zoneId);
            end = ZonedDateTime.of(current.toLocalDate(), nightStart, zoneId);
        } else {
            start = ZonedDateTime.of(date, nightStart, zoneId);
            end = ZonedDateTime.of(date.plusDays(1), dayStart, zoneId);
        }

        return new ShiftWindow(date, type, start.toInstant(), end.toInstant());
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    // Turno de um instante conforme AD-006: Dia se em [dayStart, nightStart), Noite caso contrário.
    public ShiftType shiftTypeAt(Instant instant) {
        return shiftType(instant.atZone(zoneId).toLocalTime());
    }

    private ShiftType shiftType(LocalTime time) {
        if (!time.isBefore(dayStart) && time.isBefore(nightStart)) {
            return ShiftType.DAY;
        }
        return ShiftType.NIGHT;
    }

    private ZonedDateTime nextBoundary(ZonedDateTime current, ShiftType shiftType) {
        if (shiftType == ShiftType.DAY) {
            return ZonedDateTime.of(current.toLocalDate(), nightStart, zoneId);
        }
        if (current.toLocalTime().isBefore(dayStart)) {
            return ZonedDateTime.of(current.toLocalDate(), dayStart, zoneId);
        }
        return ZonedDateTime.of(current.toLocalDate().plusDays(1), dayStart, zoneId);
    }

    private LocalDate shiftDate(ZonedDateTime current, ShiftType shiftType) {
        if (shiftType == ShiftType.DAY) {
            return current.toLocalDate();
        }
        if (current.toLocalTime().isBefore(dayStart)) {
            return current.toLocalDate().minusDays(1);
        }
        return current.toLocalDate();
    }
}
