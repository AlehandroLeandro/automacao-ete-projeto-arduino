package br.com.ete.measurement;

import br.com.ete.station.Station;
import br.com.ete.user.AppUser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhMeasurementResponseTest {

    @Test
    void keepsManualAuthorAndLeavesDeviceFieldsNull() {
        UUID userId = UUID.randomUUID();
        AppUser author = mock(AppUser.class);
        when(author.getId()).thenReturn(userId);
        when(author.getName()).thenReturn("Operador");

        PhMeasurementResponse response = PhMeasurementResponse.from(measurement(author, null, null));

        assertThat(response.createdById()).isEqualTo(userId);
        assertThat(response.createdByName()).isEqualTo("Operador");
        assertThat(response.deviceName()).isNull();
        assertThat(response.sampleCount()).isNull();
    }

    @Test
    void returnsEquipmentLabelAndNullAuthorId() {
        PhMeasurementResponse response = PhMeasurementResponse.from(
                measurement(null, "Sensor Entrada", 12));

        assertThat(response.createdById()).isNull();
        assertThat(response.createdByName()).isEqualTo("Equipamento: Sensor Entrada");
    }

    @Test
    void returnsEquipmentNameAndSampleCount() {
        PhMeasurementResponse response = PhMeasurementResponse.from(
                measurement(null, "Sensor Entrada", 12));

        assertThat(response.deviceName()).isEqualTo("Sensor Entrada");
        assertThat(response.sampleCount()).isEqualTo(12);
    }

    private PhMeasurement measurement(AppUser author, String deviceName, Integer sampleCount) {
        Station station = mock(Station.class);
        when(station.getId()).thenReturn(UUID.randomUUID());
        when(station.getName()).thenReturn("ETE A");
        Instant measuredAt = Instant.parse("2026-01-01T00:00:00Z");
        if (deviceName != null) {
            return PhMeasurement.fromDevice(station, PhWaterType.RAW, new BigDecimal("7.00"), measuredAt,
                    null, deviceName, null, sampleCount);
        }
        return new PhMeasurement(station, PhWaterType.RAW, new BigDecimal("7.00"), measuredAt, author);
    }
}
