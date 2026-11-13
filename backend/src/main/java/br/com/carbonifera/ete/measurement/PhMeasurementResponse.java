package br.com.carbonifera.ete.measurement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PhMeasurementResponse(
        UUID id,
        UUID stationId,
        String stationName,
        PhWaterType type,
        BigDecimal ph,
        Instant measuredAt,
        UUID createdById,
        String createdByName
) {

    public static PhMeasurementResponse from(PhMeasurement measurement) {
        return new PhMeasurementResponse(
                measurement.getId(),
                measurement.getStation().getId(),
                measurement.getStation().getName(),
                measurement.getType(),
                measurement.getPh(),
                measurement.getMeasuredAt(),
                measurement.getCreatedBy().getId(),
                measurement.getCreatedBy().getName()
        );
    }
}
