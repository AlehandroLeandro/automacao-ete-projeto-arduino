package br.com.carbonifera.ete.measurement;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowMeasurementResponseTest {

    @Test
    void keepsManualAuthorAndLeavesDeviceFieldsNull() {
        UUID userId = UUID.randomUUID();
        AppUser author = mock(AppUser.class);
        when(author.getId()).thenReturn(userId);
        when(author.getName()).thenReturn("Operador");

        FlowMeasurementResponse response = FlowMeasurementResponse.from(measurement(author, null, null));

        assertThat(response.createdById()).isEqualTo(userId);
        assertThat(response.createdByName()).isEqualTo("Operador");
        assertThat(response.deviceName()).isNull();
        assertThat(response.sampleCount()).isNull();
    }

    @Test
    void returnsEquipmentLabelAndNullAuthorId() {
        FlowMeasurementResponse response = FlowMeasurementResponse.from(
                measurement(null, "Sensor Entrada", 12));

        assertThat(response.createdById()).isNull();
        assertThat(response.createdByName()).isEqualTo("Equipamento: Sensor Entrada");
    }

    @Test
    void returnsEquipmentNameAndSampleCount() {
        FlowMeasurementResponse response = FlowMeasurementResponse.from(
                measurement(null, "Sensor Entrada", 12));

        assertThat(response.deviceName()).isEqualTo("Sensor Entrada");
        assertThat(response.sampleCount()).isEqualTo(12);
    }

    private FlowMeasurement measurement(AppUser author, String deviceName, Integer sampleCount) {
        Station station = mock(Station.class);
        when(station.getId()).thenReturn(UUID.randomUUID());
        when(station.getName()).thenReturn("ETE A");
        FlowMeasurement measurement = new FlowMeasurement(station, new BigDecimal("1.25"),
                Instant.parse("2026-01-01T00:00:00Z"), author);
        if (deviceName != null) {
            measurement = FlowMeasurement.fromDevice(station, new BigDecimal("1.25"), new BigDecimal("2.00"),
                    Instant.parse("2026-01-01T00:00:00Z"), null, deviceName, null, sampleCount);
        }
        return measurement;
    }
}
