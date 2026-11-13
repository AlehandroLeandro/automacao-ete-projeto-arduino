package br.com.carbonifera.ete.station;

import br.com.carbonifera.ete.lime.LimeMinStockMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StationServiceTest {

    @Test
    void updateLimeThresholdStoresMinimumRearmsAlertAndReevaluatesStock() {
        UUID stationId = UUID.randomUUID();
        Station station = new Station("ITE A", "ite-a");
        ReflectionTestUtils.setField(station, "id", stationId);
        station.setLimeMinStockAlerted(true);
        StationRepository repository = mock(StationRepository.class);
        LimeMinStockMonitor monitor = mock(LimeMinStockMonitor.class);
        StationService service = new StationService(repository, monitor);
        when(repository.findById(stationId)).thenReturn(Optional.of(station));

        StationResponse response = service.updateLimeThreshold(stationId, new BigDecimal("25.50"));

        assertThat(response.minLimeStockBags()).isEqualByComparingTo("25.50");
        assertThat(station.getMinLimeStockBags()).isEqualByComparingTo("25.50");
        assertThat(station.isLimeMinStockAlerted()).isFalse();
        verify(monitor).evaluate(station);
    }

    @Test
    void updateLimeThresholdAllowsNullToRemoveMinimum() {
        UUID stationId = UUID.randomUUID();
        Station station = new Station("ITE A", "ite-a");
        ReflectionTestUtils.setField(station, "id", stationId);
        station.updateLimeThreshold(new BigDecimal("10"));
        StationRepository repository = mock(StationRepository.class);
        LimeMinStockMonitor monitor = mock(LimeMinStockMonitor.class);
        StationService service = new StationService(repository, monitor);
        when(repository.findById(stationId)).thenReturn(Optional.of(station));

        StationResponse response = service.updateLimeThreshold(stationId, null);

        assertThat(response.minLimeStockBags()).isNull();
        assertThat(station.getMinLimeStockBags()).isNull();
        assertThat(station.isLimeMinStockAlerted()).isFalse();
        verify(monitor).evaluate(station);
    }
}
