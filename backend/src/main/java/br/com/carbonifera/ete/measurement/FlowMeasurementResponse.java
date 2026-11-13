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
        String createdByName,
        String deviceName,
        Integer sampleCount
) {

    public static FlowMeasurementResponse from(FlowMeasurement measurement) {
        return new FlowMeasurementResponse(
                measurement.getId(),
                measurement.getStation().getId(),
                measurement.getStation().getName(),
                measurement.getFlowLiters(),
                measurement.getMeasuredAt(),
                measurement.getCreatedBy() == null ? null : measurement.getCreatedBy().getId(),
                measurement.getCreatedBy() == null
                        ? "Equipamento: " + measurement.getDeviceName()
                        : measurement.getCreatedBy().getName(),
                measurement.getDeviceName(),
                measurement.getSampleCount()
        );
    }
}
