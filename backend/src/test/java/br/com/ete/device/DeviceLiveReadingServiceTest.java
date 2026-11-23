package br.com.ete.device;

import br.com.ete.station.Station;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DeviceLiveReadingServiceTest {
    @Test void selectsLatestPerTypeFromRequestedStationsAndIgnoresEmptyKeys() {
        DeviceKeyRepository keys = mock(DeviceKeyRepository.class);
        Station station = new Station("ETE", "ete");
        DeviceKey first = new DeviceKey(station, "first", "a", "a", null);
        DeviceKey second = new DeviceKey(station, "second", "b", "b", null);
        DeviceKey empty = new DeviceKey(station, "empty", "c", "c", null);
        Instant earlier = Instant.parse("2026-01-01T10:00:00Z");
        first.recordLiveFlow(BigDecimal.ONE, BigDecimal.TEN, earlier);
        first.recordLivePh(new BigDecimal("7"), null, earlier.plusSeconds(10));
        second.recordLiveFlow(BigDecimal.ONE, new BigDecimal("20"), earlier.plusSeconds(10));
        second.recordLivePh(new BigDecimal("8"), new BigDecimal("9"), earlier);
        when(keys.findAllByStationIn(List.of(station))).thenReturn(List.of(first, second, empty));
        var service = new DeviceLiveReadingService(keys);
        var result = service.latest(List.of(station));
        assertThat(result.get(DeviceReadingType.FLOW).value()).isEqualByComparingTo("20");
        assertThat(result.get(DeviceReadingType.PH_RAW).value()).isEqualByComparingTo("7");
        assertThat(result.get(DeviceReadingType.PH_TREATED).value()).isEqualByComparingTo("9");
        assertThat(service.latest(List.of())).isEmpty();
        verify(keys).findAllByStationIn(List.of(station));
        when(keys.findAllByStationIn(List.of(station))).thenReturn(List.of(empty));
        assertThat(service.latest(List.of(station))).isEmpty();
    }
}
