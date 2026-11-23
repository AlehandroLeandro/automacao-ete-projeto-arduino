package br.com.ete.measurement;

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
        String createdByName,
        String deviceName,
        Integer sampleCount,
        boolean timeEstimated,
        String timeEstimateOrigin
) {

    public PhMeasurementResponse(UUID id, UUID stationId, String stationName, PhWaterType type, BigDecimal ph,
            Instant measuredAt, UUID createdById, String createdByName, String deviceName, Integer sampleCount) {
        this(id, stationId, stationName, type, ph, measuredAt, createdById, createdByName, deviceName, sampleCount, false, null);
    }

    public static PhMeasurementResponse from(PhMeasurement measurement) {
        return new PhMeasurementResponse(
                measurement.getId(),
                measurement.getStation().getId(),
                measurement.getStation().getName(),
                measurement.getType(),
                measurement.getPh(),
                measurement.getMeasuredAt(),
                measurement.getCreatedBy() == null ? null : measurement.getCreatedBy().getId(),
                measurement.getCreatedBy() == null
                        ? "Equipamento: " + measurement.getDeviceName()
                        : measurement.getCreatedBy().getName(),
                measurement.getDeviceName(),
                measurement.getSampleCount(),
                measurement.isTimeEstimated(),
                measurement.getTimeEstimateOrigin()
        );
    }
}
