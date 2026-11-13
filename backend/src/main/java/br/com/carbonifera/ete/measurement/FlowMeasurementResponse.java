package br.com.carbonifera.ete.measurement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FlowMeasurementResponse(
        UUID id,
        UUID stationId,
        String stationName,
        BigDecimal flowLiters,
        Instant measuredAt,
        UUID createdById,
        String createdByName
) {

    public static FlowMeasurementResponse from(FlowMeasurement measurement) {
        return new FlowMeasurementResponse(
                measurement.getId(),
                measurement.getStation().getId(),
                measurement.getStation().getName(),
                measurement.getFlowLiters(),
                measurement.getMeasuredAt(),
                measurement.getCreatedBy().getId(),
                measurement.getCreatedBy().getName()
        );
    }
}
