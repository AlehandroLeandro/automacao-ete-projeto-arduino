package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.station.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceReadingServiceTest {
    final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    final UUID id = UUID.randomUUID();
    DeviceKeyRepository keys = mock(DeviceKeyRepository.class);
    DeviceReadingRepository readings = mock(DeviceReadingRepository.class);
    DeviceRateLimiter limiter = mock(DeviceRateLimiter.class);
    DeviceKey key = new DeviceKey(new Station("ETE", "ete"), "device", "encrypted", "hash", null);
    DeviceReadingService service = new DeviceReadingService(keys, readings, limiter, Clock.fixed(now, ZoneOffset.UTC));
    @BeforeEach void setup() { when(limiter.allow(id)).thenReturn(true); when(keys.lockById(id)).thenReturn(Optional.of(key)); }
    BigDecimal n(String value) { return new BigDecimal(value); }
    @Test void acceptsOnlyValidFieldsAndUpdatesLiveState() {
        var result = service.receive(id, new DeviceReadingRequest(n("10"), n("14.5"), n("7.123"), null));
        assertThat(result.accepted()).containsExactly("heightCm");
        assertThat(result.rejected()).extracting(DeviceReadingResponse.FieldResult::field).containsExactly("rawPh", "treatedPh");
        assertThat(result.discarded()).isEmpty();
        var capture = org.mockito.ArgumentCaptor.forClass(DeviceReading.class);
        verify(readings).save(capture.capture());
        assertThat(capture.getValue().getFlowLiters()).isEqualByComparingTo("19.76");
        assertThat(capture.getValue().getMeasuredAt()).isEqualTo(now);
        assertThat(key.getLastSeenAt()).isEqualTo(now);
        assertThat(key.getLastRawPh()).isNull();
    }
    @Test void oldRawPhDoesNotReplaceLiveOrTouchOtherValues() {
        key.recordLiveFlow(n("10"), n("20"), now);
        key.recordLivePh(n("8"), n("9"), now);
        service.receive(id, new DeviceReadingRequest(null, n("7"), null, now.minusSeconds(1)));
        assertThat(key.getLastRawPh()).isEqualByComparingTo("8");
        assertThat(key.getLastTreatedPh()).isEqualByComparingTo("9");
        assertThat(key.getLastHeightCm()).isEqualByComparingTo("10");
        verify(readings).save(any());
    }
    @Test void rawOnlyUpdatesRawAndLeavesOtherTypesUntouched() {
        key.recordLiveFlow(n("10"), n("20"), now.minusSeconds(10));
        key.recordLivePh(n("8"), n("9"), now.minusSeconds(10));
        service.receive(id, new DeviceReadingRequest(null, n("7"), null, now));
        assertThat(key.getLastRawPh()).isEqualByComparingTo("7");
        assertThat(key.getLastRawPhAt()).isEqualTo(now);
        assertThat(key.getLastTreatedPh()).isEqualByComparingTo("9");
        assertThat(key.getLastTreatedPhAt()).isEqualTo(now.minusSeconds(10));
        assertThat(key.getLastHeightCm()).isEqualByComparingTo("10");
        assertThat(key.getLastFlowAt()).isEqualTo(now.minusSeconds(10));
        var capture = org.mockito.ArgumentCaptor.forClass(DeviceReading.class);
        verify(readings).save(capture.capture());
        assertThat(capture.getValue().getType()).isEqualTo(DeviceReadingType.PH_RAW);
    }
    @Test void treatedAndFlowRespectTheirOwnTimestamps() {
        key.recordLiveFlow(n("10"), n("20"), now);
        key.recordLivePh(n("8"), n("9"), now);
        service.receive(id, new DeviceReadingRequest(n("11"), null, n("6"), now.minusSeconds(1)));
        assertThat(key.getLastHeightCm()).isEqualByComparingTo("10");
        assertThat(key.getLastFlowLiters()).isEqualByComparingTo("20");
        assertThat(key.getLastTreatedPh()).isEqualByComparingTo("9");
        verify(readings, times(2)).save(any());
    }
    @Test void invalidAndFutureRequestsDoNotPersist() {
        assertThatThrownBy(() -> service.receive(id, new DeviceReadingRequest(n("46"), null, null, null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.receive(id, new DeviceReadingRequest(null, null, null, null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.receive(id, new DeviceReadingRequest(n("1"), null, null, now.plusSeconds(1)))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(readings);
        assertThat(key.getLastSeenAt()).isNull();
    }
    @Test void deniedLimitDoesNotPersist() {
        when(limiter.allow(id)).thenReturn(false);
        assertThatThrownBy(() -> service.receive(id, new DeviceReadingRequest(n("1"), null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus().value()).isEqualTo(429));
        verifyNoInteractions(readings, keys);
    }
}
