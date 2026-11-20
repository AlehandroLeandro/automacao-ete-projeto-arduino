package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.config.CorsProperties;
import br.com.carbonifera.ete.config.DeviceProperties;
import br.com.carbonifera.ete.security.*;
import br.com.carbonifera.ete.station.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeviceReadingController.class, properties = "server.port=0")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, ChangePasswordGuardFilter.class,
        DeviceReadingService.class, DeviceRateLimiter.class, DeviceReadingHttpValidationTest.Config.class})
class DeviceReadingHttpValidationTest {
    @org.springframework.boot.test.mock.mockito.MockBean DeviceBatchService batches;
    @Autowired MockMvc mvc;
    @MockBean DeviceKeyRepository keys;
    @MockBean DeviceReadingRepository readings;
    @MockBean DeviceHourlyAggregator aggregator;
    @MockBean JwtService jwt;
    @MockBean AppUserDetailsService users;
    UUID id;

    @TestConfiguration static class Config {
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-01-01T12:30:00Z"), ZoneOffset.UTC); }
        @Bean DeviceProperties deviceProperties() { return new DeviceProperties(null, 5, 10); }
        @Bean CorsProperties corsProperties() { return new CorsProperties("http://localhost:5174"); }
    }

    @BeforeEach void setup() {
        id = UUID.randomUUID();
        Station station = new Station("ETE", "ete");
        ReflectionTestUtils.setField(station, "id", UUID.randomUUID());
        DeviceKey key = new DeviceKey(station, "sensor", "encrypted", "hash", null);
        ReflectionTestUtils.setField(key, "id", id);
        when(jwt.isDeviceToken("valid")).thenReturn(true);
        when(jwt.deviceKeyId("valid")).thenReturn(id);
        when(keys.findById(id)).thenReturn(Optional.of(key));
        when(keys.lockById(id)).thenReturn(Optional.of(key));
    }

    @Test void invalidAndFutureReadingsReturnBadRequestWithoutSaving() throws Exception {
        for (String body : new String[]{"{}", "{\"heightCm\":46}",
                "{\"rawPh\":7,\"measuredAt\":\"2026-01-01T12:30:01Z\"}"}) {
            mvc.perform(post("/api/device/readings").header("Authorization", "Bearer valid")
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verify(readings, never()).save(any());
    }

    @Test void eleventhRequestReturnsTooManyRequestsWithoutSaving() throws Exception {
        for (int i = 0; i < 11; i++) {
            var result = mvc.perform(post("/api/device/readings").header("Authorization", "Bearer valid")
                    .contentType("application/json").content("{\"rawPh\":7}"));
            result.andExpect(i < 10 ? status().isOk() : status().isTooManyRequests());
        }
        verify(readings, times(10)).save(any());
    }
}
